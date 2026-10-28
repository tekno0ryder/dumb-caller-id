package com.ahmedalsinan.dumbcallerid

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast

// Opens settings screens for the setup steps without ever crashing: Android may refuse a screen because it's
// missing on this phone or guarded by a permission this app lacks (Samsung's One UI 9 does that).
object SettingsLauncher {
    // Opens the first screen this app may start, else App info. Every screen must be declared in the manifest's
    // <queries>, or resolveActivity can't see it (DeviceTest checks that).
    fun openFirstAllowed(activity: Activity, screens: List<Screen>) {
        val allowed = screens.map { intentFor(activity, it) }.filter { canStart(activity, it) }
        startFirst(activity, allowed + appInfo(activity))
    }

    // Starts the first intent Android accepts, moving on from refused ones. Shows a short message if none opened.
    internal fun startFirst(activity: Activity, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(LOG_TAG, "No screen for $intent")
            } catch (e: SecurityException) {
                Log.w(LOG_TAG, "Not allowed to open $intent", e)
            }
        }
        Toast.makeText(activity, R.string.open_failed, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun appInfo(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private fun intentFor(context: Context, screen: Screen): Intent = Intent(screen.action).apply {
        if (screen.pkg != null && screen.cls != null) setClassName(screen.pkg, screen.cls)
        if (screen.withPackageUri) data = Uri.parse("package:${context.packageName}")
        screen.packageExtra?.let { putExtra(it, context.packageName) }
    }

    internal fun canStart(context: Context, intent: Intent): Boolean {
        val info = context.packageManager.resolveActivity(intent, 0)?.activityInfo ?: return false
        val permission = info.permission ?: return info.exported
        return info.exported && context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    // A system settings screen for this app, else App info. Not filtered with canStart: the Settings app isn't
    // in the manifest's <queries>, so resolveActivity can't see these screens.
    fun openSettings(activity: Activity, action: String) {
        startFirst(activity, listOf(Intent(action, Uri.parse("package:${activity.packageName}")), appInfo(activity)))
    }
}
