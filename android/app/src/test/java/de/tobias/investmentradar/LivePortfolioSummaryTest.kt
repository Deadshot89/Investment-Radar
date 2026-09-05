package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePortfolioSummaryTest {
    @Test
    fun liveSummaryUsesSamePortfolioMetricsAndKeepsAllEightPositions() {
        val values = UserPortfolioSeed.latestSnapshotValues()
        val costs = UserPortfolioSeed.latestSnapshotCostBasis()
        val positions = values.mapValues { (id, value) ->
            PortfolioPosition(itemId = id, snapshotValueEur = value, snapshotCostBasisEur = costs[id])
        }
        val items = values.keys.map { id -> testInvestmentItem(id = id, priceEur = null) }

        val summary = LivePortfolioSummary.build(items, positions, emptyList())

        assertEquals(8, summary.positionCount)
        assertEquals(1491.89, summary.currentValue, 0.001)
        assertEquals(1485.6424, summary.costBasis, 0.001)
        assertEquals(6.2476, summary.profitLoss, 0.001)
        assertEquals(0.4205, summary.profitLossPct, 0.001)
        assertTrue(summary.performanceComplete)
        assertEquals(8, summary.positions.size)
        assertEquals("meta", summary.positions.first().itemId)
    }
}
