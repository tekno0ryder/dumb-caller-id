package com.ahmedalsinan.dumbcallerid

import android.content.Intent
import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

// What happens when Android's installer reports back on an update.
@RunWith(RobolectricTestRunner::class)
class UpdateInstallReceiverTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun report(status: Int, extras: Intent.() -> Unit = {}) =
        UpdateInstallReceiver().onReceive(context, Intent().putExtra(PackageInstaller.EXTRA_STATUS, status).apply(extras))

    @Test
    fun showsAndroidsConfirmationScreen() {
        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL")
        report(PackageInstaller.STATUS_PENDING_USER_ACTION) { putExtra(Intent.EXTRA_INTENT, confirm) }
        val started = shadowOf(context).nextStartedActivity
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", started.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun successNeedsNothingMore() {
        report(PackageInstaller.STATUS_SUCCESS)
        assertNull(shadowOf(context).nextStartedActivity)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun failureTellsTheUser() {
        report(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
        assertEquals(context.getString(R.string.update_failed), ShadowToast.getTextOfLatestToast())
    }

    // E.g. an APK signed with another key: Android refuses it, and the user is told.
    @Test
    fun conflictTellsTheUser() {
        report(PackageInstaller.STATUS_FAILURE_CONFLICT)
        assertEquals(context.getString(R.string.update_failed), ShadowToast.getTextOfLatestToast())
    }
}
