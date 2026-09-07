package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventAwareAdvisorPolicyTest {
    @Test
    fun unverifiedEventLeavesAdvisorResultUnchanged() {
        val base = result(AdvisorSignal.HALTEN, 60)
        val context = EventAwareAdvisorPolicy.context(
            listOf(event(verification = MarketEventVerification.UNVERIFIED))
        )

        val adjusted = EventAwareAdvisorPolicy.apply(base, context)

        assertEquals(base, adjusted)
        assertFalse(context.criticalThesisBreak)
    }

    @Test
    fun ordinaryVerifiedNegativeEventAdjustsScoreButStillUsesNormalStability() {
        val previous = snapshot(result(AdvisorSignal.HALTEN, 60))
        val base = result(AdvisorSignal.HALTEN, 60)
        val context = EventAwareAdvisorPolicy.context(
            listOf(
                event(
                    type = MarketEventType.GUIDANCE,
                    direction = MarketEventDirection.NEGATIVE,
                    materiality = 75,
                    confidencePct = 90
                )
            )
        )

        val adjusted = EventAwareAdvisorPolicy.apply(base, context)
        val resolved = AdvisorStabilityPolicy.resolve(
            previous = previous,
            proposed = adjusted,
            analysisDay = "2026-09-07",
            criticalEvent = context.criticalThesisBreak
        )

        assertTrue(adjusted.score!! < base.score!!)
        assertEquals(AdvisorSignal.REDUZIEREN, adjusted.signal)
        assertFalse(context.criticalThesisBreak)
        assertEquals(AdvisorSignal.HALTEN, resolved.signal)
        assertEquals(AdvisorSignal.REDUZIEREN, resolved.pendingWorseSignal)
    }

    @Test
    fun criticalVerifiedThesisBreakMayBypassOrdinaryMultiDayConfirmation() {
        val previous = snapshot(result(AdvisorSignal.HALTEN, 60))
        val base = result(AdvisorSignal.HALTEN, 60)
        val context = EventAwareAdvisorPolicy.context(
            listOf(
                event(
                    type = MarketEventType.PROFIT_WARNING,
                    direction = MarketEventDirection.NEGATIVE,
                    materiality = 95,
                    confidencePct = 95
                )
            )
        )

        val adjusted = EventAwareAdvisorPolicy.apply(base, context)
        val resolved = AdvisorStabilityPolicy.resolve(
            previous = previous,
            proposed = adjusted,
            analysisDay = "2026-09-07",
            criticalEvent = context.criticalThesisBreak
        )

        assertTrue(context.criticalThesisBreak)
        assertEquals(AdvisorSignal.REDUZIEREN, adjusted.signal)
        assertEquals(AdvisorSignal.REDUZIEREN, resolved.signal)
        assertEquals(null, resolved.pendingWorseSignal)
    }

    @Test
    fun positiveVerifiedEventNeverGetsCriticalNegativeBypass() {
        val context = EventAwareAdvisorPolicy.context(
            listOf(
                event(
                    type = MarketEventType.GUIDANCE,
                    direction = MarketEventDirection.POSITIVE,
                    materiality = 100,
                    confidencePct = 100
                )
            )
        )

        val adjusted = EventAwareAdvisorPolicy.apply(result(AdvisorSignal.HALTEN, 60), context)

        assertFalse(context.criticalThesisBreak)
        assertTrue(adjusted.score!! > 60)
    }

    @Test
    fun multipleVerifiedEventsHaveOneBoundedTotalScoreEffect() {
        val context = EventAwareAdvisorPolicy.context(
            listOf(
                event(eventId = "one", fingerprint = "meta|guidance|one", materiality = 100, confidencePct = 100),
                event(eventId = "two", fingerprint = "meta|guidance|two", materiality = 100, confidencePct = 100)
            )
        )

        val adjusted = EventAwareAdvisorPolicy.apply(result(AdvisorSignal.HALTEN, 60), context)

        assertEquals(45, adjusted.score)
    }

    private fun result(signal: AdvisorSignal, score: Int) = AdvisorResult(
        instrumentId = "meta",
        signal = signal,
        score = score,
        reliable = true,
        reasons = listOf("Basisbewertung"),
        risks = emptyList(),
        confidencePct = 80,
        timingFactor = 1.0
    )

    private fun snapshot(current: AdvisorResult) = AdvisorSnapshot(
        current = DatedAdvisorResult("2026-09-06", current),
        previous = DatedAdvisorResult("2026-09-05", current),
        lastReliable = DatedAdvisorResult("2026-09-06", current)
    )

    private fun event(
        eventId: String = "event-1",
        fingerprint: String = "meta|guidance|event-1",
        type: MarketEventType = MarketEventType.GUIDANCE,
        verification: MarketEventVerification = MarketEventVerification.VERIFIED_PRIMARY,
        direction: MarketEventDirection = MarketEventDirection.NEGATIVE,
        materiality: Int = 90,
        confidencePct: Int = 90
    ) = MarketEvent(
        eventId = eventId,
        instrumentId = "meta",
        type = type,
        title = "Verifiziertes Ereignis",
        summary = "Testereignis",
        eventAt = "2026-09-07T10:00:00Z",
        publishedAt = "2026-09-07T10:05:00Z",
        sourceName = "Issuer",
        sourceUrl = "https://issuer.example/event",
        verification = verification,
        direction = direction,
        horizon = MarketEventHorizon.MEDIUM,
        materiality = materiality,
        confidencePct = confidencePct,
        fingerprint = fingerprint
    )
}
