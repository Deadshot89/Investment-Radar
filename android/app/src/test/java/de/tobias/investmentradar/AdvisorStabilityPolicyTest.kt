package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class AdvisorStabilityPolicyTest {
    @Test
    fun firstOrdinaryHaltenToReduzierenEscalationStaysOnPreviousReliableAction() {
        val previous = snapshot(
            current = result(AdvisorSignal.HALTEN, score = 68),
            previous = result(AdvisorSignal.HALTEN, score = 70),
            lastReliable = result(AdvisorSignal.HALTEN, score = 68)
        )
        val proposed = result(AdvisorSignal.REDUZIEREN, score = 55)

        val resolved = AdvisorStabilityPolicy.resolve(previous, proposed)

        assertEquals(AdvisorSignal.HALTEN, resolved.signal)
        assertEquals(68, resolved.score)
    }

    @Test
    fun secondConsecutiveOrdinaryWorseSignalIsConfirmed() {
        val firstReduce = result(AdvisorSignal.REDUZIEREN, score = 55)
        val previous = snapshot(
            current = firstReduce,
            previous = result(AdvisorSignal.HALTEN, score = 68),
            lastReliable = result(AdvisorSignal.HALTEN, score = 68)
        )
        val proposed = result(AdvisorSignal.REDUZIEREN, score = 54)

        val resolved = AdvisorStabilityPolicy.resolve(previous, proposed)

        assertEquals(AdvisorSignal.REDUZIEREN, resolved.signal)
        assertEquals(54, resolved.score)
    }

    @Test
    fun secondConsecutiveWorseProposalIsConfirmedAfterRealFirstDayRecording() {
        val before = snapshot(
            current = result(AdvisorSignal.HALTEN, score = 68),
            previous = result(AdvisorSignal.HALTEN, score = 70),
            lastReliable = result(AdvisorSignal.HALTEN, score = 68)
        )
        val firstProposal = result(AdvisorSignal.REDUZIEREN, score = 55)
        val firstResolved = AdvisorStabilityPolicy.resolve(before, firstProposal)
        val afterFirstDay = AdvisorHistoryState.record(before, firstResolved, "2026-09-06")

        val secondResolved = AdvisorStabilityPolicy.resolve(
            afterFirstDay,
            result(AdvisorSignal.REDUZIEREN, score = 54)
        )

        assertEquals(AdvisorSignal.REDUZIEREN, secondResolved.signal)
        assertEquals(54, secondResolved.score)
    }

    @Test
    fun severeReliableSellMayEscalateImmediately() {
        val previous = snapshot(
            current = result(AdvisorSignal.HALTEN, score = 70),
            previous = result(AdvisorSignal.HALTEN, score = 72),
            lastReliable = result(AdvisorSignal.HALTEN, score = 70)
        )
        val proposed = result(AdvisorSignal.VERKAUFEN, score = 24)

        val resolved = AdvisorStabilityPolicy.resolve(previous, proposed)

        assertEquals(AdvisorSignal.VERKAUFEN, resolved.signal)
        assertEquals(24, resolved.score)
    }

    @Test
    fun largeReliableScoreDropMayEscalateImmediately() {
        val previous = snapshot(
            current = result(AdvisorSignal.HALTEN, score = 72),
            previous = result(AdvisorSignal.HALTEN, score = 74),
            lastReliable = result(AdvisorSignal.HALTEN, score = 72)
        )
        val proposed = result(AdvisorSignal.REDUZIEREN, score = 47)

        val resolved = AdvisorStabilityPolicy.resolve(previous, proposed)

        assertEquals(AdvisorSignal.REDUZIEREN, resolved.signal)
        assertEquals(47, resolved.score)
    }

    private fun snapshot(
        current: AdvisorResult,
        previous: AdvisorResult,
        lastReliable: AdvisorResult
    ) = AdvisorSnapshot(
        current = DatedAdvisorResult("2026-09-05", current),
        previous = DatedAdvisorResult("2026-09-04", previous),
        lastReliable = DatedAdvisorResult("2026-09-05", lastReliable)
    )

    private fun result(signal: AdvisorSignal, score: Int) = AdvisorResult(
        instrumentId = "meta",
        signal = signal,
        score = score,
        reliable = true,
        reasons = listOf("test"),
        risks = emptyList(),
        confidencePct = 80,
        timingFactor = 1.0
    )
}
