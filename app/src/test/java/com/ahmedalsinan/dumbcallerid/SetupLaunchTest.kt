package com.ahmedalsinan.dumbcallerid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class SetupLaunchTest {
    private fun newActivity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    // An app whose screens Android refuses to start: the first throws as a missing screen, the next as a
    // permission-guarded one (how Samsung's One UI 9 refused "Background usage limits").
    private class RefusingActivity : Activity() {
        val refused = mutableListOf<String?>()
        override fun startActivity(intent: Intent) {
            when (intent.action) {
                "com.example.MISSING" -> { refused += intent.action; throw android.content.ActivityNotFoundException() }
                "com.example.GUARDED" -> { refused += intent.action; throw SecurityException("Permission Denial") }
                else -> super.startActivity(intent)
            }
        }
    }

    @Test
    fun refusedScreensFallThroughToTheNextOne() {
        val activity = Robolectric.buildActivity(RefusingActivity::class.java).setup().get()
        val started = SettingsLauncher.startFirst(
            activity,
            listOf(Intent("com.example.MISSING"), Intent("com.example.GUARDED"), Intent("com.example.OPEN")),
        )
        assertTrue(started)
        assertEquals(listOf("com.example.MISSING", "com.example.GUARDED"), activity.refused)
        assertEquals("com.example.OPEN", shadowOf(activity).nextStartedActivity.action)
    }

    @Test
    fun saysSoWhenNothingCanBeOpened() {
        val activity = Robolectric.buildActivity(RefusingActivity::class.java).setup().get()
        assertFalse(SettingsLauncher.startFirst(activity, listOf(Intent("com.example.MISSING"), Intent("com.example.GUARDED"))))
        assertEquals(activity.getString(R.string.open_failed), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun settingsScreensFallBackToAppInfo() {
        val activity = Robolectric.buildActivity(RefusingActivity::class.java).setup().get()
        SettingsLauncher.openSettings(activity, "com.example.GUARDED")
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:${activity.packageName}", started.data.toString())
    }

    // The "Did you do it?" question must survive the phone rotating (a new screen) until it's answered.
    @Test
    fun pendingQuestionSurvivesUntilAnswered() {
        val step = ManualSteps.forOem(Oem.VIVO).single()
        Prefs.setPendingStep(newActivity(), step.key)

        Setup.confirmPendingStep(newActivity()) {} // asked, then the screen is "rotated" away unanswered
        assertEquals(step.key, Prefs.pendingStep(newActivity()))

        var confirmed = false
        Setup.confirmPendingStep(newActivity()) { confirmed = true }
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(confirmed)
        assertTrue(Prefs.isConfirmed(newActivity(), step.key))
        assertNull(Prefs.pendingStep(newActivity()))
    }

    @Test
    fun notYetClearsTheQuestionWithoutConfirming() {
        val step = ManualSteps.forOem(Oem.XIAOMI).first()
        Prefs.setPendingStep(newActivity(), step.key)
        Setup.confirmPendingStep(newActivity()) {}
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(Prefs.isConfirmed(newActivity(), step.key))
        assertNull(Prefs.pendingStep(newActivity()))
    }

    @Test
    fun resumingTheSameScreenDoesNotStackQuestions() {
        val activity = newActivity()
        Prefs.setPendingStep(activity, ManualSteps.forOem(Oem.VIVO).single().key)
        Setup.confirmPendingStep(activity) {}
        val first = ShadowDialog.getLatestDialog()
        Setup.confirmPendingStep(activity) {}
        assertTrue(first === ShadowDialog.getLatestDialog())
    }

    @Test
    fun forgetsAQuestionForAStepThatNoLongerExists() {
        Prefs.setPendingStep(newActivity(), "removed_step")
        Setup.confirmPendingStep(newActivity()) {}
        assertNull(Prefs.pendingStep(newActivity()))
    }
}
