package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioPositionLifecycleTest {
    @Test
    fun markSoldPreservesPurchasesSalesSnapshotAndDoesNotCreateProceeds() {
        val original = PortfolioPosition(
            itemId = "meta",
            snapshotValueEur = 250.0,
            snapshotCostBasisEur = 210.0,
            trackedShares = 2.0,
            purchases = listOf(PortfolioPurchase("buy-meta", "2026-09-01", 100.0, 1.0)),
            sales = listOf(PortfolioSale("sell-meta", "2026-09-05", 40.0, 0.2))
        )
        val marked = original.markSoldWithoutTransaction()!!

        assertEquals(PortfolioLifecycleStatus.SOLD_MARKED, marked.lifecycleStatus)
        assertEquals(original.purchases, marked.purchases)
        assertEquals(original.sales, marked.sales)
        assertEquals(original.totalSaleProceeds, marked.totalSaleProceeds, 0.00001)
        assertEquals(original.snapshotValueEur, marked.snapshotValueEur)
        assertEquals(original.snapshotCostBasisEur, marked.snapshotCostBasisEur)
        assertEquals(original.trackedShares, marked.trackedShares)
        assertFalse(marked.isActiveHolding())
        assertNull(marked.currentValue(125.0))
    }

    @Test
    fun reactivationRestoresOnlyStatusWithoutInventingPurchases() {
        val original = PortfolioPosition(
            itemId = "meta",
            snapshotValueEur = 250.0,
            trackedShares = 2.0
        )
        val marked = original.markSoldWithoutTransaction()!!
        val restored = marked.reactivateWithoutTransaction()!!

        assertEquals(original, restored)
        assertTrue(restored.isActiveHolding())
        assertTrue(restored.purchases.isEmpty())
        assertTrue(restored.sales.isEmpty())
        assertEquals(0.0, restored.totalSaleProceeds, 0.00001)
    }

    @Test
    fun invalidLifecycleTransitionsDoNotChangePositions() {
        val empty = PortfolioPosition("meta")
        assertNull(empty.markSoldWithoutTransaction())
        assertNull(empty.reactivateWithoutTransaction())
        val sold = PortfolioPosition(
            itemId = "meta",
            snapshotValueEur = 20.0,
            lifecycleStatus = PortfolioLifecycleStatus.SOLD_MARKED
        )
        assertNull(sold.markSoldWithoutTransaction())
    }
}
