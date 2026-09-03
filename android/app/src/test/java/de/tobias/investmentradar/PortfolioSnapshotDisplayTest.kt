package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PortfolioSnapshotDisplayTest {
    @Test
    fun snapshotOnlyPositionKeepsValueButMarksCostBasisUnknown() {
        val position = PortfolioPosition(itemId = "meta", snapshotValueEur = 1675.88)
        val result = PortfolioMetrics.calculate(
            items = listOf(testInvestmentItem(id = "meta", priceEur = 500.0)),
            positions = mapOf("meta" to position),
            customItems = emptyList()
        )
        val metric = result.positions.single()

        assertEquals(1675.88, metric.currentValue!!, 0.001)
        assertFalse(metric.costBasisKnown)
        assertNull(metric.totalProfitLoss)
        assertNull(metric.totalProfitLossPct)
    }

    @Test
    fun firstRealPurchaseReplacesImportedSnapshotWithTransactionBasedPosition() {
        val imported = PortfolioPosition(itemId = "meta", snapshotValueEur = 1675.88)
        val next = imported.upsertPurchase(
            PortfolioPurchase(id = "p1", date = "03.09.2026", investedAmount = 500.0, shares = 1.0)
        )

        assertNull(next.snapshotValueEur)
        assertEquals(1.0, next.shares, 0.001)
        assertEquals(500.0, next.investedAmount, 0.001)
    }
}
