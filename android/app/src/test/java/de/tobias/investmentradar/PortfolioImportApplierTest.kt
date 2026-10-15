package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioImportApplierTest {
    private val meta = PortfolioPosition(
        itemId = "meta",
        snapshotValueEur = 100.0,
        snapshotCostBasisEur = 90.0,
        trackedShares = 1.5,
        purchases = listOf(PortfolioPurchase("buy-1", "2026-09-01", 120.0, 2.0)),
        sales = listOf(PortfolioSale("sale-1", "2026-09-20", 40.0, 0.5)),
        snapshotCapturedAt = "2026-10-01T10:00:00Z"
    )
    private val apple = PortfolioPosition(
        itemId = "apple",
        snapshotValueEur = 50.0,
        snapshotCostBasisEur = 45.0,
        trackedShares = 0.5
    )
    private val existing = mapOf("meta" to meta, "apple" to apple)

    @Test
    fun updateSnapshotChangesConfirmedSnapshotFieldsButPreservesLedger() {
        val result = PortfolioImportApplier.preview(
            existing = existing,
            changes = listOf(
                PortfolioImportChange.UpdateSnapshot(
                    itemId = "meta",
                    valueEur = 140.0,
                    costBasisEur = 110.0,
                    trackedShares = 1.75
                )
            ),
            capturedAt = "2026-10-15T12:00:00Z"
        )

        val updated = result.positions.getValue("meta")
        assertEquals(140.0, updated.snapshotValueEur!!, 0.001)
        assertEquals(110.0, updated.snapshotCostBasisEur!!, 0.001)
        assertEquals(1.75, updated.trackedShares!!, 0.001)
        assertEquals("2026-10-15T12:00:00Z", updated.snapshotCapturedAt)
        assertEquals(meta.purchases, updated.purchases)
        assertEquals(meta.sales, updated.sales)
        assertEquals(apple, result.positions.getValue("apple"))
        assertEquals(1, result.summary.updatedCount)
    }

    @Test
    fun missingOptionalSnapshotFieldsDoNotEraseExistingKnownData() {
        val result = PortfolioImportApplier.preview(
            existing = existing,
            changes = listOf(PortfolioImportChange.UpdateSnapshot("meta", valueEur = 141.0)),
            capturedAt = "2026-10-15T12:01:00Z"
        )

        val updated = result.positions.getValue("meta")
        assertEquals(meta.snapshotCostBasisEur, updated.snapshotCostBasisEur)
        assertEquals(meta.trackedShares, updated.trackedShares)
    }

    @Test
    fun addPositionCreatesSnapshotWithoutFabricatedTransactions() {
        val result = PortfolioImportApplier.preview(
            existing = existing,
            changes = listOf(
                PortfolioImportChange.AddPosition(
                    itemId = "msft",
                    valueEur = 75.0,
                    costBasisEur = 70.0,
                    trackedShares = null
                )
            ),
            capturedAt = "2026-10-15T12:02:00Z"
        )

        val added = result.positions.getValue("msft")
        assertTrue(added.purchases.isEmpty())
        assertTrue(added.sales.isEmpty())
        assertEquals(PortfolioLifecycleStatus.ACTIVE, added.lifecycleStatus)
        assertEquals(75.0, added.snapshotValueEur!!, 0.001)
        assertEquals("2026-10-15T12:02:00Z", added.snapshotCapturedAt)
        assertEquals(1, result.summary.addedCount)
    }

    @Test
    fun keepMissingPositionIsANoOp() {
        val result = PortfolioImportApplier.preview(
            existing = existing,
            changes = listOf(
                PortfolioImportChange.MissingPosition("apple", MissingPositionDecision.KEEP)
            ),
            capturedAt = "2026-10-15T12:03:00Z"
        )

        assertEquals(existing, result.positions)
        assertEquals(1, result.summary.keptCount)
    }

    @Test
    fun markSoldPreservesHistoryAndCreatesNoSale() {
        val result = PortfolioImportApplier.preview(
            existing = existing,
            changes = listOf(
                PortfolioImportChange.MissingPosition("meta", MissingPositionDecision.MARK_SOLD)
            ),
            capturedAt = "2026-10-15T12:04:00Z"
        )

        val sold = result.positions.getValue("meta")
        assertEquals(PortfolioLifecycleStatus.SOLD_MARKED, sold.lifecycleStatus)
        assertEquals(meta.purchases, sold.purchases)
        assertEquals(meta.sales, sold.sales)
        assertEquals(meta.snapshotValueEur, sold.snapshotValueEur)
        assertFalse(sold.isActiveHolding())
        assertEquals(1, result.summary.soldCount)
    }

    @Test
    fun deleteRemovesOnlyRequestedPosition() {
        val result = PortfolioImportApplier.preview(
            existing = existing,
            changes = listOf(
                PortfolioImportChange.MissingPosition("apple", MissingPositionDecision.DELETE)
            ),
            capturedAt = "2026-10-15T12:05:00Z"
        )

        assertEquals(setOf("meta"), result.positions.keys)
        assertEquals(meta, result.positions.getValue("meta"))
        assertEquals(1, result.summary.deletedCount)
    }

    @Test
    fun soldMarkedPositionRequiresExplicitReactivateCommand() {
        val soldExisting = existing + ("meta" to meta.copy(lifecycleStatus = PortfolioLifecycleStatus.SOLD_MARKED))

        val stillSold = PortfolioImportApplier.preview(
            existing = soldExisting,
            changes = emptyList(),
            capturedAt = "2026-10-15T12:06:00Z"
        )
        assertEquals(PortfolioLifecycleStatus.SOLD_MARKED, stillSold.positions.getValue("meta").lifecycleStatus)

        val reactivated = PortfolioImportApplier.preview(
            existing = soldExisting,
            changes = listOf(
                PortfolioImportChange.Reactivate(
                    itemId = "meta",
                    valueEur = 150.0,
                    costBasisEur = null,
                    trackedShares = null
                )
            ),
            capturedAt = "2026-10-15T12:07:00Z"
        )
        val position = reactivated.positions.getValue("meta")
        assertEquals(PortfolioLifecycleStatus.ACTIVE, position.lifecycleStatus)
        assertEquals(150.0, position.snapshotValueEur!!, 0.001)
        assertEquals(meta.purchases, position.purchases)
        assertEquals(meta.sales, position.sales)
        assertEquals(1, reactivated.summary.reactivatedCount)
    }
}
