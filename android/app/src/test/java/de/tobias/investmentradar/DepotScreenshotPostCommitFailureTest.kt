package de.tobias.investmentradar

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepotScreenshotPostCommitFailureTest {
    @Test
    fun successfulPersistenceRemainsCompletedWhenPostCommitRefreshThrows() = runBlocking {
        val image = ScreenshotImageRef("one", "content://screenshots/one")
        val current = PortfolioPosition(
            itemId = "meta",
            snapshotValueEur = 100.0,
            snapshotCostBasisEur = 80.0,
            snapshotCapturedAt = "2026-10-14T10:00:00Z"
        )
        var persisted: PortfolioImportResult? = null
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
                                        name = "Meta Platforms",
                                        valueEur = 110.0,
                                        profitLossEur = null,
                                        profitLossPct = null,
                                        shares = null,
                                        derivedCostBasisEur = null,
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
                    positions = mapOf("meta" to current),
                    builtInItems = listOf(
                        testInvestmentItem(id = "meta").copy(
                            type = "Aktie",
                            name = "Meta Platforms",
                            ticker = "META",
                            tradeRepublicName = "Meta Platforms"
                        )
                    ),
                    customItems = emptyList()
                )
            },
            persistImport = { result, _ ->
                persisted = result
                true
            },
            capturedAtProvider = { "2026-10-15T10:00:00Z" },
            importIdProvider = { "post-commit-test" },
            onCommitted = { error("refresh failed") }
        )

        viewModel.selectImages(listOf(image))
        viewModel.analyze()
        assertTrue((viewModel.state.value as DepotScreenshotImportState.Reviewing).canApply)

        viewModel.apply()

        assertTrue(viewModel.state.value is DepotScreenshotImportState.Completed)
        assertEquals(110.0, persisted!!.positions.getValue("meta").snapshotValueEur!!, 0.000001)
    }
}
