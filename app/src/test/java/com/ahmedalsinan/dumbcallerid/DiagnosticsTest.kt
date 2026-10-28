package com.ahmedalsinan.dumbcallerid

import com.ahmedalsinan.dumbcallerid.RecentCalls.Call
import com.ahmedalsinan.dumbcallerid.RecentCalls.Outcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DiagnosticsTest {
    private val report = Diagnostics.format(
        appVersion = "1.0",
        device = "samsung SM-S936B (samsung)",
        android = "16 (SDK 36)",
        maker = Oem.SAMSUNG,
        battery = BatteryUi.BACKGROUND_USAGE,
        supported = true,
        enabled = true,
        checks = listOf("check_role" to true, "check_battery" to false),
        calls = listOf(Call(0L, "+966500000000", Outcome.POPUP), Call(0L, null, Outcome.NO_NUMBER)),
    )

    @Test
    fun showsThePhoneAndWhatIsMissing() {
        assertTrue(report.contains("samsung SM-S936B"))
        assertTrue(report.contains("16 (SDK 36)"))
        assertTrue(report.contains("done    check_role"))
        assertTrue(report.contains("MISSING check_battery"))
        assertTrue(report.contains("POPUP"))
        assertTrue(report.contains("NO_NUMBER"))
    }

    // It's meant to be pasted into a chat, so callers' numbers must never be in it.
    @Test
    fun containsNoPhoneNumbers() {
        assertFalse(report.contains("966500000000"))
        assertFalse(report.contains("500000000"))
    }

    @Test
    fun collectsFromARealPhoneSetup() {
        RecentCalls.record(RuntimeEnvironment.getApplication(), "+966511111111", Outcome.POPUP)
        val collected = Diagnostics.collect(RuntimeEnvironment.getApplication())
        for (check in listOf("check_role", "check_overlay", "check_battery", "check_telegram")) {
            assertTrue("missing $check", collected.contains(check))
        }
        assertFalse(collected.contains("511111111"))
    }
}
