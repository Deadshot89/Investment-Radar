package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepotActionCenterMapperTest {
    @Test
    fun actionsFollowDepotWidePriorityOrder() {
        val plan = ActionPlan(
            planId = "plan",
            analysisDay = "2026-09-08",
            availableBudgetEur = 100.0,
            cashEur = 10.0,
            actions = listOf(
                action("cash", ActionType.HOLD_CASH, 10.0),
                action("keep", ActionType.KEEP_SAVINGS_PLAN, 20.0),
                action("open", ActionType.OPEN_POSITION, 30.0),
                action("buy", ActionType.BUY_MORE, 40.0),
                action("review", ActionType.REVIEW_SAVINGS_PLAN, 25.0),
                action("reduce", ActionType.REDUCE, 50.0),
                action("sell", ActionType.SELL, 60.0)
            )
        )

        val state = DepotActionCenterMapper.build(plan, emptyMap(), emptyMap(), emptyMap())

        assertEquals(
            listOf(
                ActionType.SELL,
                ActionType.REDUCE,
                ActionType.REVIEW_SAVINGS_PLAN,
                ActionType.BUY_MORE,
                ActionType.OPEN_POSITION,
                ActionType.KEEP_SAVINGS_PLAN,
                ActionType.HOLD_CASH
            ),
            state.items.map { it.type }
        )
    }

    @Test
    fun holdingComesFirstInsideSameActionType() {
        val plan = ActionPlan(
            "plan",
            "2026-09-08",
            100.0,
            0.0,
            listOf(
                action("external", ActionType.BUY_MORE, 40.0, priority = 50),
                action("held", ActionType.BUY_MORE, 40.0, priority = 50)
            )
        )
        val positions = mapOf("held" to PortfolioPosition("held"))

        val state = DepotActionCenterMapper.build(plan, emptyMap(), emptyMap(), positions)

        assertEquals(listOf("held", "external"), state.items.map { it.instrumentId })
    }

    @Test
    fun unreliableBuyIsVisibleButBlockedWithoutExecutableAmount() {
        val plan = ActionPlan("plan", "2026-09-08", 100.0, 0.0, listOf(action("weak", ActionType.OPEN_POSITION, 75.0)))
        val candidate = candidate("weak", reliable = false, coverage = 35)

        val state = DepotActionCenterMapper.build(plan, mapOf("weak" to candidate), emptyMap(), emptyMap())
        val item = state.items.single()

        assertTrue(item.buyBlocked)
        assertFalse(item.executable)
        assertEquals(null, item.displayAmountEur)
        assertEquals("Nicht kaufen – Datenbasis unvollständig", item.actionText)
    }

    @Test
    fun reliableAmountComesUnchangedFromActionPlan() {
        val plan = ActionPlan("plan", "2026-09-08", 100.0, 0.0, listOf(action("buy", ActionType.BUY_MORE, 37.0)))
        val candidate = candidate("buy", reliable = true, coverage = 90)

        val item = DepotActionCenterMapper.build(plan, mapOf("buy" to candidate), emptyMap(), emptyMap()).items.single()

        assertEquals(37.0, item.displayAmountEur!!, 0.001)
        assertEquals("Position um ca. 37 € erhöhen", item.actionText)
        assertTrue(item.executable)
    }

    @Test
    fun summaryUsesPlanAmountsWithoutRecalculatingAllocations() {
        val plan = ActionPlan(
            "plan",
            "2026-09-08",
            100.0,
            15.0,
            listOf(
                action("a", ActionType.BUY_MORE, 35.0),
                action("b", ActionType.OPEN_POSITION, 50.0),
                action("cash", ActionType.HOLD_CASH, 15.0)
            )
        )
        val candidates = mapOf(
            "a" to candidate("a", true, 90),
            "b" to candidate("b", true, 90)
        )

        val state = DepotActionCenterMapper.build(plan, candidates, emptyMap(), emptyMap())

        assertEquals(85.0, state.summary.plannedBuyEur, 0.001)
        assertEquals(15.0, state.summary.cashEur, 0.001)
        assertEquals(0, state.summary.urgentActions)
    }

    @Test
    fun emptyPlanReturnsExplicitEmptyState() {
        val state = DepotActionCenterMapper.build(ActionPlan("", "", 0.0, 0.0, emptyList()), emptyMap(), emptyMap(), emptyMap())

        assertTrue(state.items.isEmpty())
        assertTrue(state.isEmpty)
    }

    private fun action(
        id: String,
        type: ActionType,
        amount: Double,
        priority: Int = 0
    ) = ActionPlanAction(
        actionId = id,
        type = type,
        instrumentId = id,
        amountEur = amount,
        reason = "Testgrund",
        priority = priority
    )

    private fun candidate(itemId: String, reliable: Boolean, coverage: Int) = PortfolioAdvisorCandidate(
        itemId = itemId,
        isHolding = false,
        action = if (reliable) PortfolioAdvisorAction.NACHKAUFEN else PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG,
        advisor = AdvisorResult(
            instrumentId = itemId,
            signal = if (reliable) AdvisorSignal.NACHKAUFEN else AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG,
            score = if (reliable) 82 else 40,
            reliable = reliable,
            reasons = listOf("Testgrund"),
            risks = emptyList(),
            confidencePct = if (reliable) 85 else 30,
            timingFactor = 1.0
        ),
        currentValueEur = 0.0,
        monthlySavingsEur = 0,
        riskScore = 2,
        coveragePct = coverage,
        forecastDirection = "AUFWÄRTS"
    )
}
