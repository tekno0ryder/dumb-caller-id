package com.ahmedalsinan.dumbcallerid

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A plain-text report a user can paste to whoever helps them, since they can't run adb. In English whatever the
// app language, and with no phone numbers: only the phone, its setup, and what happened on recent calls.
object Diagnostics {
    fun collect(context: Context): String = format(
        appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
        device = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})",
        android = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        maker = Oem.from(Build.MANUFACTURER, Build.BRAND),
        battery = BatteryUi.forSdk(Build.VERSION.SDK_INT),
        supported = Setup.isSupported(context),
        enabled = Prefs.isEnabled(context),
        // Resource names ("check_battery") rather than titles, so the report reads the same in every language.
        checks = Setup.checks.map { context.resources.getResourceEntryName(it.title) to it.isOk(context) },
        calls = RecentCalls.load(context),
    )

    internal fun format(
        appVersion: String,
        device: String,
        android: String,
        maker: Oem,
        battery: BatteryUi,
        supported: Boolean,
        enabled: Boolean,
        checks: List<Pair<String, Boolean>>,
        calls: List<RecentCalls.Call>,
    ): String = buildString {
        appendLine("Dumb Caller ID $appVersion")
        appendLine("Device: $device")
        appendLine("Android: $android")
        appendLine("Maker: $maker, battery settings: $battery")
        appendLine("Caller ID supported: ${yesNo(supported)}, popup switched on: ${yesNo(enabled)}")
        appendLine("Setup:")
        for ((name, ok) in checks) appendLine("  ${if (ok) "done   " else "MISSING"} $name")
        appendLine("Recent calls, newest first:")
        if (calls.isEmpty()) appendLine("  none")
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
        for (call in calls) appendLine("  ${time.format(Date(call.time))} ${call.outcome.name}")
    }.trimEnd()

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
