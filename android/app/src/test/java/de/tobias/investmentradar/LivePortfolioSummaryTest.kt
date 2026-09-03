package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePortfolioSummaryTest {
    @Test
    fun liveSummaryUsesSamePortfolioMetricsAndKeepsAllSevenPositions() {
        val values = UserPortfolioSeed.latestSnapshotValues()
        val costs = UserPortfolioSeed.latestSnapshotCostBasis()
        val positions = values.mapValues { (id, value) ->
            PortfolioPosition(itemId = id, snapshotValueEur = value, snapshotCostBasisEur = costs[id])
        }
        val items = values.keys.map { id -> testInvestmentItem(id = id, priceEur = null) }

        val summary = LivePortfolioSummary.build(items, positions, emptyList())

        assertEquals(7, summary.positionCount)
        assertEquals(1986.24, summary.currentValue, 0.001)
        assertEquals(2008.49, summary.costBasis, 0.001)
        assertEquals(-22.25, summary.profitLoss, 0.001)
        assertEquals(-1.1078, summary.profitLossPct, 0.001)
        assertTrue(summary.performanceComplete)
        assertEquals(7, summary.positions.size)
        assertEquals("meta", summary.positions.first().itemId)
    }
}
