package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PortfolioImportedPurchaseTest {
    @Test
    fun addingPurchaseToImportedPositionPreservesOpeningSharesAndCostBasis() {
        val imported = PortfolioPosition(
            itemId = "meta",
            snapshotValueEur = 1213.11,
            snapshotCostBasisEur = 2.291921 * 519.04,
            trackedShares = 2.291921
        )

        val updated = imported.upsertPurchaseIfValid(
            PortfolioPurchase(
                id = "savings-plan-plan-meta@2026-09-16",
                date = "2026-09-16",
                investedAmount = 10.0,
                shares = 0.02
            )
        )

        assertNotNull(updated)
        updated!!
        assertEquals(2.311921, updated.shares, 0.000000001)
        assertEquals((2.291921 * 519.04) + 10.0, updated.investedAmount, 0.000001)
        assertEquals(2, updated.purchases.size)
        assertEquals("imported-opening-meta", updated.purchases.first().id)
        assertEquals("savings-plan-plan-meta@2026-09-16", updated.purchases.last().id)
    }
}
