package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioAdvisorEngineTest {
    @Test
    fun allocationsAndCashAlwaysEqualBudget() {
        val plan = PortfolioAdvisorEngine.allocate(
            candidates = listOf(
                candidate("meta", PortfolioAdvisorAction.NACHKAUFEN, score = 85, timing = 1.0),
                candidate("new", PortfolioAdvisorAction.NEU_AUFNEHMEN, score = 78, timing = 0.9, holding = false)
            ),
            budgetEur = 100
        )

        assertEquals(100, plan.allocations.sumOf { it.amountEur } + plan.cashEur)
        assertTrue(plan.allocations.all { it.amountEur >= 0 })
    }

    @Test
    fun allWeakOrUnreliableCandidatesLeaveAllBudgetAsCash() {
        val plan = PortfolioAdvisorEngine.allocate(
            listOf(
                candidate("weak", PortfolioAdvisorAction.NICHT_AUFNEHMEN, score = 60, holding = false),
                candidate("stale", PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG, score = null, reliable = false)
            ),
            100
        )

        assertTrue(plan.allocations.isEmpty())
        assertEquals(100, plan.cashEur)
    }

    @Test
    fun strongestScoreLimitsHowMuchOfBudgetMayBeDeployed() {
        val score78 = PortfolioAdvisorEngine.allocate(
            listOf(candidate("good", PortfolioAdvisorAction.NEU_AUFNEHMEN, score = 78, holding = false)),
            100
        )
        val score73 = PortfolioAdvisorEngine.allocate(
            listOf(candidate("okay", PortfolioAdvisorAction.NEU_AUFNEHMEN, score = 73, holding = false)),
            100
        )

        assertEquals(80, score78.allocations.sumOf { it.amountEur })
        assertEquals(20, score78.cashEur)
        assertEquals(60, score73.allocations.sumOf { it.amountEur })
        assertEquals(40, score73.cashEur)
    }

    @Test
    fun reduceAndSellNeverReceiveNewAllocation() {
        val plan = PortfolioAdvisorEngine.allocate(
            listOf(
                candidate("reduce", PortfolioAdvisorAction.REDUZIEREN, score = 45),
                candidate("sell", PortfolioAdvisorAction.VERKAUFEN, score = 22),
                candidate("new", PortfolioAdvisorAction.NEU_AUFNEHMEN, score = 85, holding = false)
            ),
            100
        )

        assertFalse(plan.allocations.any { it.itemId == "reduce" || it.itemId == "sell" })
        assertTrue(plan.allocations.any { it.itemId == "new" })
    }

    @Test
    fun plannedSavingsReduceExtraPurchaseAndLeaveTheDifferenceAsCash() {
        val withoutSavings = PortfolioAdvisorEngine.allocate(
            listOf(candidate("meta", PortfolioAdvisorAction.NACHKAUFEN, score = 85, savings = 0)),
            100
        )
        val withSavings = PortfolioAdvisorEngine.allocate(
            listOf(candidate("meta", PortfolioAdvisorAction.NACHKAUFEN, score = 85, savings = 20)),
            100
        )

        assertEquals(100, withoutSavings.allocations.single().amountEur)
        assertEquals(80, withSavings.allocations.single().amountEur)
        assertEquals(20, withSavings.cashEur)
    }

    @Test
    fun reduceOrSellWithActiveSavingsPlanCreatesConflictButDoesNotMutateIt() {
        val plan = PortfolioAdvisorEngine.allocate(
            listOf(candidate("meta", PortfolioAdvisorAction.REDUZIEREN, score = 44, savings = 20)),
            100
        )

        assertTrue(plan.allocations.isEmpty())
        assertEquals(1, plan.savingsPlanConflicts.size)
        val conflict = plan.savingsPlanConflicts.single()
        assertEquals("meta", conflict.itemId)
        assertEquals(20, conflict.monthlySavingsEur)
        assertEquals(PortfolioAdvisorAction.REDUZIEREN, conflict.action)
    }

    @Test
    fun borderlineHaltenCanReceiveCapitalOnlyWhenNoStrongerEligibleCandidateExists() {
        val alone = PortfolioAdvisorEngine.allocate(
            listOf(candidate("hold", PortfolioAdvisorAction.HALTEN, score = 70)),
            100
        )
        val withStrong = PortfolioAdvisorEngine.allocate(
            listOf(
                candidate("hold", PortfolioAdvisorAction.HALTEN, score = 70),
                candidate("strong", PortfolioAdvisorAction.NEU_AUFNEHMEN, score = 82, holding = false)
            ),
            100
        )

        assertTrue(alone.allocations.any { it.itemId == "hold" && it.amountEur > 0 })
        assertFalse(withStrong.allocations.any { it.itemId == "hold" })
    }

    @Test
    fun candidateModelHasNoPortfolioWeightInputThatCanChangeStrategicEligibility() {
        val fields = PortfolioAdvisorCandidate::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fields.any { it.contains("weight") || it.contains("concentration") })
    }

    private fun candidate(
        id: String,
        action: PortfolioAdvisorAction,
        score: Int?,
        timing: Double = 1.0,
        holding: Boolean = true,
        savings: Int = 0,
        reliable: Boolean = true
    ) = PortfolioAdvisorCandidate(
        itemId = id,
        isHolding = holding,
        action = action,
        advisor = AdvisorResult(
            instrumentId = id,
            signal = when (action) {
                PortfolioAdvisorAction.NACHKAUFEN, PortfolioAdvisorAction.NEU_AUFNEHMEN -> AdvisorSignal.NACHKAUFEN
                PortfolioAdvisorAction.HALTEN, PortfolioAdvisorAction.NICHT_AUFNEHMEN -> AdvisorSignal.HALTEN
                PortfolioAdvisorAction.REDUZIEREN -> AdvisorSignal.REDUZIEREN
                PortfolioAdvisorAction.VERKAUFEN -> AdvisorSignal.VERKAUFEN
                PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG
            },
            score = score,
            reliable = reliable,
            reasons = listOf("test"),
            risks = emptyList(),
            confidencePct = if (reliable) 80 else 0,
            timingFactor = timing
        ),
        currentValueEur = if (holding) 500.0 else null,
        monthlySavingsEur = savings
    )
}
