package com.ahmedalsinan.dumbcallerid

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

// How setup steps pick a settings screen: skip what's missing or forbidden, else fall back to App info.
@RunWith(RobolectricTestRunner::class)
class SetupScreensTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun missingScreenIsNotStartable() = assertFalse(SettingsLauncher.canStart(activity, Intent("com.example.MISSING")))

    @Test
    fun exportedScreenIsStartable() {
        register("com.example.OPEN")
        assertTrue(SettingsLauncher.canStart(activity, Intent("com.example.OPEN")))
    }

    // Samsung's One UI 9 guards "Background usage limits" with a system-only permission; starting it crashed the app.
    @Test
    fun screenGuardedByAPermissionWeLackIsSkipped() {
        register("com.example.GUARDED", permission = "android.permission.READ_SEARCH_INDEXABLES")
        assertFalse(SettingsLauncher.canStart(activity, Intent("com.example.GUARDED")))
    }

    @Test
    fun privateScreenIsSkipped() {
        register("com.example.PRIVATE", exported = false)
        assertFalse(SettingsLauncher.canStart(activity, Intent("com.example.PRIVATE")))
    }

    @Test
    fun opensTheFirstAllowedScreen() {
        register("com.example.GUARDED", permission = "android.permission.READ_SEARCH_INDEXABLES")
        register("com.example.OPEN")
        SettingsLauncher.openFirstAllowed(
            activity,
            listOf(Screen(action = "com.example.MISSING"), Screen(action = "com.example.GUARDED"), Screen(action = "com.example.OPEN")),
        )
        assertEquals("com.example.OPEN", shadowOf(activity).nextStartedActivity.action)
    }

    @Test
    fun fallsBackToAppInfo() {
        SettingsLauncher.openFirstAllowed(activity, listOf(Screen(action = "com.example.MISSING")))
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:${activity.packageName}", started.data.toString())
    }

    @Test
    fun passesThisAppToScreensThatAskForIt() {
        register("com.example.PER_APP")
        SettingsLauncher.openFirstAllowed(activity, listOf(Screen(action = "com.example.PER_APP", packageExtra = "extra_pkgname")))
        assertEquals(activity.packageName, shadowOf(activity).nextStartedActivity.getStringExtra("extra_pkgname"))
    }

    private fun register(action: String, exported: Boolean = true, permission: String? = null) {
        val component = ComponentName("com.example", "com.example.${action.substringAfterLast('.')}")
        val pm = shadowOf(activity.packageManager)
        pm.addActivityIfNotPresent(component).apply {
            this.exported = exported
            this.permission = permission
        }
        pm.addIntentFilterForActivity(component, IntentFilter(action).apply { addCategory(Intent.CATEGORY_DEFAULT) })
    }
}
