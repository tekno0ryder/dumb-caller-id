package com.ahmedalsinan.dumbcallerid

import java.util.Locale

// Which setup steps a phone needs and which screens they open, decided from plain values (maker, Android
// version) so DeviceTest can cover phones nobody has at hand. Setup turns these into Android calls.

enum class Oem {
    SAMSUNG, XIAOMI, OPPO, VIVO, HUAWEI, OTHER;

    companion object {
        // Build.MANUFACTURER, with Build.BRAND as a second opinion for sub-brands (Redmi, POCO, realme, iQOO, Honor).
        fun from(manufacturer: String, brand: String): Oem {
            val names = listOf(manufacturer, brand).map { it.trim().lowercase(Locale.ROOT) }
            fun anyOf(vararg keys: String) = names.any { it in keys }
            return when {
                anyOf("samsung") -> SAMSUNG
                anyOf("xiaomi", "redmi", "poco") -> XIAOMI
                // OnePlus has run ColorOS underneath since OxygenOS 12.
                anyOf("oppo", "realme", "oneplus") -> OPPO
                anyOf("vivo", "iqoo") -> VIVO
                anyOf("huawei", "honor") -> HUAWEI
                else -> OTHER
            }
        }
    }
}

// A settings screen to try, described without Android types so tests can check it against the manifest.
// Opened with this app's package as data (withPackageUri) and/or as an extra (packageExtra) where the screen wants it.
data class Screen(
    val action: String? = null,
    val pkg: String? = null,
    val cls: String? = null,
    val withPackageUri: Boolean = false,
    val packageExtra: String? = null,
)

// The "don't limit this app's battery" setting. The one-tap system dialog comes first on every version; if a
// phone doesn't offer it, a settings page opens instead, whose layout moved twice ([pageNext] describes it).
enum class BatteryUi(val pageNext: Int, val hint: Int?, private val pages: List<Screen>) {
    // Android 10–11: only the Battery optimization list in Special app access.
    OPTIMIZATION_LIST(
        R.string.next_battery_list, null,
        listOf(Screen(action = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS")),
    ),

    // Android 12–13: App info → Battery → Unrestricted.
    UNRESTRICTED(R.string.next_battery_unrestricted, R.string.hint_battery_list, listOf(APP_BATTERY_PAGE)),

    // Android 14+: the same choice, now under "Allow background usage".
    BACKGROUND_USAGE(R.string.next_battery_background, R.string.hint_battery_app_info, listOf(APP_BATTERY_PAGE));

    val screens: List<Screen> get() = listOf(BATTERY_DIALOG) + pages

    companion object {
        fun forSdk(sdk: Int): BatteryUi = when {
            sdk <= 30 -> OPTIMIZATION_LIST
            sdk <= 33 -> UNRESTRICTED
            else -> BACKGROUND_USAGE
        }
    }
}

// "Allow <app> to always run in the background?", answered with one tap. Needs the
// REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission, which Google Play only allows for some kinds of apps.
private val BATTERY_DIALOG = Screen(action = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", withPackageUri = true)

// The app's own battery page. Not in the public SDK, but exported by the Settings app on Android 12+.
private val APP_BATTERY_PAGE = Screen(action = "android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL", withPackageUri = true)

// Android 10 opens the app's own popup toggle; 11+ opens the list of all apps.
fun overlayNext(sdk: Int): Int = if (sdk <= 29) R.string.next_overlay_direct else R.string.next_overlay

// A maker-specific setting that apps can't read, so the step counts as done once the user confirms it.
// [key] is where that answer is stored; never rename one, or users who answered are asked again.
class ManualStep(
    val key: String,
    val title: Int,
    val why: Int,
    val next: Int,
    val hint: Int,
    val confirm: Int,
    val screens: List<Screen>,
)

// Screens are tried in order, then App info. The class names are the ones these makers have used since
// Android 10 (the app's minimum); any that a phone lacks are simply skipped.
object ManualSteps {
    fun forOem(oem: Oem): List<ManualStep> = when (oem) {
        // No step for Samsung's "Never auto sleeping apps": an app set to Unrestricted (the battery step) can't be
        // added to it, as Unrestricted already keeps the app awake.
        Oem.SAMSUNG -> emptyList()
        Oem.XIAOMI -> listOf(
            ManualStep(
                "xiaomi_autostart_confirmed", R.string.check_xiaomi_autostart, R.string.why_xiaomi_autostart,
                R.string.next_xiaomi_autostart, R.string.hint_xiaomi_autostart, R.string.confirm_xiaomi_autostart,
                listOf(Screen(pkg = MIUI, cls = "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            ),
            ManualStep(
                "xiaomi_popups_confirmed", R.string.check_xiaomi_popups, R.string.why_xiaomi_popups,
                R.string.next_xiaomi_popups, R.string.hint_xiaomi_popups, R.string.confirm_xiaomi_popups,
                listOf(
                    Screen(
                        action = "miui.intent.action.APP_PERM_EDITOR", pkg = MIUI,
                        cls = "com.miui.permcenter.permissions.PermissionsEditorActivity", packageExtra = "extra_pkgname",
                    ),
                    Screen(
                        action = "miui.intent.action.APP_PERM_EDITOR", pkg = MIUI,
                        cls = "com.miui.permcenter.permissions.AppPermissionsEditorActivity", packageExtra = "extra_pkgname",
                    ),
                ),
            ),
        )
        Oem.OPPO -> listOf(
            ManualStep(
                "oppo_background_confirmed", R.string.check_oppo_background, R.string.why_oppo_background,
                R.string.next_oppo_background, R.string.hint_oppo_background, R.string.confirm_oppo_background,
                listOf(
                    Screen(pkg = "com.coloros.safecenter", cls = "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    Screen(pkg = "com.coloros.safecenter", cls = "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    Screen(pkg = "com.oneplus.security", cls = "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                ),
            ),
        )
        Oem.VIVO -> listOf(
            ManualStep(
                "vivo_background_confirmed", R.string.check_vivo_background, R.string.why_vivo_background,
                R.string.next_vivo_background, R.string.hint_vivo_background, R.string.confirm_vivo_background,
                listOf(
                    Screen(pkg = "com.vivo.permissionmanager", cls = "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    Screen(pkg = "com.iqoo.secure", cls = "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                    Screen(pkg = "com.iqoo.secure", cls = "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                ),
            ),
        )
        Oem.HUAWEI -> listOf(
            ManualStep(
                "huawei_launch_confirmed", R.string.check_huawei_launch, R.string.why_huawei_launch,
                R.string.next_huawei_launch, R.string.hint_huawei_launch, R.string.confirm_huawei_launch,
                listOf(
                    Screen(pkg = "com.huawei.systemmanager", cls = "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    Screen(pkg = "com.hihonor.systemmanager", cls = "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ),
            ),
        )
        Oem.OTHER -> emptyList()
    }

    private const val MIUI = "com.miui.securitycenter"
}
