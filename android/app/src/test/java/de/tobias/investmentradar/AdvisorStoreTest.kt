package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvisorStoreTest {
    @Test
    fun firstResultBecomesCurrentAndReliableBaseline() {
        val result = result(AdvisorSignal.HALTEN, true)
        val next = AdvisorHistoryState.record(AdvisorSnapshot(), result, "2026-09-06")

        assertEquals(result, next.current?.result)
        assertEquals(result, next.lastReliable?.result)
        assertNull(next.previous)
    }

    @Test
    fun secondResultKeepsExactlyOnePreviousResult() {
        val first = result(AdvisorSignal.HALTEN, true)
        val second = result(AdvisorSignal.NACHKAUFEN, true)
        val afterFirst = AdvisorHistoryState.record(AdvisorSnapshot(), first, "2026-09-05")
        val afterSecond = AdvisorHistoryState.record(afterFirst, second, "2026-09-06")

        assertEquals(second, afterSecond.current?.result)
        assertEquals(first, afterSecond.previous?.result)
        assertEquals(second, afterSecond.lastReliable?.result)
    }

    @Test
    fun unreliableResultDoesNotDestroyLastReliableAssessment() {
        val reliable = result(AdvisorSignal.HALTEN, true)
        val unreliable = result(AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG, false)
        val baseline = AdvisorHistoryState.record(AdvisorSnapshot(), reliable, "2026-09-05")
        val next = AdvisorHistoryState.record(baseline, unreliable, "2026-09-06")

        assertEquals(unreliable, next.current?.result)
        assertEquals(reliable, next.previous?.result)
        assertEquals(reliable, next.lastReliable?.result)
    }

    @Test
    fun sameDaySameResultIsIdempotent() {
        val result = result(AdvisorSignal.HALTEN, true)
        val once = AdvisorHistoryState.record(AdvisorSnapshot(), result, "2026-09-06")
        val twice = AdvisorHistoryState.record(once, result, "2026-09-06")

        assertEquals(once, twice)
    }

    private fun result(signal: AdvisorSignal, reliable: Boolean) = AdvisorResult(
        instrumentId = "meta",
        signal = signal,
        score = if (reliable) 64 else null,
        reliable = reliable,
        reasons = listOf("test"),
        risks = emptyList()
    )
}
