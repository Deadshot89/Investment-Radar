package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPlanEngineTest {
    @Test
    fun savingsCommitmentAndExtraBuyNeverExceedMonthlyBudget() {
        val candidate = candidate(
            itemId = "meta",
            action = PortfolioAdvisorAction.NACHKAUFEN,
            monthlySavingsEur = 40,
            score = 85
        )
        val advisorPlan = plan(
            budgetEur = 100,
            allocations = listOf(
                PortfolioAllocation("meta", 100, PortfolioAdvisorAction.NACHKAUFEN, "Starker Nachkauf")
            ),
            candidates = listOf(candidate)
        )

        val actionPlan = ActionPlanEngine.build(
            analysisDay = "2026-09-07",
            advisorPlan = advisorPlan,
            currentPricesEur = mapOf("meta" to 20.0)
        )

        val keepSavings = actionPlan.actions.single { it.type == ActionType.KEEP_SAVINGS_PLAN }
        val buy = actionPlan.actions.single { it.type == ActionType.BUY_MORE }
        assertEquals(40.0, keepSavings.amountEur, 0.001)
        assertEquals(60.0, buy.amountEur, 0.001)
        assertEquals(3.0, buy.plannedShares!!, 0.001)
        assertEquals(0.0, actionPlan.cashEur, 0.001)

        val monthlyUse = actionPlan.actions
            .filter { it.type == ActionType.KEEP_SAVINGS_PLAN || (it.type in setOf(ActionType.BUY_MORE, ActionType.OPEN_POSITION) && it.fromInstrumentId == null) }
            .sumOf { it.amountEur } + actionPlan.cashEur
        assertTrue(monthlyUse <= actionPlan.availableBudgetEur + 0.001)
    }

    @Test
    fun sellOrReduceWithSavingsPlanCreatesReviewInsteadOfKeepAction() {
        val weak = candidate(
            itemId = "weak",
            action = PortfolioAdvisorAction.REDUZIEREN,
            monthlySavingsEur = 30,
            score = 42
        )
        val advisorPlan = plan(
            budgetEur = 100,
            savingsPlanConflicts = listOf(
                SavingsPlanConflict("weak", 30, PortfolioAdvisorAction.REDUZIEREN)
            ),
            candidates = listOf(weak)
        )

        val actionPlan = ActionPlanEngine.build("2026-09-07", advisorPlan)

        val review = actionPlan.actions.single { it.type == ActionType.REVIEW_SAVINGS_PLAN }
        assertEquals("weak", review.instrumentId)
        assertEquals(30.0, review.amountEur, 0.001)
        assertTrue(actionPlan.actions.none { it.type == ActionType.KEEP_SAVINGS_PLAN && it.instrumentId == "weak" })
    }

    @Test
    fun reallocationProducesCompleteSourceAndTargetActions() {
        val source = candidate("weak", PortfolioAdvisorAction.REDUZIEREN, monthlySavingsEur = 0, score = 40, isHolding = true)
        val target = candidate("strong", PortfolioAdvisorAction.NEU_AUFNEHMEN, monthlySavingsEur = 0, score = 86, isHolding = false)
        val advisorPlan = plan(
            budgetEur = 0,
            reallocations = listOf(
                ReallocationSuggestion("weak", "strong", 100, "Deutlich bessere Chance")
            ),
            candidates = listOf(source, target)
        )

        val actionPlan = ActionPlanEngine.build(
            analysisDay = "2026-09-07",
            advisorPlan = advisorPlan,
            currentPricesEur = mapOf("weak" to 10.0, "strong" to 20.0)
        )

        val reduce = actionPlan.actions.single { it.type == ActionType.REDUCE }
        val open = actionPlan.actions.single { it.type == ActionType.OPEN_POSITION }
        assertEquals(100.0, reduce.amountEur, 0.001)
        assertEquals(10.0, reduce.plannedShares!!, 0.001)
        assertEquals("weak", reduce.fromInstrumentId)
        assertEquals("strong", reduce.toInstrumentId)
        assertEquals(100.0, open.amountEur, 0.001)
        assertEquals(5.0, open.plannedShares!!, 0.001)
        assertEquals("weak", open.fromInstrumentId)
        assertEquals("strong", open.toInstrumentId)
        assertEquals(reduce.amountEur, open.amountEur, 0.001)
    }

    @Test
    fun missingReliablePriceKeepsEuroActionButDoesNotInventShares() {
        val strong = candidate("strong", PortfolioAdvisorAction.NEU_AUFNEHMEN, score = 84, isHolding = false)
        val advisorPlan = plan(
            budgetEur = 80,
            allocations = listOf(
                PortfolioAllocation("strong", 80, PortfolioAdvisorAction.NEU_AUFNEHMEN, "Neue Chance")
            ),
            candidates = listOf(strong)
        )

        val actionPlan = ActionPlanEngine.build(
            analysisDay = "2026-09-07",
            advisorPlan = advisorPlan,
            currentPricesEur = mapOf("strong" to null)
        )

        val action = actionPlan.actions.single { it.type == ActionType.OPEN_POSITION }
        assertEquals(80.0, action.amountEur, 0.001)
        assertNull(action.plannedShares)
    }

    @Test
    fun weakPlanKeepsUnusedBudgetAsExplicitCashAction() {
        val advisorPlan = plan(
            budgetEur = 100,
            cashEur = 100,
            candidates = listOf(candidate("hold", PortfolioAdvisorAction.HALTEN, score = 60))
        )

        val actionPlan = ActionPlanEngine.build("2026-09-07", advisorPlan)

        assertEquals(100.0, actionPlan.cashEur, 0.001)
        val cash = actionPlan.actions.single { it.type == ActionType.HOLD_CASH }
        assertEquals(100.0, cash.amountEur, 0.001)
        assertEquals("cash", cash.instrumentId)
    }

    @Test
    fun eventFingerprintsAndCriticalPriorityStayAttachedToAffectedAction() {
        val criticalSource = candidate("weak", PortfolioAdvisorAction.VERKAUFEN, score = 20)
        val normalSource = candidate("other", PortfolioAdvisorAction.REDUZIEREN, score = 40)
        val target = candidate("strong", PortfolioAdvisorAction.NEU_AUFNEHMEN, score = 90, isHolding = false)
        val advisorPlan = plan(
            budgetEur = 0,
            reallocations = listOf(
                ReallocationSuggestion("weak", "strong", 50, "Kritische Verschlechterung"),
                ReallocationSuggestion("other", "strong", 50, "Normale Umschichtung")
            ),
            candidates = listOf(criticalSource, normalSource, target)
        )

        val actionPlan = ActionPlanEngine.build(
            analysisDay = "2026-09-07",
            advisorPlan = advisorPlan,
            eventFingerprintsByInstrument = mapOf("weak" to listOf("weak|profit_warning|1")),
            criticalEventInstrumentIds = setOf("weak")
        )

        val critical = actionPlan.actions.single { it.instrumentId == "weak" && it.type == ActionType.SELL }
        val normal = actionPlan.actions.single { it.instrumentId == "other" && it.type == ActionType.REDUCE }
        assertEquals(listOf("weak|profit_warning|1"), critical.eventFingerprints)
        assertTrue(critical.priority > normal.priority)
    }

    @Test
    fun identicalInputsProduceStablePlanAndActionIds() {
        val strong = candidate("strong", PortfolioAdvisorAction.NACHKAUFEN, score = 84)
        val advisorPlan = plan(
            budgetEur = 50,
            allocations = listOf(
                PortfolioAllocation("strong", 50, PortfolioAdvisorAction.NACHKAUFEN, "Nachkauf")
            ),
            candidates = listOf(strong)
        )

        val first = ActionPlanEngine.build("2026-09-07", advisorPlan, currentPricesEur = mapOf("strong" to 25.0))
        val second = ActionPlanEngine.build("2026-09-07", advisorPlan, currentPricesEur = mapOf("strong" to 25.0))

        assertEquals(first.planId, second.planId)
        assertEquals(first.actions.map { it.actionId }, second.actions.map { it.actionId })
        assertNotNull(first.planId.takeIf(String::isNotBlank))
    }

    private fun plan(
        budgetEur: Int,
        allocations: List<PortfolioAllocation> = emptyList(),
        cashEur: Int = budgetEur,
        reallocations: List<ReallocationSuggestion> = emptyList(),
        savingsPlanConflicts: List<SavingsPlanConflict> = emptyList(),
        candidates: List<PortfolioAdvisorCandidate> = emptyList()
    ) = PortfolioAdvisorPlan(
        budgetEur = budgetEur,
        allocations = allocations,
        cashEur = cashEur,
        reallocations = reallocations,
        savingsPlanConflicts = savingsPlanConflicts,
        candidates = candidates
    )

    private fun candidate(
        itemId: String,
        action: PortfolioAdvisorAction,
        monthlySavingsEur: Int = 0,
        score: Int = 60,
        isHolding: Boolean = true
    ) = PortfolioAdvisorCandidate(
        itemId = itemId,
        isHolding = isHolding,
        action = action,
        advisor = AdvisorResult(
            instrumentId = itemId,
            signal = when (action) {
                PortfolioAdvisorAction.NACHKAUFEN, PortfolioAdvisorAction.NEU_AUFNEHMEN -> AdvisorSignal.NACHKAUFEN
                PortfolioAdvisorAction.REDUZIEREN -> AdvisorSignal.REDUZIEREN
                PortfolioAdvisorAction.VERKAUFEN -> AdvisorSignal.VERKAUFEN
                PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG
                else -> AdvisorSignal.HALTEN
            },
            score = score,
            reliable = action != PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG,
            reasons = listOf("Testgrund"),
            risks = emptyList(),
            confidencePct = 80,
            timingFactor = 1.0
        ),
        currentValueEur = 500.0,
        monthlySavingsEur = monthlySavingsEur
    )
}
