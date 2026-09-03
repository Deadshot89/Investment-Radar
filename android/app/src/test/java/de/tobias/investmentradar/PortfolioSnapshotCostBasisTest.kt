package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioSnapshotCostBasisTest {
    @Test
    fun importedSnapshotWithCostBasisCalculatesCompletePerformance() {
        val position = PortfolioPosition(
            itemId = "meta",
            snapshotValueEur = 1714.83,
            snapshotCostBasisEur = 1716.25
        )

        val result = PortfolioMetrics.calculate(
            items = listOf(testInvestmentItem(id = "meta", priceEur = null)),
            positions = mapOf("meta" to position),
            customItems = emptyList()
        )

        assertTrue(result.currentValueComplete)
        assertEquals(1716.25, result.investedCostBasis, 0.001)
        assertEquals(-1.42, result.totalProfitLoss!!, 0.001)
        assertEquals(-1.42 / 1716.25 * 100.0, result.totalProfitLossPct!!, 0.001)
        assertTrue(result.positions.single().costBasisKnown)
    }
}
