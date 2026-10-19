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

    private fun item(
        id: String,
        name: String,
        ticker: String,
        tradeRepublicName: String = name,
        type: String = "Aktie"
    ): InvestmentItem = testInvestmentItem(id = id, type = type).copy(
        name = name,
        ticker = ticker,
        tradeRepublicName = tradeRepublicName
    )

    private fun observation(
        name: String,
        value: Double?,
        sourceIndex: Int,
        pnl: Double? = null,
        shares: Double? = null
    ) = TradeRepublicScreenshotObservation(
        name = name,
        valueEur = value,
        profitLossEur = pnl,
        profitLossPct = null,
        shares = shares,
        derivedCostBasisEur = pnl?.let { value?.minus(it) },
        sourceIndex = sourceIndex
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

    @Test fun duplicatesAndInvalidValuesAreRejectedByLegacyReview() {
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

    @Test
    fun equalOverlappingObservationsFromMultipleScreenshotsMergeIntoOneReadyItem() {
        val builtIn = listOf(item("meta", "Meta Platforms", "META"))
        val positions = mapOf("meta" to PortfolioPosition("meta", snapshotValueEur = 95.0))

        val result = ScreenshotDepotReconciliation.reconcileTradeRepublic(
            observations = listOf(
                observation("Meta Platforms", 100.0, sourceIndex = 0, pnl = 10.0),
                observation("Meta Platforms", 100.0, sourceIndex = 1, pnl = 10.0)
            ),
            builtInItems = builtIn,
            customItems = emptyList(),
            existing = positions
        )

        assertEquals(1, result.size)
        assertEquals("meta", result.single().itemId)
        assertEquals(ScreenshotReviewStatus.READY, result.single().status)
        assertEquals(setOf(0, 1), result.single().sourceIndices)
    }

    @Test
    fun overlappingScreenshotsWithDifferentValuesProduceConflictInsteadOfPickingOne() {
        val builtIn = listOf(item("meta", "Meta Platforms", "META"))
        val positions = mapOf("meta" to PortfolioPosition("meta", snapshotValueEur = 95.0))

        val result = ScreenshotDepotReconciliation.reconcileTradeRepublic(
            observations = listOf(
                observation("Meta Platforms", 100.0, sourceIndex = 0),
                observation("Meta Platforms", 110.0, sourceIndex = 1)
            ),
            builtInItems = builtIn,
            customItems = emptyList(),
            existing = positions
        )

        assertEquals(ScreenshotReviewStatus.CONFLICT, result.single().status)
        assertEquals("meta", result.single().itemId)
        assertEquals(setOf(0, 1), result.single().sourceIndices)
    }

    @Test
    fun firstMissingOptionalFieldsMustNotHideConflictsInLaterScreenshots() {
        val result = ScreenshotDepotReconciliation.reconcileTradeRepublic(
            observations = listOf(
                observation("Meta Platforms", 100.0, sourceIndex = 0),
                observation("Meta Platforms", 100.0, sourceIndex = 1, pnl = 10.0, shares = 2.0),
                observation("Meta Platforms", 100.0, sourceIndex = 2, pnl = 20.0, shares = 3.0)
            ),
            builtInItems = listOf(item("meta", "Meta Platforms", "META")),
            customItems = emptyList(),
            existing = mapOf("meta" to PortfolioPosition("meta", snapshotValueEur = 95.0))
        )

        assertEquals(ScreenshotReviewStatus.CONFLICT, result.single().status)
        assertEquals(setOf(0, 1, 2), result.single().sourceIndices)
    }

    @Test
    fun compatibleSparseOptionalFieldsMergeWithoutInventingValues() {
        val result = ScreenshotDepotReconciliation.reconcileTradeRepublic(
            observations = listOf(
                observation("Meta Platforms", 100.0, sourceIndex = 0),
                observation("Meta Platforms", 100.0, sourceIndex = 1, pnl = 10.0, shares = 2.0),
                observation("Meta Platforms", 100.0, sourceIndex = 2, pnl = 10.0, shares = 2.0)
            ),
            builtInItems = listOf(item("meta", "Meta Platforms", "META")),
            customItems = emptyList(),
            existing = mapOf("meta" to PortfolioPosition("meta", snapshotValueEur = 95.0))
        )

        assertEquals(ScreenshotReviewStatus.READY, result.single().status)
        assertEquals(10.0, result.single().observation!!.profitLossEur!!, 0.001)
        assertEquals(2.0, result.single().observation!!.shares!!, 0.001)
    }

    @Test
    fun differentPercentagesMustConflictWhenFirstScreenshotOmitsPercentage() {
        val result = ScreenshotDepotReconciliation.reconcileTradeRepublic(
            observations = listOf(
                observation("Meta Platforms", 100.0, sourceIndex = 0),
                observation("Meta Platforms", 100.0, sourceIndex = 1).copy(profitLossPct = 5.0),
                observation("Meta Platforms", 100.0, sourceIndex = 2).copy(profitLossPct = 7.0)
            ),
            builtInItems = listOf(item("meta", "Meta Platforms", "META")),
            customItems = emptyList(),
            existing = mapOf("meta" to PortfolioPosition("meta", snapshotValueEur = 95.0))
        )

        assertEquals(ScreenshotReviewStatus.CONFLICT, result.single().status)
    }

    @Test
    fun fuzzyCandidateRequiresReviewAndUnknownObservationBecomesNewPosition() {
        val builtIn = listOf(item("meta", "Meta Platforms", "META"))

        val result = ScreenshotDepotReconciliation.reconcileTradeRepublic(
            observations = listOf(
                observation("Meta Platform", 100.0, 0),
                observation("Brand New Mining", 50.0, 1)
            ),
            builtInItems = builtIn,
            customItems = emptyList(),
            existing = emptyMap()
        )

        val fuzzy = result.first { it.observation?.name == "Meta Platform" }
        val unknown = result.first { it.observation?.name == "Brand New Mining" }
        assertEquals(ScreenshotReviewStatus.NEEDS_REVIEW, fuzzy.status)
        assertEquals(listOf("meta"), fuzzy.candidateItemIds)
        assertEquals(ScreenshotReviewStatus.NEW_POSITION, unknown.status)
        assertNull(unknown.itemId)
    }

    @Test
    fun onlyMissingActiveTradeRepublicEligiblePositionsBecomeNotFound() {
        val builtIn = listOf(
            item("meta", "Meta Platforms", "META"),
            item("apple", "Apple", "AAPL"),
            item("fixed-built-in", "Festzins", "FIXED", tradeRepublicName = "", type = "Festzins")
        )
        val custom = listOf(
            CustomInvestment("custom-imported", "NVIDIA", "NVDA", "", "Aktie"),
            CustomInvestment("custom-manual", "Private Holding", "PRIV", "", "Aktie"),
            CustomInvestment("fixed-custom", "Festzins 2027", "FIX27", "", "Festzins")
        )
        val positions = mapOf(
            "meta" to PortfolioPosition("meta", snapshotValueEur = 100.0),
            "apple" to PortfolioPosition("apple", snapshotValueEur = 50.0),
            "fixed-built-in" to PortfolioPosition("fixed-built-in", snapshotValueEur = 1000.0),
            "custom-imported" to PortfolioPosition(
                "custom-imported",
                snapshotValueEur = 70.0,
                snapshotCapturedAt = "2026-10-01T10:00:00Z"
            ),
            "custom-manual" to PortfolioPosition("custom-manual", snapshotValueEur = 60.0),
            "fixed-custom" to PortfolioPosition("fixed-custom", snapshotValueEur = 2000.0)
        )

        val result = ScreenshotDepotReconciliation.reconcileTradeRepublic(
            observations = listOf(observation("Meta Platforms", 101.0, 0)),
            builtInItems = builtIn,
            customItems = custom,
            existing = positions
        )

        val notFoundIds = result
            .filter { it.status == ScreenshotReviewStatus.NOT_FOUND }
            .mapNotNull { it.itemId }
            .toSet()
        assertEquals(setOf("apple", "custom-imported"), notFoundIds)
        assertFalse(result.any { it.itemId == "fixed-built-in" && it.status == ScreenshotReviewStatus.NOT_FOUND })
        assertFalse(result.any { it.itemId == "custom-manual" && it.status == ScreenshotReviewStatus.NOT_FOUND })
        assertFalse(result.any { it.itemId == "fixed-custom" && it.status == ScreenshotReviewStatus.NOT_FOUND })
    }

    @Test
    fun soldMarkedPositionReappearingRequiresExplicitReactivation() {
        val builtIn = listOf(item("meta", "Meta Platforms", "META"))
        val positions = mapOf(
            "meta" to PortfolioPosition(
                itemId = "meta",
                snapshotValueEur = 90.0,
                lifecycleStatus = PortfolioLifecycleStatus.SOLD_MARKED
            )
        )

        val result = ScreenshotDepotReconciliation.reconcileTradeRepublic(
            observations = listOf(observation("Meta Platforms", 100.0, 0)),
            builtInItems = builtIn,
            customItems = emptyList(),
            existing = positions
        )

        assertEquals(1, result.size)
        assertEquals(ScreenshotReviewStatus.SOLD_REAPPEARED, result.single().status)
        assertEquals("meta", result.single().itemId)
    }

    @Test
    fun invalidObservedValueIsNeverReadyOrNew() {
        val result = ScreenshotDepotReconciliation.reconcileTradeRepublic(
            observations = listOf(observation("Meta Platforms", null, 0)),
            builtInItems = listOf(item("meta", "Meta Platforms", "META")),
            customItems = emptyList(),
            existing = mapOf("meta" to PortfolioPosition("meta", snapshotValueEur = 90.0))
        )

        assertEquals(ScreenshotReviewStatus.INVALID, result.first { it.observation != null }.status)
    }
}
