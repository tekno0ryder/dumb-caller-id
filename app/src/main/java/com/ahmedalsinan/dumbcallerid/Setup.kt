package com.ahmedalsinan.dumbcallerid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.lang.ref.WeakReference

// What the phone must allow for the popup to work. The home screen only counts problems,
// the settings screen lists every check with its Fix button, and OnboardingActivity walks through
// the missing ones. Which steps apply to this phone, and the screens they open, come from Device.kt.
object Setup {
    const val REQUEST_PERMISSIONS = 1
    const val REQUEST_ROLE = 2

    private val battery = BatteryUi.forSdk(Build.VERSION.SDK_INT)

    // The "Did you do it?" dialog on screen and the screen showing it, so resuming that screen doesn't stack a
    // second one. Weak, so a closed screen isn't kept in memory.
    private var question: WeakReference<AlertDialog>? = null
    private var questionScreen: WeakReference<Activity>? = null

    // A setup step: what it's for, what the user will see after tapping [action], whether it's done,
    // how to fix it, and phone-specific advice for when the fix doesn't work. A step that can't be fixed on
    // this phone (not [canFix]) shows no Fix button.
    class Check(
        val title: Int,
        val why: Int,
        val next: Int,
        val action: Int,
        val isOk: (Context) -> Boolean,
        val hint: (Context) -> String? = { null },
        val canFix: (Context) -> Boolean = { true },
        val fix: (Activity) -> Unit,
    )

    // InlinedApi: the auto-revoke check passes below Android 11, so its Fix action never runs there.
    @SuppressLint("InlinedApi")
    val checks: List<Check> = listOf(
        Check(
            R.string.check_role, R.string.why_role, R.string.next_role, R.string.action_allow,
            isOk = ::holdsCallerIdRole, hint = ::roleHint, canFix = ::isSupported,
        ) {
            val request = it.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            try {
                it.startActivityForResult(request, REQUEST_ROLE)
            } catch (e: RuntimeException) { // ActivityNotFoundException, SecurityException on modified Android builds
                Log.w(LOG_TAG, "Caller ID role prompt refused", e)
                SettingsLauncher.openSettings(it, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
        },
        Check(
            R.string.check_phone, R.string.why_phone, R.string.next_phone, R.string.action_allow,
            isOk = { missingPermissions(it).isEmpty() },
        ) {
            it.requestPermissions(missingPermissions(it).toTypedArray(), REQUEST_PERMISSIONS)
        },
        Check(
            R.string.check_overlay, R.string.why_overlay, overlayNext(Build.VERSION.SDK_INT), R.string.action_open_settings,
            isOk = CallOverlay::canShow, hint = ::restrictedHint,
        ) {
            SettingsLauncher.openSettings(it, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        },
        Check(
            R.string.check_battery, R.string.why_battery, R.string.next_battery_dialog, R.string.action_allow,
            isOk = ::isIgnoringBatteryOptimizations, hint = ::batteryHint,
        ) {
            SettingsLauncher.openFirstAllowed(it, battery.screens)
        },
    ) + ManualSteps.forOem(Oem.from(Build.MANUFACTURER, Build.BRAND)).map(::manualCheck) + listOf(
        Check(
            R.string.check_auto_revoke, R.string.why_auto_revoke, R.string.next_auto_revoke, R.string.action_open_settings,
            isOk = ::isExemptFromAutoRevoke,
        ) {
            SettingsLauncher.openSettings(it, Intent.ACTION_AUTO_REVOKE_PERMISSIONS)
        },
        Check(
            R.string.check_telegram, R.string.why_telegram, R.string.next_telegram, R.string.action_install,
            isOk = Telegram::isInstalled, fix = ::openTelegramStorePage,
        ),
    )

    // Makers' own background and popup limits can't be read by apps, so these count as done once the user
    // confirms them after visiting the screen.
    private fun manualCheck(step: ManualStep) = Check(
        step.title, step.why, step.next, R.string.action_open_settings,
        isOk = { Prefs.isConfirmed(it, step.key) },
        hint = { it.getString(step.hint, it.getString(R.string.app_name)) },
    ) {
        Prefs.setPendingStep(it, step.key)
        SettingsLauncher.openFirstAllowed(it, step.screens)
    }

    // Call from onResume of a screen that offers the setup steps. After a visit to a maker's settings, asks
    // whether the change was made; [onConfirmed] runs on "Yes" so the screen can show the step as done.
    // The question is saved until answered, so rotating the phone or Android closing the app doesn't lose it.
    fun confirmPendingStep(activity: Activity, onConfirmed: () -> Unit) {
        val key = Prefs.pendingStep(activity) ?: return
        val step = Oem.entries.flatMap(ManualSteps::forOem).find { it.key == key }
            ?: return Prefs.clearPendingStep(activity)
        val open = question?.get()
        if (open != null && open.isShowing && questionScreen?.get() === activity) return
        questionScreen = WeakReference(activity)
        question = WeakReference(AlertDialog.Builder(activity)
            .setMessage(activity.getString(step.confirm, activity.getString(R.string.app_name)))
            .setPositiveButton(R.string.confirm_yes) { _, _ ->
                Prefs.clearPendingStep(activity)
                Prefs.setConfirmed(activity, step.key)
                onConfirmed()
            }
            .setNegativeButton(R.string.confirm_not_yet) { _, _ -> Prefs.clearPendingStep(activity) }
            .setOnCancelListener { Prefs.clearPendingStep(activity) }
            .show())
    }

    fun isComplete(context: Context): Boolean = checks.all { it.isOk(context) }

    // Some custom Android builds have no caller ID role; the popup can't work there at all.
    fun isSupported(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)

    // The settings screen shows the advice for every unfinished step.
    fun hints(context: Context): List<String> =
        listOfNotNull(context.getString(R.string.unsupported_summary).takeUnless { isSupported(context) })
            .plus(checks.filter { !it.isOk(context) }.mapNotNull { it.hint(context) })
            .distinct()

    // Android 13+ blocks some settings for apps not installed from an app store until "Allow restricted settings"
    // is turned on. Installs from Google Play never see this.
    private fun restrictedHint(context: Context): String? {
        if (Build.VERSION.SDK_INT < 33) return null
        val installer = context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        if (installer == "com.android.vending") return null
        return context.getString(R.string.hint_restricted, context.getString(R.string.app_name))
    }

    // What to do if a settings page opens instead of the dialog, for this Android version.
    private fun batteryHint(context: Context): String {
        val appName = context.getString(R.string.app_name)
        return listOfNotNull(
            context.getString(R.string.hint_battery_page) + "\n" + context.getString(battery.pageNext, appName),
            battery.hint?.let { context.getString(it, appName) },
        ).joinToString("\n\n")
    }

    // After two refusals Android stops showing the role prompt; it can still be chosen in Default apps.
    private fun roleHint(context: Context): String =
        context.getString(R.string.hint_role, context.getString(R.string.app_name))

    private fun holdsCallerIdRole(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    fun missingPermissions(context: Context): List<String> {
        // Only to notice when a call ends and remove the popup; the number itself comes from CallScreener.
        val perms = listOf(Manifest.permission.READ_PHONE_STATE)
        return perms.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    // After "Don't allow" twice Android stops showing the dialog; send the user to App info instead.
    fun onPermissionsResult(activity: Activity) {
        if (missingPermissions(activity).any { !activity.shouldShowRequestPermissionRationale(it) }) {
            SettingsLauncher.openSettings(activity, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    // "Pause app activity if unused" (Android 11+) revokes permissions of apps not opened for months.
    private fun isExemptFromAutoRevoke(context: Context): Boolean =
        Build.VERSION.SDK_INT < 30 || context.packageManager.isAutoRevokeWhitelisted

    private fun openTelegramStorePage(activity: Activity) {
        SettingsLauncher.startFirst(
            activity,
            listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.telegram.messenger")),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://telegram.org/android")),
            ),
        )
    }

}
