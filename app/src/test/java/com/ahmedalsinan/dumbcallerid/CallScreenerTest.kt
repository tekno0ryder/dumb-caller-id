package com.ahmedalsinan.dumbcallerid

import com.ahmedalsinan.dumbcallerid.RecentCalls.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class CallScreenerTest {
    @Test
    fun showsPopupWhenEverythingIsAllowed() = assertEquals(Outcome.POPUP, decideCall("+966500000000", true, true))

    @Test
    fun hiddenNumberWinsOverEverythingElse() = assertEquals(Outcome.NO_NUMBER, decideCall(null, false, false))

    @Test
    fun switchedOffSkipsThePopup() = assertEquals(Outcome.DISABLED, decideCall("+966500000000", false, true))

    @Test
    fun missingPermissionIsReported() = assertEquals(Outcome.POPUP_BLOCKED, decideCall("+966500000000", true, false))
}
