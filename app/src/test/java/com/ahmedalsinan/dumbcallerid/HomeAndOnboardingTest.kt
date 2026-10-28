package com.ahmedalsinan.dumbcallerid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAndOnboardingTest {
    @Test
    fun homeStatusPriority() {
        assertEquals(HomeStatus.UNSUPPORTED, HomeStatus.of(supported = false, enabled = true, setupComplete = true))
        assertEquals(HomeStatus.OFF, HomeStatus.of(supported = true, enabled = false, setupComplete = false))
        assertEquals(HomeStatus.NEEDS_SETUP, HomeStatus.of(supported = true, enabled = true, setupComplete = false))
        assertEquals(HomeStatus.ACTIVE, HomeStatus.of(supported = true, enabled = true, setupComplete = true))
    }

    @Test
    fun setupWarningShowsWhileSwitchedOffButNotOnUnsupportedPhones() {
        assertTrue(showsSetupWarning(supported = true, setupComplete = false))
        assertFalse(showsSetupWarning(supported = true, setupComplete = true))
        assertFalse(showsSetupWarning(supported = false, setupComplete = false))
    }

    @Test
    fun nextStepSkipsDoneAndSkippedSteps() {
        val steps = intArrayOf(0, 2, 5)
        assertEquals(0, nextStep(steps, emptySet()) { false })
        assertEquals(1, nextStep(steps, setOf(0)) { false })
        assertEquals(2, nextStep(steps, setOf(0)) { it == 2 }) // step 2 was done in a settings screen meanwhile
    }

    @Test
    fun noStepLeftWhenAllAreDoneOrSkipped() {
        assertEquals(-1, nextStep(intArrayOf(0, 2), setOf(0)) { it == 2 })
        assertEquals(-1, nextStep(intArrayOf(), emptySet()) { false })
    }
}
