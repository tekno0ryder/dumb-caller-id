package com.ahmedalsinan.dumbcallerid

import android.content.Intent
import android.view.View
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

// The About card: version with an update check, and the source code link.
@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest {
    private val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    @Test
    fun sourceCodeRowShowsAndOpensTheRepository() {
        val shown = activity.findViewById<TextView>(R.id.sourceCodeUrl).text.toString()
        assertEquals("github.com/tekno0ryder/dumb-caller-id", shown)
        activity.findViewById<View>(R.id.sourceCode).performClick()
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("https://github.com/tekno0ryder/dumb-caller-id", opened.data.toString())
    }

    // The link and the update check must name the same repository, or a fork would send people elsewhere.
    @Test
    fun linkAndUpdatesUseTheSameRepository() {
        val repo = activity.getString(R.string.repo_url).removePrefix("https://github.com/")
        val build = java.io.File("build.gradle.kts").readText()
        assertTrue(build.contains("https://api.github.com/repos/$repo/releases/latest"))
    }

    // The update check is a labelled button beside the version, not a hidden tap on it.
    @Test
    fun checkForUpdatesIsAVisibleButton() {
        val button = activity.findViewById<TextView>(R.id.checkUpdates)
        assertEquals(activity.getString(R.string.check_updates), button.text.toString())
        assertTrue(button.hasOnClickListeners())
        assertFalse(activity.findViewById<View>(R.id.version).hasOnClickListeners())
    }

    // These tests run a debug build, which says so; release builds show just the number.
    @Test
    fun versionShowsTheNumber() {
        val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        assertEquals(activity.getString(R.string.version_debug, version), activity.findViewById<TextView>(R.id.version).text.toString())
    }

    // Each step's instructions live on its own setup page; Settings doesn't repeat them all in one block.
    @Test
    fun fixOpensThatStepsSetupPage() {
        val checklist = activity.findViewById<android.widget.LinearLayout>(R.id.checklist)
        val index = (0 until checklist.childCount).first {
            checklist.getChildAt(it).findViewById<View>(R.id.checkFix).visibility == View.VISIBLE
        }
        checklist.getChildAt(index).findViewById<View>(R.id.checkFix).performClick()
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(OnboardingActivity::class.java.name, opened.component?.className)
        assertEquals(index, opened.getIntExtra("step", -1))
    }

    @Test
    fun opensJustThatStep() {
        // A phone that supports caller ID apps (Robolectric's doesn't by default, which shows "Not supported").
        // Deprecated in Robolectric 4.17 without a replacement for marking a role available.
        @Suppress("DEPRECATION")
        shadowOf(activity.getSystemService(android.app.role.RoleManager::class.java))
            .addAvailableRole(android.app.role.RoleManager.ROLE_CALL_SCREENING)
        val index = Setup.checks.indexOfFirst { it.title == R.string.check_overlay }
        val page = Robolectric.buildActivity(OnboardingActivity::class.java, OnboardingActivity.forStep(activity, index)).setup().get()
        assertEquals(activity.getString(R.string.check_overlay), page.findViewById<TextView>(R.id.title).text.toString())
        assertEquals(activity.getString(R.string.onboarding_step, 1, 1), page.findViewById<TextView>(R.id.progress).text.toString())
    }

    // Only required steps are red; an optional one that's off is grey, as it doesn't stop the popup.
    @Test
    fun onlyRequiredMissingStepsAreRed() {
        fun check(required: Boolean, ok: Boolean) =
            Setup.Check(0, 0, 0, 0, isOk = { ok }, required = required) {}
        assertEquals(R.drawable.ic_check_missing, Setup.icon(check(required = true, ok = false), activity))
        assertEquals(R.drawable.ic_check_optional, Setup.icon(check(required = false, ok = false), activity))
        assertEquals(R.drawable.ic_check_ok, Setup.icon(check(required = false, ok = true), activity))
    }

    // Opened from Settings for one step: skipping it goes straight back, with no summary page.
    @Test
    fun singleStepPageClosesWhenSkipped() {
        @Suppress("DEPRECATION") // see opensJustThatStep
        shadowOf(activity.getSystemService(android.app.role.RoleManager::class.java))
            .addAvailableRole(android.app.role.RoleManager.ROLE_CALL_SCREENING)
        val index = Setup.checks.indexOfFirst { it.title == R.string.check_overlay }
        val page = Robolectric.buildActivity(OnboardingActivity::class.java, OnboardingActivity.forStep(activity, index)).setup().get()
        page.findViewById<View>(R.id.skip).performClick()
        assertTrue(page.isFinishing)
    }
}
