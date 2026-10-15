package de.tobias.investmentradar

import org.junit.Assert.*
import org.junit.Test

class ScreenshotDepotReconciliationTest {
    private val existing = mapOf(
        "meta" to PortfolioPosition(
            itemId = "meta",
            snapshotValueEur = 100.0,
            purchases = listOf(PortfolioPurchase("buy-1", "2026-10-01", 90.0, 1.0)),
            sales = listOf(PortfolioSale("sell-1", "2026-10-02", 10.0, 0.1))
        ),
        "apple" to PortfolioPosition(itemId = "apple", snapshotValueEur = 50.0)
    )

    @Test fun confirmedSnapshotChangesOnlyValuation() {
        val items = ScreenshotDepotReconciliation.review(
            listOf(ScreenshotHoldingObservation("meta", 120.0, 100.0, 3.0, true)), existing
        )
        val updated = ScreenshotDepotReconciliation.previewConfirmed(items, existing)
        assertEquals(120.0, updated["meta"]!!.snapshotValueEur!!, 0.001)
        assertEquals(existing["meta"]!!.purchases, updated["meta"]!!.purchases)
        assertEquals(existing["meta"]!!.sales, updated["meta"]!!.sales)
        assertEquals(existing["meta"]!!.trackedShares, updated["meta"]!!.trackedShares)
        assertEquals(existing["apple"], updated["apple"])
    }

    @Test fun unconfirmedAndUnknownAreNeverApplied() {
        val items = ScreenshotDepotReconciliation.review(
            listOf(
                ScreenshotHoldingObservation("meta", 200.0),
                ScreenshotHoldingObservation("unknown", 40.0, confirmedByUser = true)
            ), existing
        )
        assertTrue(items.all { it.status == ScreenshotReviewStatus.NEEDS_REVIEW })
        assertEquals(existing, ScreenshotDepotReconciliation.previewConfirmed(items, existing))
    }

    @Test fun duplicatesAndInvalidValuesAreRejected() {
        val items = ScreenshotDepotReconciliation.review(
            listOf(
                ScreenshotHoldingObservation("meta", 120.0, confirmedByUser = true),
                ScreenshotHoldingObservation("meta", 130.0, confirmedByUser = true),
                ScreenshotHoldingObservation("apple", Double.NaN, confirmedByUser = true)
            ), existing
        )
        assertTrue(items.all { it.status == ScreenshotReviewStatus.INVALID })
        assertEquals(existing, ScreenshotDepotReconciliation.previewConfirmed(items, existing))
    }
}
