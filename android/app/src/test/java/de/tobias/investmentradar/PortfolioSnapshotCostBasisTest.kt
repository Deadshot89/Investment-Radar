package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun importedSnapshotWithTrackedSharesAllowsPartialSale() {
        val position = PortfolioPosition(
            itemId = "meta",
            snapshotValueEur = 1709.74,
            snapshotCostBasisEur = 1716.25,
            trackedShares = 3.245937
        )

        val updated = position.upsertSale(
            PortfolioSale(
                id = "sale-2026-09-04",
                date = "04.09.2026",
                proceeds = 500.0,
                shares = 0.954016
            )
        )

        assertNotNull(updated)
        updated!!
        assertTrue(updated.isLedgerValid())
        assertEquals(2.291921, updated.shares, 0.000001)
        assertEquals(0.954016, updated.sales.single().shares, 0.000001)
        assertEquals(500.0, updated.sales.single().proceeds, 0.001)
    }
}
