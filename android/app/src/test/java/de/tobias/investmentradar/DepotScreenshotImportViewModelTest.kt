package de.tobias.investmentradar

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepotScreenshotImportViewModelTest {
    @Test
    fun multipleImagesAdvanceProgressAndEnterReview() = runBlocking {
        val images = listOf(image("one"), image("two"))
        val progress = mutableListOf<Pair<Int, Int>>()
        val loader = ScreenshotImportBatchLoader { selected, onProgress ->
            selected.forEachIndexed { index, _ -> onProgress(index + 1, selected.size) }
            progress += selected.indices.map { (it + 1) to selected.size }
            ScreenshotImportBatch(
                listOf(
                    success(images[0], observation("Meta Platforms", 110.0, 0)),
                    success(images[1], observation("Apple", 70.0, 1))
                )
            )
        }
        val source = source(
            items = listOf(item("meta", "Meta Platforms"), item("apple", "Apple")),
            positions = mapOf(
                "meta" to position("meta", 100.0),
                "apple" to position("apple", 65.0)
            )
        )
        val viewModel = viewModel(loader, source)

        viewModel.selectImages(images)
        assertEquals(2, (viewModel.state.value as DepotScreenshotImportState.Selecting).images.size)

        viewModel.analyze()

        assertEquals(listOf(1 to 2, 2 to 2), progress)
        val reviewing = viewModel.state.value as DepotScreenshotImportState.Reviewing
        assertEquals(2, reviewing.rows.size)
        assertTrue(reviewing.canApply)
    }

    @Test
    fun oneFailedImageKeepsSuccessfulReviewButBlocksApply() = runBlocking {
        val images = listOf(image("good"), image("broken"))
        val loader = ScreenshotImportBatchLoader { _, onProgress ->
            onProgress(1, 2)
            onProgress(2, 2)
            ScreenshotImportBatch(
                listOf(
                    success(images[0], observation("Meta Platforms", 110.0, 0)),
                    ScreenshotImportImageResult(images[1], errorMessage = "Bild nicht lesbar")
                )
            )
        }
        val source = source(
            items = listOf(item("meta", "Meta Platforms")),
            positions = mapOf("meta" to position("meta", 100.0))
        )
        val viewModel = viewModel(loader, source)
        viewModel.selectImages(images)

        viewModel.analyze()

        val reviewing = viewModel.state.value as DepotScreenshotImportState.Reviewing
        assertEquals(1, reviewing.rows.size)
        assertEquals(1, reviewing.failedImages.size)
        assertFalse(reviewing.canApply)
    }

    @Test
    fun unresolvedConflictBlocksApplyUntilCorrectedAndConfirmed() = runBlocking {
        val images = listOf(image("one"), image("two"))
        val loader = ScreenshotImportBatchLoader { _, onProgress ->
            onProgress(2, 2)
            ScreenshotImportBatch(
                listOf(
                    success(images[0], observation("Meta Platforms", 110.0, 0)),
                    success(images[1], observation("Meta Platforms", 115.0, 1))
                )
            )
        }
        val source = source(
            items = listOf(item("meta", "Meta Platforms")),
            positions = mapOf("meta" to position("meta", 100.0))
        )
        val viewModel = viewModel(loader, source)
        viewModel.selectImages(images)
        viewModel.analyze()

        val conflict = (viewModel.state.value as DepotScreenshotImportState.Reviewing).rows.single()
        assertEquals(ScreenshotReviewStatus.CONFLICT, conflict.status)
        assertFalse((viewModel.state.value as DepotScreenshotImportState.Reviewing).canApply)

        viewModel.correctValues(conflict.key, valueEur = 112.0, costBasisEur = 90.0, shares = null)
        viewModel.confirmReviewedRow(conflict.key)

        assertTrue((viewModel.state.value as DepotScreenshotImportState.Reviewing).canApply)
    }

    @Test
    fun cancelDiscardsSessionWithoutPersistence() = runBlocking {
        var persistenceCalls = 0
        val images = listOf(image("one"))
        val source = source(
            items = listOf(item("meta", "Meta Platforms")),
            positions = mapOf("meta" to position("meta", 100.0))
        )
        val viewModel = viewModel(
            loader = ScreenshotImportBatchLoader { _, onProgress ->
                onProgress(1, 1)
                ScreenshotImportBatch(listOf(success(images[0], observation("Meta Platforms", 110.0, 0))))
            },
            source = source,
            persist = { _, _ -> persistenceCalls++; true }
        )
        viewModel.selectImages(images)
        viewModel.analyze()

        viewModel.cancel()

        assertEquals(0, persistenceCalls)
        val selecting = viewModel.state.value as DepotScreenshotImportState.Selecting
        assertTrue(selecting.images.isEmpty())
    }

    @Test
    fun reviewSummaryCountsUpdateNewSoldDeletedAndKeptExactly() = runBlocking {
        val images = listOf(image("one"))
        val allItems = listOf(
            item("meta", "Meta Platforms"),
            item("apple", "Apple"),
            item("nel", "Nel ASA"),
            item("samsung", "Samsung"),
            item("msft", "Microsoft")
        )
        val existing = mapOf(
            "meta" to position("meta", 100.0),
            "nel" to position("nel", 60.0),
            "samsung" to position("samsung", 59.0),
            "msft" to position("msft", 45.0)
        )
        var persisted: PortfolioImportResult? = null
        var previousActive = emptySet<String>()
        val viewModel = viewModel(
            loader = ScreenshotImportBatchLoader { _, onProgress ->
                onProgress(1, 1)
                ScreenshotImportBatch(
                    listOf(
                        ScreenshotImportImageResult(
                            images[0],
                            TradeRepublicParseResult(
                                recognizedAsTradeRepublic = true,
                                observations = listOf(
                                    observation("Meta Platforms", 110.0, 0),
                                    observation("Apple", 70.0, 0)
                                )
                            )
                        )
                    )
                )
            },
            source = source(allItems, existing),
            persist = { result, _ -> persisted = result; true },
            onCommitted = { previousActive = it }
        )
        viewModel.selectImages(images)
        viewModel.analyze()

        var reviewing = viewModel.state.value as DepotScreenshotImportState.Reviewing
        val apple = reviewing.rows.single { it.itemId == "apple" }
        viewModel.confirmKnownNewPosition(apple.key)
        viewModel.chooseMissingPosition("nel", MissingPositionDecision.MARK_SOLD)
        viewModel.chooseMissingPosition("samsung", MissingPositionDecision.DELETE)
        viewModel.chooseMissingPosition("msft", MissingPositionDecision.KEEP)

        reviewing = viewModel.state.value as DepotScreenshotImportState.Reviewing
        assertTrue(reviewing.canApply)
        assertEquals(1, reviewing.summary.updatedCount)
        assertEquals(1, reviewing.summary.addedCount)
        assertEquals(1, reviewing.summary.soldCount)
        assertEquals(1, reviewing.summary.deletedCount)
        assertEquals(1, reviewing.summary.keptCount)

        viewModel.apply()

        val completed = viewModel.state.value as DepotScreenshotImportState.Completed
        assertEquals(reviewing.summary, completed.summary)
        assertEquals(setOf("meta", "nel", "samsung", "msft"), previousActive)
        assertEquals(PortfolioLifecycleStatus.SOLD_MARKED, persisted!!.positions.getValue("nel").lifecycleStatus)
        assertFalse("samsung" in persisted!!.positions)
        assertTrue("apple" in persisted!!.positions)
    }

    @Test
    fun persistenceFailureProducesFailedAndDoesNotInvokePostCommitRefresh() = runBlocking {
        val images = listOf(image("one"))
        val existing = mapOf("meta" to position("meta", 100.0))
        var postCommitCalls = 0
        val viewModel = viewModel(
            loader = ScreenshotImportBatchLoader { _, onProgress ->
                onProgress(1, 1)
                ScreenshotImportBatch(listOf(success(images[0], observation("Meta Platforms", 110.0, 0))))
            },
            source = source(listOf(item("meta", "Meta Platforms")), existing),
            persist = { _, _ -> false },
            onCommitted = { postCommitCalls++ }
        )
        viewModel.selectImages(images)
        viewModel.analyze()

        viewModel.apply()

        assertTrue(viewModel.state.value is DepotScreenshotImportState.Failed)
        assertEquals(0, postCommitCalls)
        assertEquals(100.0, existing.getValue("meta").snapshotValueEur!!, 0.000001)
    }

    @Test
    fun depotChangedAfterAnalysisBlocksApplyWithoutPersistence() = runBlocking {
        val images = listOf(image("one"))
        val items = listOf(item("meta", "Meta Platforms"))
        val analyzed = source(items, mapOf("meta" to position("meta", 100.0)))
        var current = analyzed
        var persistenceCalls = 0
        var postCommitCalls = 0
        val viewModel = DepotScreenshotImportViewModel(
            batchLoader = ScreenshotImportBatchLoader { _, onProgress ->
                onProgress(1, 1)
                ScreenshotImportBatch(listOf(success(images[0], observation("Meta Platforms", 110.0, 0))))
            },
            sourceProvider = { current },
            persistImport = { _, _ -> persistenceCalls++; true },
            capturedAtProvider = { "2026-10-15T10:00:00Z" },
            importIdProvider = { "import-test" },
            onCommitted = { postCommitCalls++ }
        )
        viewModel.selectImages(images)
        viewModel.analyze()
        assertTrue((viewModel.state.value as DepotScreenshotImportState.Reviewing).canApply)

        val purchase = PortfolioPurchase(id = "buy-1", date = "2026-10-15", investedAmount = 50.0, shares = 0.5)
        current = analyzed.copy(
            positions = mapOf("meta" to position("meta", 100.0).copy(purchases = listOf(purchase)))
        )
        viewModel.apply()

        val failed = viewModel.state.value as DepotScreenshotImportState.Failed
        assertTrue(failed.message.contains("erneut auswerten"))
        assertEquals(null, failed.previousReview)
        assertEquals(0, persistenceCalls)
        assertEquals(0, postCommitCalls)
    }

    @Test
    fun soldPositionReappearingRequiresExplicitReactivation() = runBlocking {
        val images = listOf(image("one"))
        val source = source(
            items = listOf(item("meta", "Meta Platforms")),
            positions = mapOf(
                "meta" to position("meta", 100.0).copy(lifecycleStatus = PortfolioLifecycleStatus.SOLD_MARKED)
            )
        )
        var persisted: PortfolioImportResult? = null
        val viewModel = viewModel(
            loader = ScreenshotImportBatchLoader { _, onProgress ->
                onProgress(1, 1)
                ScreenshotImportBatch(listOf(success(images[0], observation("Meta Platforms", 120.0, 0))))
            },
            source = source,
            persist = { result, _ -> persisted = result; true }
        )
        viewModel.selectImages(images)
        viewModel.analyze()

        val reviewing = viewModel.state.value as DepotScreenshotImportState.Reviewing
        val row = reviewing.rows.single()
        assertEquals(ScreenshotReviewStatus.SOLD_REAPPEARED, row.status)
        assertFalse(reviewing.canApply)

        viewModel.confirmReactivation(row.key)
        assertTrue((viewModel.state.value as DepotScreenshotImportState.Reviewing).canApply)
        viewModel.apply()

        assertEquals(PortfolioLifecycleStatus.ACTIVE, persisted!!.positions.getValue("meta").lifecycleStatus)
    }

    @Test
    fun holdingTopicChangesUseOnlyActivePositions() {
        val previousActive = setOf("meta", "sold", "deleted")
        val current = mapOf(
            "meta" to position("meta", 100.0),
            "sold" to position("sold", 80.0).copy(lifecycleStatus = PortfolioLifecycleStatus.SOLD_MARKED),
            "new" to position("new", 50.0)
        )

        val changes = MainViewModel.holdingTopicChanges(previousActive, current)

        assertEquals(setOf("new"), changes.subscribe)
        assertEquals(setOf("sold", "deleted"), changes.unsubscribe)
        assertEquals(setOf("meta", "new"), changes.activeIds)
    }

    private fun viewModel(
        loader: ScreenshotImportBatchLoader,
        source: DepotImportSourceSnapshot,
        persist: (PortfolioImportResult, PortfolioImportMetadata) -> Boolean = { _, _ -> true },
        onCommitted: (Set<String>) -> Unit = {}
    ) = DepotScreenshotImportViewModel(
        batchLoader = loader,
        sourceProvider = { source },
        persistImport = persist,
        capturedAtProvider = { "2026-10-15T10:00:00Z" },
        importIdProvider = { "import-test" },
        onCommitted = onCommitted
    )

    private fun image(id: String) = ScreenshotImageRef(id, "content://screenshots/$id")

    private fun success(
        image: ScreenshotImageRef,
        observation: TradeRepublicScreenshotObservation
    ) = ScreenshotImportImageResult(
        image = image,
        parseResult = TradeRepublicParseResult(true, listOf(observation))
    )

    private fun observation(name: String, value: Double, sourceIndex: Int) =
        TradeRepublicScreenshotObservation(
            name = name,
            valueEur = value,
            profitLossEur = null,
            profitLossPct = null,
            shares = null,
            derivedCostBasisEur = null,
            sourceIndex = sourceIndex
        )

    private fun position(id: String, value: Double) = PortfolioPosition(
        itemId = id,
        snapshotValueEur = value,
        snapshotCostBasisEur = value * 0.8,
        snapshotCapturedAt = "2026-10-14T10:00:00Z"
    )

    private fun source(
        items: List<InvestmentItem>,
        positions: Map<String, PortfolioPosition>
    ) = DepotImportSourceSnapshot(
        positions = positions,
        builtInItems = items,
        customItems = emptyList()
    )

    private fun item(id: String, name: String) = InvestmentItem(
        id = id,
        type = "Aktie",
        name = name,
        ticker = id.uppercase(),
        isin = "",
        tradeRepublicName = name,
        status = "HALTEN",
        allocation = 0,
        risk = 3,
        price = null,
        priceEur = null,
        currency = "EUR",
        fxRateToEur = null,
        fxSource = "",
        fxDelayed = false,
        fxAsOf = null,
        percentChange = null,
        marketOpen = null,
        dataSource = "test",
        dataDelayed = false,
        dataError = null
    )
}
