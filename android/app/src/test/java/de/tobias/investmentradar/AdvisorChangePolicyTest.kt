package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorChangePolicyTest {
    @Test
    fun firstReliableHoldingSignalCreatesOneStableSignalChangeEvent() {
        val current = result(AdvisorSignal.HALTEN, reliable = true)

        val event = AdvisorChangePolicy.notificationEvent(
            previous = null,
            current = current,
            analysisDay = "2026-09-06",
            isHolding = true
        )

        assertEquals("meta|NONE|HALTEN|2026-09-06", event?.id)
        assertEquals(AdvisorNotificationEventKind.SIGNAL_CHANGE, event?.kind)
    }

    @Test
    fun unchangedSignalCreatesNoEvent() {
        val previous = result(AdvisorSignal.HALTEN, reliable = true)
        val current = result(AdvisorSignal.HALTEN, reliable = true)

        assertNull(AdvisorChangePolicy.notificationEvent(previous, current, "2026-09-06", isHolding = true))
    }

    @Test
    fun newlyReliableScore82RadarCandidateCreatesOpportunityEvent() {
        val event = AdvisorChangePolicy.notificationEvent(
            previous = null,
            current = result(AdvisorSignal.NACHKAUFEN, reliable = true, score = 82),
            analysisDay = "2026-09-06",
            isHolding = false
        )

        assertEquals(AdvisorNotificationEventKind.NEW_STRONG_OPPORTUNITY, event?.kind)
        assertEquals("opportunity|meta|2026-09-06", event?.id)
    }

    @Test
    fun newlyReliableScore80RadarCandidateCreatesNoOpportunityEvent() {
        assertNull(
            AdvisorChangePolicy.notificationEvent(
                previous = null,
                current = result(AdvisorSignal.NACHKAUFEN, reliable = true, score = 80),
                analysisDay = "2026-09-06",
                isHolding = false
            )
        )
    }

    @Test
    fun lossOfReliableAssessmentCreatesReliabilityLostEvent() {
        val previous = result(AdvisorSignal.HALTEN, reliable = true)
        val current = result(AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG, reliable = false)

        val event = AdvisorChangePolicy.notificationEvent(previous, current, "2026-09-06", isHolding = true)

        assertTrue(event != null)
        assertEquals(AdvisorNotificationEventKind.RELIABILITY_LOST, event?.kind)
    }

    @Test
    fun unchangedPlanCreatesNoPlanEvent() {
        val plan = plan(reallocationAmount = 20)

        assertTrue(AdvisorChangePolicy.planEvents(plan, plan, "2026-09-06").isEmpty())
    }

    @Test
    fun fifteenEuroReallocationCreatesNoEvent() {
        val current = plan(reallocationAmount = 15)

        assertTrue(AdvisorChangePolicy.planEvents(null, current, "2026-09-06").isEmpty())
    }

    @Test
    fun twentyEuroReallocationCreatesOneStableEvent() {
        val current = plan(reallocationAmount = 20)

        val events = AdvisorChangePolicy.planEvents(null, current, "2026-09-06")

        assertEquals(1, events.size)
        assertEquals(AdvisorNotificationEventKind.REALLOCATION, events.single().kind)
        assertEquals("reallocation|meta|msft|20|2026-09-06", events.single().id)
    }

    private fun plan(reallocationAmount: Int) = PortfolioAdvisorPlan(
        budgetEur = 100,
        allocations = emptyList(),
        cashEur = 100,
        reallocations = listOf(
            ReallocationSuggestion(
                fromItemId = "meta",
                toItemId = "msft",
                amountEur = reallocationAmount,
                reason = "test"
            )
        ),
        savingsPlanConflicts = emptyList(),
        candidates = emptyList()
    )

    private fun result(
        signal: AdvisorSignal,
        reliable: Boolean,
        score: Int? = if (reliable) 65 else null
    ) = AdvisorResult(
        instrumentId = "meta",
        signal = signal,
        score = score,
        reliable = reliable,
        reasons = listOf("test"),
        risks = emptyList()
    )
}
