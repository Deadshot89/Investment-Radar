package de.tobias.investmentradar

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepotScreenshotAmbiguousMatchTest {
    @Test
    fun assigningOneAmbiguousCandidateCreatesMissingDecisionForOtherActiveHolding() = runBlocking {
        val image = ScreenshotImageRef("one", "content://screenshots/one")
        val items = listOf(
            item("alpha-a", "Alpha Technologies", "ALPA"),
            item("alpha-b", "Alpha Technology", "ALPB")
        )
        val positions = mapOf(
            "alpha-a" to position("alpha-a", 100.0),
            "alpha-b" to position("alpha-b", 90.0)
        )
        val viewModel = DepotScreenshotImportViewModel(
            batchLoader = ScreenshotImportBatchLoader { _, onProgress ->
                onProgress(1, 1)
                ScreenshotImportBatch(
                    listOf(
                        ScreenshotImportImageResult(
                            image = image,
                            parseResult = TradeRepublicParseResult(
                                recognizedAsTradeRepublic = true,
                                observations = listOf(
                                    TradeRepublicScreenshotObservation(
                                        name = "Alpha Technolog",
                                        valueEur = 105.0,
                                        sourceIndex = 0
                                    )
                                )
                            )
                        )
                    )
                )
            },
            sourceProvider = {
                DepotImportSourceSnapshot(
                    positions = positions,
                    builtInItems = items,
                    customItems = emptyList()
                )
            },
            persistImport = { _, _ -> true },
            capturedAtProvider = { "2026-10-15T10:00:00Z" },
            importIdProvider = { "ambiguous-test" }
        )

        viewModel.selectImages(listOf(image))
        viewModel.analyze()

        val ambiguous = (viewModel.state.value as DepotScreenshotImportState.Reviewing)
            .rows
            .single { it.observation != null }
        assertEquals(setOf("alpha-a", "alpha-b"), ambiguous.candidateItemIds.toSet())

        viewModel.assignMatch(ambiguous.key, "alpha-a")

        val reviewing = viewModel.state.value as DepotScreenshotImportState.Reviewing
        assertTrue(reviewing.rows.any {
            it.itemId == "alpha-b" &&
                it.kind == DepotScreenshotImportRowKind.MISSING &&
                it.status == ScreenshotReviewStatus.NOT_FOUND &&
                !it.resolved
        })
        assertFalse(reviewing.rows.any {
            it.itemId == "alpha-a" && it.kind == DepotScreenshotImportRowKind.MISSING
        })
        assertFalse(reviewing.canApply)
    }

    private fun position(id: String, value: Double) = PortfolioPosition(
        itemId = id,
        snapshotValueEur = value,
        snapshotCostBasisEur = value * 0.8,
        snapshotCapturedAt = "2026-10-14T10:00:00Z"
    )

    private fun item(id: String, name: String, ticker: String) = testInvestmentItem(id = id).copy(
        type = "Aktie",
        name = name,
        ticker = ticker,
        tradeRepublicName = name
    )
}
