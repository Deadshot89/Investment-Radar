package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidityNeedEngineTest {
    private fun holding(
        id: String,
        value: Double,
        action: PortfolioAdvisorAction,
        score: Int,
        shares: Double = value / 10.0
    ) = LiquidityHolding(
        itemId = id,
        instrumentName = id,
        currentValueEur = value,
        shares = shares,
        advisorAction = action,
        advisorScore = score,
        dataReliable = true,
        forecastDirection = null,
        profitLossPct = null
    )

    @Test
    fun availableCashIsUsedBeforeSales() {
        val plan = LiquidityNeedEngine.plan(
            requestedEur = 100.0,
            availableCashEur = 120.0,
            holdings = listOf(holding("weak", 200.0, PortfolioAdvisorAction.VERKAUFEN, 30))
        )
        assertEquals(100.0, plan.cashUsedEur, 0.001)
        assertEquals(0.0, plan.saleNeededEur, 0.001)
        assertTrue(plan.suggestions.isEmpty())
    }

    @Test
    fun sellAndReduceSignalsAreUsedBeforeHoldAndBuySignals() {
        val plan = LiquidityNeedEngine.plan(
            requestedEur = 180.0,
            availableCashEur = 0.0,
            holdings = listOf(
                holding("strong", 300.0, PortfolioAdvisorAction.NACHKAUFEN, 84),
                holding("hold", 300.0, PortfolioAdvisorAction.HALTEN, 70),
                holding("reduce", 100.0, PortfolioAdvisorAction.REDUZIEREN, 55),
                holding("sell", 100.0, PortfolioAdvisorAction.VERKAUFEN, 35)
            )
        )
        assertEquals(listOf("sell", "reduce"), plan.suggestions.map { it.itemId })
        assertEquals(100.0, plan.suggestions[0].amountEur, 0.001)
        assertEquals(80.0, plan.suggestions[1].amountEur, 0.001)
    }

    @Test
    fun lastPositionIsOnlyPartiallySoldForExactNeed() {
        val plan = LiquidityNeedEngine.plan(
            requestedEur = 75.0,
            availableCashEur = 25.0,
            holdings = listOf(holding("sell", 200.0, PortfolioAdvisorAction.VERKAUFEN, 30, shares = 20.0))
        )
        val suggestion = plan.suggestions.single()
        assertEquals(50.0, suggestion.amountEur, 0.001)
        assertEquals(5.0, suggestion.shares!!, 0.001)
        assertEquals(150.0, suggestion.remainingValueEur, 0.001)
        assertTrue(!suggestion.fullExit)
    }

    @Test
    fun uncoveredAmountIsReportedWhenPortfolioIsTooSmall() {
        val plan = LiquidityNeedEngine.plan(
            requestedEur = 500.0,
            availableCashEur = 50.0,
            holdings = listOf(holding("sell", 100.0, PortfolioAdvisorAction.VERKAUFEN, 20))
        )
        assertEquals(350.0, plan.uncoveredEur, 0.001)
        assertEquals(150.0, plan.coveredEur, 0.001)
    }
    @Test
    fun valuedHoldingStillProducesConcreteSaleSuggestionWhenAdvisorDataIsMissing() {
        val unreliableMeta = LiquidityHolding(
            itemId = "META",
            instrumentName = "Meta Platforms",
            currentValueEur = 946.83,
            shares = 1.436,
            advisorAction = PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG,
            advisorScore = 72,
            dataReliable = false,
            forecastDirection = null,
            profitLossPct = 20.5
        )

        val plan = LiquidityNeedEngine.plan(
            requestedEur = 200.0,
            availableCashEur = 0.0,
            holdings = listOf(unreliableMeta)
        )

        assertEquals(listOf("META"), plan.suggestions.map { it.itemId })
        assertEquals(200.0, plan.suggestions.single().amountEur, 0.001)
        assertEquals(0.0, plan.uncoveredEur, 0.001)
        assertTrue(plan.suggestions.single().reason.contains("vor Order prüfen"))
    }

    @Test
    fun reliableSellCandidateIsPreferredBeforeUnreliableLargeHolding() {
        val unreliableMeta = LiquidityHolding(
            itemId = "META",
            instrumentName = "Meta Platforms",
            currentValueEur = 946.83,
            shares = 1.436,
            advisorAction = PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG,
            advisorScore = 72,
            dataReliable = false,
            forecastDirection = null,
            profitLossPct = 20.5
        )
        val reliableSell = holding(
            id = "SELL",
            value = 110.0,
            action = PortfolioAdvisorAction.VERKAUFEN,
            score = 40
        )

        val plan = LiquidityNeedEngine.plan(
            requestedEur = 200.0,
            availableCashEur = 0.0,
            holdings = listOf(unreliableMeta, reliableSell)
        )

        assertEquals(listOf("SELL", "META"), plan.suggestions.map { it.itemId })
        assertEquals(110.0, plan.suggestions[0].amountEur, 0.001)
        assertEquals(90.0, plan.suggestions[1].amountEur, 0.001)
        assertEquals(0.0, plan.uncoveredEur, 0.001)
    }

    @Test
    fun unreliableHoldingStillCountsTowardPortfolioValue() {
        val unreliable = LiquidityHolding(
            itemId = "META",
            instrumentName = "Meta Platforms",
            currentValueEur = 946.83,
            shares = 1.436,
            advisorAction = PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG,
            advisorScore = 72,
            dataReliable = false,
            forecastDirection = null,
            profitLossPct = 20.5
        )

        val plan = LiquidityNeedEngine.plan(200.0, 18.43, listOf(unreliable))

        assertEquals(946.83, plan.portfolioValueEur, 0.001)
        assertEquals(0.0, plan.reliablePortfolioValueEur, 0.001)
        assertEquals(1, plan.suggestions.size)
        assertEquals(181.57, plan.suggestions.single().amountEur, 0.001)
        assertEquals(0.0, plan.uncoveredEur, 0.001)
    }

}
