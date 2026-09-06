package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun unchangedDailyResultDoesNotGrowMaterialHistory() {
        val previous = result(AdvisorSignal.HALTEN, true)
        val history = listOf(
            AdvisorHistoryEntry(
                analysisDay = "2026-09-05",
                previousSignal = null,
                newSignal = AdvisorSignal.HALTEN,
                score = 64,
                reasons = listOf("test")
            )
        )

        val next = AdvisorMaterialHistory.record(history, previous, previous, "2026-09-06")

        assertEquals(history, next)
    }

    @Test
    fun twentyOneMaterialChangesKeepNewestTwentyOnly() {
        var history = emptyList<AdvisorHistoryEntry>()
        var previous = result(AdvisorSignal.HALTEN, true)
        repeat(21) { index ->
            val next = result(
                signal = if (index % 2 == 0) AdvisorSignal.NACHKAUFEN else AdvisorSignal.HALTEN,
                reliable = true,
                score = 70 + index
            )
            history = AdvisorMaterialHistory.record(
                history,
                previous,
                next,
                "2026-09-${(index + 1).toString().padStart(2, '0')}"
            )
            previous = next
        }

        assertEquals(20, history.size)
        assertEquals("2026-09-21", history.first().analysisDay)
        assertEquals("2026-09-02", history.last().analysisDay)
    }

    @Test
    fun reliabilityChangeIsMaterialEvenWhenSignalDoesNotChange() {
        val reliable = result(AdvisorSignal.HALTEN, true)
        val unreliableSameSignal = result(AdvisorSignal.HALTEN, false, score = null)

        val history = AdvisorMaterialHistory.record(
            emptyList(),
            reliable,
            unreliableSameSignal,
            "2026-09-06"
        )

        assertEquals(1, history.size)
    }

    @Test
    fun existingV1SnapshotJsonStillDecodes() {
        val encoded = AdvisorStore.encode(
            mapOf(
                "meta" to AdvisorHistoryState.record(
                    AdvisorSnapshot(),
                    result(AdvisorSignal.HALTEN, true),
                    "2026-09-05"
                )
            )
        )

        val decoded = AdvisorStore.decode(encoded)

        assertTrue(decoded.containsKey("meta"))
        assertEquals(AdvisorSignal.HALTEN, decoded["meta"]?.current?.result?.signal)
    }

    private fun result(
        signal: AdvisorSignal,
        reliable: Boolean,
        score: Int? = if (reliable) 64 else null
    ) = AdvisorResult(
        instrumentId = "meta",
        signal = signal,
        score = score,
        reliable = reliable,
        reasons = listOf("test"),
        risks = emptyList()
    )
}
