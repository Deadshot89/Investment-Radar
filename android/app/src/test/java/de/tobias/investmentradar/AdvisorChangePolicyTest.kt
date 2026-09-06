package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorChangePolicyTest {
    @Test
    fun firstReliableSignalCreatesOneStableEvent() {
        val current = result(AdvisorSignal.HALTEN, reliable = true)

        val event = AdvisorChangePolicy.notificationEvent(
            previous = null,
            current = current,
            analysisDay = "2026-09-06"
        )

        assertEquals("meta|NONE|HALTEN|2026-09-06", event?.id)
        assertEquals(AdvisorSignal.HALTEN, event?.newSignal)
    }

    @Test
    fun unchangedSignalCreatesNoEvent() {
        val previous = result(AdvisorSignal.HALTEN, reliable = true)
        val current = result(AdvisorSignal.HALTEN, reliable = true)

        assertNull(AdvisorChangePolicy.notificationEvent(previous, current, "2026-09-06"))
    }

    @Test
    fun realSignalChangeCreatesExactlyOneStableEvent() {
        val previous = result(AdvisorSignal.HALTEN, reliable = true)
        val current = result(AdvisorSignal.NACHKAUFEN, reliable = true)

        val first = AdvisorChangePolicy.notificationEvent(previous, current, "2026-09-06")
        val second = AdvisorChangePolicy.notificationEvent(previous, current, "2026-09-06")

        assertEquals("meta|HALTEN|NACHKAUFEN|2026-09-06", first?.id)
        assertEquals(first, second)
    }

    @Test
    fun lossOfReliableAssessmentCreatesDecisionRelevantEvent() {
        val previous = result(AdvisorSignal.HALTEN, reliable = true)
        val current = result(AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG, reliable = false)

        val event = AdvisorChangePolicy.notificationEvent(previous, current, "2026-09-06")

        assertTrue(event != null)
        assertEquals(AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG, event?.newSignal)
    }

    @Test
    fun firstUnreliableAssessmentDoesNotCreateNoise() {
        assertNull(
            AdvisorChangePolicy.notificationEvent(
                previous = null,
                current = result(AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG, reliable = false),
                analysisDay = "2026-09-06"
            )
        )
    }

    private fun result(signal: AdvisorSignal, reliable: Boolean) = AdvisorResult(
        instrumentId = "meta",
        signal = signal,
        score = if (reliable) 65 else null,
        reliable = reliable,
        reasons = listOf("test"),
        risks = emptyList()
    )
}
