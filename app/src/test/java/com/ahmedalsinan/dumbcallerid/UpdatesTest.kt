package com.ahmedalsinan.dumbcallerid

import android.app.NotificationManager
import android.app.job.JobScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.IOException

// Robolectric for its real org.json, and to start the app as a phone would.
@RunWith(RobolectricTestRunner::class)
class UpdatesTest {
    private fun release(tag: String, assets: String, extra: String = "") =
        """{"tag_name":"$tag",$extra"assets":[$assets]}"""

    private val apk = """{"name":"dumb-caller-id-v1.2.apk","browser_download_url":"https://github.com/x/releases/download/v1.2/dumb-caller-id-v1.2.apk"}"""

    @Test
    fun readsTheApkFromTheLatestRelease() {
        val parsed = Updates.parseLatest(release("v1.2", """{"name":"notes.txt","browser_download_url":"n"},$apk"""))
        assertEquals("v1.2", parsed?.tag)
        assertTrue(parsed!!.apkUrl.endsWith("dumb-caller-id-v1.2.apk"))
    }

    @Test
    fun ignoresReleasesWithoutAnApkOrNotFinal() {
        assertNull(Updates.parseLatest(release("v1.2", "")))
        assertNull(Updates.parseLatest(release("v1.2", apk, """"draft":true,""")))
        assertNull(Updates.parseLatest(release("v1.2", apk, """"prerelease":true,""")))
        assertNull(Updates.parseLatest("not json"))
    }

    @Test
    fun comparesVersionsAsNumbers() {
        assertTrue(Updates.isNewer("v1.10", "1.9"))
        assertTrue(Updates.isNewer("v1.1", "1.0"))
        assertTrue(Updates.isNewer("v2", "1.9.9"))
        assertFalse(Updates.isNewer("v1.0", "1.0"))
        assertFalse(Updates.isNewer("v1.0.0", "1.0"))
        assertFalse(Updates.isNewer("v0.9", "1.0"))
        assertFalse(Updates.isNewer("v1.1-beta", "1.0")) // not plain numbers: never offered
    }

    // Starting the app (which every incoming call does) schedules the daily check, once.
    @Test
    fun appStartSchedulesTheDailyCheck() {
        val context = RuntimeEnvironment.getApplication()
        Updates.schedule(context)
        val jobs = context.getSystemService(JobScheduler::class.java).allPendingJobs
        assertEquals(1, jobs.size)
        assertEquals(UpdateJob::class.java.name, jobs.single().service.className)
    }

    @Test
    fun nothingAvailableBeforeAnyCheck() = assertNull(Updates.available(RuntimeEnvironment.getApplication()))

    // The daily check, with GitHub replaced by a fake response.
    private val context get() = RuntimeEnvironment.getApplication()
    private val installed get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName!!
    private fun latest(tag: String) = release(tag, apk.replace("v1.2", tag))

    @Test
    fun offersANewerRelease() {
        Updates.check(context) { latest("v99.0") }
        assertEquals("v99.0", Updates.available(context)?.tag)
        assertTrue(Updates.available(context)!!.apkUrl.endsWith("dumb-caller-id-v99.0.apk"))
    }

    @Test
    fun doesNotOfferTheInstalledVersion() {
        Updates.check(context) { latest("v$installed") }
        assertNull(Updates.available(context))
    }

    // A check finding nothing newer (or no release at all: GitHub answers 404) withdraws an older offer.
    @Test
    fun clearsAnOfferWhenNothingIsNewer() {
        Updates.check(context) { latest("v99.0") }
        Updates.check(context) { null }
        assertNull(Updates.available(context))
    }

    // Offline or GitHub down: keep what the last successful check found, and try again next time.
    @Test
    fun networkFailureKeepsThePreviousOffer() {
        Updates.check(context) { latest("v99.0") }
        Updates.check(context) { throw IOException("offline") }
        assertEquals("v99.0", Updates.available(context)?.tag)
    }

    @Test
    fun asksTheConfiguredAddressOnly() {
        var asked = ""
        Updates.check(context) { url -> asked = url; null }
        assertEquals(context.getString(R.string.update_url), asked)
    }

    // Release builds must look at this repository's latest GitHub release; only debug builds may use the local
    // server of scripts/try-update.sh. These tests run the debug build, so check the build file itself.
    @Test
    fun releaseBuildsCheckGitHubAndOnlyDebugBuildsCheckThisComputer() {
        val build = java.io.File("build.gradle.kts").readText()
        val defaults = build.substringAfter("defaultConfig {").substringBefore("\n    }")
        assertTrue(defaults.contains("https://api.github.com/repos/tekno0ryder/dumb-caller-id/releases/latest"))
        val debug = build.substringAfter("        debug {").substringBefore("\n        }")
        assertTrue(debug.contains("http://127.0.0.1:8765/latest.json"))
        assertFalse(build.substringAfter("        release {").substringBefore("\n        }").contains("update_url"))
    }

    private fun notifications() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun announcesANewVersionWithANotification() {
        Updates.check(context) { latest("v99.0") }
        val notification = notifications().single()
        assertEquals(context.getString(R.string.update_available, "v99.0"), notification.extras.getString("android.title"))
        // Tapping it opens the home screen, where the Update button is.
        val tap = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tap.component?.className)
    }

    // The daily check finds the same version again and again; announce it once.
    @Test
    fun announcesEachVersionOnlyOnce() {
        Updates.check(context) { latest("v99.0") }
        context.getSystemService(NotificationManager::class.java).cancelAll() // the user dismissed it
        Updates.check(context) { latest("v99.0") }
        assertEquals(0, notifications().size)
        Updates.check(context) { latest("v99.1") }
        assertEquals(1, notifications().size)
    }

    @Test
    fun noNotificationWithoutANewVersion() {
        Updates.check(context) { latest("v$installed") }
        assertEquals(0, notifications().size)
    }

    // Notifications switched off: nothing shown, and it's announced once they're allowed.
    @Test
    fun waitsUntilNotificationsAreAllowed() {
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        manager.setNotificationsEnabled(false)
        Updates.check(context) { latest("v99.0") }
        assertEquals(0, notifications().size)
        manager.setNotificationsEnabled(true)
        Updates.check(context) { latest("v99.0") }
        assertEquals(1, notifications().size)
    }

    @Test
    fun staleNotificationIsRemoved() {
        Updates.check(context) { latest("v99.0") }
        Updates.check(context) { null }
        assertEquals(0, notifications().size)
    }

    // Notifications only announce updates, so turning them off must never make the home screen ask for setup.
    @Test
    fun notificationStepIsOptional() =
        assertFalse(Setup.checks.single { it.title == R.string.check_notifications }.required)

    // The app starts for every incoming call, so a refused job (Android 17 crashed the app this way when a
    // permission was missing) must cost only the update check.
    @Test
    fun refusedScheduleNeverCrashesTheApp() {
        val refusing = object : android.content.ContextWrapper(context) {
            override fun getSystemService(name: String): Any? =
                if (name == JOB_SCHEDULER_SERVICE) throw SecurityException("ACCESS_NETWORK_STATE required") else super.getSystemService(name)
        }
        Updates.schedule(refusing)
    }

    // Android 17+ refuses a job that waits for internet without this permission, which crashed the app on start.
    @Test
    fun declaresThePermissionsTheUpdateJobNeeds() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        for (permission in listOf("INTERNET", "ACCESS_NETWORK_STATE")) {
            assertTrue("missing $permission", manifest.contains("android.permission.$permission\""))
        }
    }

    // Tapping the version uses this to say "latest version" only when GitHub was actually reached.
    @Test
    fun reportsWhetherGitHubWasReached() {
        assertTrue(Updates.check(context) { null })
        assertFalse(Updates.check(context) { throw IOException("offline") })
    }
}
