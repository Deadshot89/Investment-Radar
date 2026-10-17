package de.tobias.investmentradar

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DepotScreenshotCustomIdCollisionTest {
    @Test
    fun customNewPositionCannotReuseBuiltInInvestmentId() = runBlocking {
        val image = ScreenshotImageRef("one", "content://screenshots/one")
        val builtIn = testInvestmentItem(id = "meta").copy(
            type = "Aktie",
            name = "Meta Platforms",
            ticker = "META",
            tradeRepublicName = "Meta Platforms"
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
                                        name = "Unbekannte AG",
                                        valueEur = 75.0,
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
                    positions = emptyMap(),
                    builtInItems = listOf(builtIn),
                    customItems = emptyList()
                )
            },
            persistImport = { _, _ -> true },
            capturedAtProvider = { "2026-10-15T10:00:00Z" },
            importIdProvider = { "collision-test" }
        )

        viewModel.selectImages(listOf(image))
        viewModel.analyze()
        val before = viewModel.state.value as DepotScreenshotImportState.Reviewing
        val unknown = before.rows.single { it.observation != null }

        viewModel.confirmCustomNewPosition(
            unknown.key,
            CustomAssetDraft(
                id = "meta",
                name = "Unbekannte AG",
                ticker = "UNK",
                type = "Aktie"
            )
        )

        val reviewing = viewModel.state.value as DepotScreenshotImportState.Reviewing
        val row = reviewing.rows.single { it.key == unknown.key }
        assertFalse(row.resolved)
        assertNull(row.customAssetDraft)
        assertFalse(reviewing.canApply)
    }
}
