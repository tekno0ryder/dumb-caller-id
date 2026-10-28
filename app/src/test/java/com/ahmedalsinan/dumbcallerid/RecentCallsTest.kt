package com.ahmedalsinan.dumbcallerid

import com.ahmedalsinan.dumbcallerid.RecentCalls.Call
import com.ahmedalsinan.dumbcallerid.RecentCalls.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Robolectric for its real org.json; plain unit tests only see Android's stubs.
@RunWith(RobolectricTestRunner::class)
class RecentCallsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun keepsTheNewestCallsFirstUpToTheLimit() {
        var calls = emptyList<Call>()
        for (i in 1..RecentCalls.MAX_CALLS + 3) calls = RecentCalls.prepend(Call(i.toLong(), "+$i", Outcome.POPUP), calls)
        assertEquals(RecentCalls.MAX_CALLS, calls.size)
        assertEquals((RecentCalls.MAX_CALLS + 3).toLong(), calls.first().time)
    }

    @Test
    fun roundTripsEveryOutcomeAndHiddenNumbers() {
        val calls = Outcome.entries.mapIndexed { i, outcome -> Call(i.toLong(), if (i == 0) null else "+$i", outcome) }
        val decoded = RecentCalls.decode(RecentCalls.encode(calls))
        assertEquals(calls.map { Triple(it.time, it.number, it.outcome) }, decoded.map { Triple(it.time, it.number, it.outcome) })
        assertNull(decoded.first().number)
    }

    @Test
    fun damagedDataMeansNoHistory() {
        assertTrue(RecentCalls.decode(null).isEmpty())
        assertTrue(RecentCalls.decode("not json").isEmpty())
        assertTrue(RecentCalls.decode("""[{"number":"+1"}]""").isEmpty())
    }

    @Test
    fun dropsOutcomesThisVersionDoesNotKnow() {
        val stored = """[{"time":1,"number":"+1","outcome":"SOMETHING_NEW"},{"time":2,"number":"+2","outcome":"POPUP"}]"""
        assertEquals(listOf(2L), RecentCalls.decode(stored).map { it.time })
    }

    @Test
    fun recentCallsShowHowLongAgo() {
        val now = 1_800_000_000_000L
        val popup = context.getString(R.string.outcome_popup)
        assertEquals("Just now · $popup", RecentCalls.detail(context, Call(now - 20_000, "+1", Outcome.POPUP), now))
        assertEquals("5 min. ago · $popup", RecentCalls.detail(context, Call(now - 5 * 60_000, "+1", Outcome.POPUP), now))
        assertTrue(RecentCalls.detail(context, Call(now - 3 * 3_600_000, "+1", Outcome.POPUP), now).startsWith("3 hr. ago"))
    }

    @Test
    fun olderCallsShowTheDate() {
        val now = 1_800_000_000_000L
        val detail = RecentCalls.detail(context, Call(now - 3 * 86_400_000L, "+1", Outcome.POPUP), now)
        assertFalse(detail.contains("ago"))
    }

    @Test
    fun recordsLoadsAndClears() {
        RecentCalls.record(context, "+1", Outcome.POPUP)
        RecentCalls.record(context, null, Outcome.NO_NUMBER)
        assertEquals(listOf(null, "+1"), RecentCalls.load(context).map { it.number })
        RecentCalls.clear(context)
        assertTrue(RecentCalls.load(context).isEmpty())
    }
}
