package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioSnapshotValueTest {
    @Test
    fun snapshotOnlyHoldingStaysActiveButIsNotACurrentMarketValue() {
        val position = PortfolioPosition(itemId = "meta", snapshotValueEur = 1675.88)

        assertTrue(position.isActiveHolding())
        assertNull(position.currentValue(null))
        assertNull(position.unrealizedProfitLoss(null))
        assertNull(position.totalProfitLoss(null))
    }

    @Test
    fun portfolioAnalysisDoesNotUseHistoricalSnapshotAsCurrentValue() {
        val positions = mapOf("meta" to PortfolioPosition(itemId = "meta", snapshotValueEur = 1675.88))

        val values = PortfolioAnalysis.values(emptyList(), positions, emptyList())

        assertTrue(values.isEmpty())
    }

    @Test
    fun portfolioAnalysisUsesTrackedSharesAtCurrentPrice() {
        val item = InvestmentItem(
            id = "meta",
            type = "Aktie",
            name = "Meta Platforms",
            ticker = "META",
            isin = "US30303M1027",
            tradeRepublicName = "Meta Platforms",
            status = "OK",
            allocation = 0,
            risk = 3,
            price = 150.0,
            priceEur = 150.0,
            currency = "EUR",
            fxRateToEur = 1.0,
            fxSource = "test",
            fxDelayed = false,
            fxAsOf = null,
            percentChange = null,
            marketOpen = true,
            dataSource = "test",
            dataDelayed = false,
            dataError = null
        )
        val position = PortfolioPosition(
            itemId = "meta",
            snapshotValueEur = 1675.88,
            trackedShares = 2.0
        )

        val values = PortfolioAnalysis.values(listOf(item), mapOf("meta" to position), emptyList())

        assertEquals(300.0, values.getValue("meta"), 0.001)
    }

    @Test
    fun snapshotOnlyHoldingsMakePortfolioValueIncompleteInsteadOfFabricatingTotal() {
        val positions = mapOf(
            "meta" to PortfolioPosition(itemId = "meta", snapshotValueEur = 1675.88),
            "msft" to PortfolioPosition(itemId = "msft", snapshotValueEur = 31.38)
        )

        val summary = PortfolioMetrics.calculate(emptyList(), positions, emptyList())

        assertEquals(2, summary.heldPositionCount)
        assertEquals(2, summary.missingPriceCount)
        assertEquals(0.0, summary.calculableCurrentValue, 0.001)
        assertTrue(!summary.currentValueComplete)
        assertNull(summary.largestPositionId)
        assertNull(summary.totalProfitLoss)
    }
}
