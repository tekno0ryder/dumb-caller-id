package com.ahmedalsinan.dumbcallerid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PrefsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun popupIsOnUntilSwitchedOff() {
        assertTrue(Prefs.isEnabled(context))
        Prefs.setEnabled(context, false)
        assertFalse(Prefs.isEnabled(context))
    }

    @Test
    fun botNameIsSavedCleanedAndResettable() {
        assertEquals("dlilcomApp_bot", Prefs.botName(context))
        assertTrue(Prefs.setBotName(context, "https://t.me/MyLookupBot"))
        assertEquals("MyLookupBot", Prefs.botName(context))
        assertTrue(Prefs.setBotName(context, ""))
        assertEquals("dlilcomApp_bot", Prefs.botName(context))
    }

    @Test
    fun invalidBotNameChangesNothing() {
        Prefs.setBotName(context, "MyLookupBot")
        assertFalse(Prefs.setBotName(context, "my bot"))
        assertEquals("MyLookupBot", Prefs.botName(context))
    }

    @Test
    fun manualStepsAreConfirmedPerKey() {
        val (first, second) = ManualSteps.forOem(Oem.XIAOMI).map { it.key }
        Prefs.setConfirmed(context, first)
        assertTrue(Prefs.isConfirmed(context, first))
        assertFalse(Prefs.isConfirmed(context, second))
    }
}
