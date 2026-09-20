package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePortfolioSummaryTest {
    @Test
    fun liveSummaryMarksPortfolioIncompleteWhenOneHoldingHasNoCurrentValue() {
        val positions = mapOf(
            "a" to PortfolioPosition(
                itemId = "a",
                snapshotValueEur = 100.0,
                snapshotCostBasisEur = 80.0,
                trackedShares = 2.0
            ),
            "b" to PortfolioPosition(
                itemId = "b",
                snapshotValueEur = 50.0,
                snapshotCostBasisEur = 40.0,
                trackedShares = 1.0
            )
        )
        val items = listOf(
            testInvestmentItem(id = "a", priceEur = 60.0),
            testInvestmentItem(id = "b", priceEur = null)
        )

        val summary = LivePortfolioSummary.build(items, positions, emptyList())

        assertEquals(2, summary.positionCount)
        assertEquals(120.0, summary.currentValue, 0.001)
        assertFalse(summary.currentValueComplete)
        assertEquals(1, summary.missingPriceCount)
        assertFalse(summary.performanceComplete)
        assertEquals(1, summary.positions.size)
        assertEquals("a", summary.positions.single().itemId)
        assertTrue(summary.positions.single().currentValue > 0.0)
    }
}
