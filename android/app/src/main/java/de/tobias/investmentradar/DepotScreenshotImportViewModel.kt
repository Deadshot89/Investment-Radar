package de.tobias.investmentradar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun interface ScreenshotImportBatchLoader {
    suspend fun load(
        images: List<ScreenshotImageRef>,
        onProgress: (completed: Int, total: Int) -> Unit
    ): ScreenshotImportBatch
}

data class DepotImportSourceSnapshot(
    val positions: Map<String, PortfolioPosition>,
    val builtInItems: List<InvestmentItem>,
    val customItems: List<CustomInvestment>
)

enum class DepotScreenshotImportRowKind {
    UPDATE,
    ADD,
    REACTIVATE,
    MISSING
}

data class DepotScreenshotImportReviewRow(
    val key: String,
    val itemId: String?,
    val observation: TradeRepublicScreenshotObservation?,
    val status: ScreenshotReviewStatus,
    val reason: String? = null,
    val candidateItemIds: List<String> = emptyList(),
    val sourceIndices: Set<Int> = emptySet(),
    val kind: DepotScreenshotImportRowKind,
    val resolved: Boolean,
    val missingDecision: MissingPositionDecision? = null,
    val customAssetDraft: CustomAssetDraft? = null
)

sealed interface DepotScreenshotImportState {
    data class Selecting(val images: List<ScreenshotImageRef> = emptyList()) : DepotScreenshotImportState

    data class Recognizing(
        val images: List<ScreenshotImageRef>,
        val completed: Int,
        val total: Int
    ) : DepotScreenshotImportState

    data class Reviewing(
        val images: List<ScreenshotImageRef>,
        val rows: List<DepotScreenshotImportReviewRow>,
        val failedImages: List<ScreenshotImportImageResult>,
        val summary: PortfolioImportSummary,
        val canApply: Boolean,
        val completeCoverageConfirmed: Boolean = false,
        val canConfirmCompleteCoverage: Boolean = false
    ) : DepotScreenshotImportState

    data class Applying(
        val summary: PortfolioImportSummary
    ) : DepotScreenshotImportState

    data class Completed(
        val summary: PortfolioImportSummary
    ) : DepotScreenshotImportState

    data class Failed(
        val message: String,
        val previousReview: Reviewing? = null
    ) : DepotScreenshotImportState
}

class DepotScreenshotImportViewModel(
    private val batchLoader: ScreenshotImportBatchLoader,
    private val sourceProvider: () -> DepotImportSourceSnapshot,
    private val persistImport: (PortfolioImportResult, PortfolioImportMetadata) -> Boolean,
    private val capturedAtProvider: () -> String,
    private val importIdProvider: () -> String,
    private val onCommitted: (previousActiveHoldingIds: Set<String>) -> Unit = {}
) {
    private val _state = MutableStateFlow<DepotScreenshotImportState>(DepotScreenshotImportState.Selecting())
    val state: StateFlow<DepotScreenshotImportState> = _state.asStateFlow()

    private var selectedImages: List<ScreenshotImageRef> = emptyList()
    private var imageResults: List<ScreenshotImportImageResult> = emptyList()
    private var sourceSnapshot: DepotImportSourceSnapshot? = null
    private var completeCoverageConfirmed = false

    fun selectImages(images: List<ScreenshotImageRef>) {
        selectedImages = images.distinctBy { it.sourceId to it.uriString }
        imageResults = emptyList()
        sourceSnapshot = null
        completeCoverageConfirmed = false
        _state.value = DepotScreenshotImportState.Selecting(selectedImages)
    }

    suspend fun analyze() {
        if (selectedImages.isEmpty()) {
            _state.value = DepotScreenshotImportState.Failed("Bitte mindestens einen Trade-Republic-Screenshot auswählen.")
            return
        }
        completeCoverageConfirmed = false
        val source = sourceProvider()
        sourceSnapshot = source
        _state.value = DepotScreenshotImportState.Recognizing(selectedImages, 0, selectedImages.size)
        try {
            val batch = batchLoader.load(selectedImages) { completed, total ->
                _state.value = DepotScreenshotImportState.Recognizing(
                    images = selectedImages,
                    completed = completed.coerceIn(0, total.coerceAtLeast(0)),
                    total = total.coerceAtLeast(0)
                )
            }
            imageResults = normalizeBatchResults(batch.images)
            rebuildReview(source)
        } catch (error: Exception) {
            _state.value = DepotScreenshotImportState.Failed(
                error.message?.takeIf { it.isNotBlank() } ?: "Screenshots konnten nicht ausgewertet werden."
            )
        }
    }

    fun correctValues(
        rowKey: String,
        valueEur: Double,
        costBasisEur: Double?,
        shares: Double?
    ) {
        mutateReviewRow(rowKey) { row ->
            val current = row.observation ?: return@mutateReviewRow row
            row.copy(
                observation = current.copy(
                    valueEur = valueEur,
                    derivedCostBasisEur = costBasisEur,
                    shares = shares
                ),
                status = ScreenshotReviewStatus.NEEDS_REVIEW,
                reason = "Korrigierte Werte müssen bestätigt werden",
                resolved = false
            )
        }
    }

    fun assignMatch(rowKey: String, itemId: String) {
        val source = sourceSnapshot ?: return
        val reviewing = _state.value as? DepotScreenshotImportState.Reviewing ?: return
        val normalized = itemId.trim()
        if (normalized.isBlank()) return
        val original = reviewing.rows.firstOrNull { it.key == rowKey } ?: return
        val current = source.positions[normalized]
        val kind = when (current?.lifecycleStatus) {
            PortfolioLifecycleStatus.SOLD_MARKED -> DepotScreenshotImportRowKind.REACTIVATE
            PortfolioLifecycleStatus.ACTIVE -> DepotScreenshotImportRowKind.UPDATE
            null -> DepotScreenshotImportRowKind.ADD
        }
        val updated = original.copy(
            itemId = normalized,
            kind = kind,
            status = when (kind) {
                DepotScreenshotImportRowKind.REACTIVATE -> ScreenshotReviewStatus.SOLD_REAPPEARED
                DepotScreenshotImportRowKind.ADD -> ScreenshotReviewStatus.NEW_POSITION
                DepotScreenshotImportRowKind.UPDATE -> ScreenshotReviewStatus.NEEDS_REVIEW
                DepotScreenshotImportRowKind.MISSING -> original.status
            },
            reason = "Zuordnung muss bestätigt werden",
            resolved = false,
            customAssetDraft = null
        )

        val candidateScope = (original.candidateItemIds + normalized).toSet()
        val baseRows = reviewing.rows
            .map { row -> if (row.key == rowKey) updated else row }
            .filterNot { row -> row.kind == DepotScreenshotImportRowKind.MISSING && row.itemId in candidateScope }
        val alreadyObservedIds = baseRows
            .asSequence()
            .filter { row -> row.key != rowKey && row.kind != DepotScreenshotImportRowKind.MISSING && row.observation != null }
            .mapNotNull { it.itemId }
            .toSet()
        val derivedMissingRows = original.candidateItemIds
            .asSequence()
            .distinct()
            .filter { candidateId -> completeCoverageConfirmed && candidateId != normalized }
            .filter { candidateId -> candidateId !in alreadyObservedIds }
            .filter { candidateId -> shouldTrackMissingCandidate(candidateId, source) }
            .map { candidateId ->
                DepotScreenshotImportReviewRow(
                    key = "ambiguous-missing|$rowKey|$candidateId",
                    itemId = candidateId,
                    observation = null,
                    status = ScreenshotReviewStatus.NOT_FOUND,
                    reason = "Nach manueller Zuordnung auf den aktuellen Trade-Republic-Screenshots nicht gefunden",
                    kind = DepotScreenshotImportRowKind.MISSING,
                    resolved = false
                )
            }
            .toList()

        publishReview(baseRows + derivedMissingRows, reviewing.failedImages)
    }

    fun confirmReviewedRow(rowKey: String) {
        mutateReviewRow(rowKey) { row ->
            if (row.itemId.isNullOrBlank() || row.observation == null || !validObservation(row.observation)) return@mutateReviewRow row
            if (row.kind != DepotScreenshotImportRowKind.UPDATE) return@mutateReviewRow row
            row.copy(status = ScreenshotReviewStatus.READY, reason = null, resolved = true)
        }
    }

    fun confirmKnownNewPosition(rowKey: String) {
        val source = sourceSnapshot ?: return
        mutateReviewRow(rowKey) { row ->
            val itemId = row.itemId ?: return@mutateReviewRow row
            if (itemId in source.positions || row.observation == null || !validObservation(row.observation)) return@mutateReviewRow row
            val known = source.builtInItems.any { it.id == itemId } || source.customItems.any { it.id == itemId }
            if (!known) return@mutateReviewRow row
            row.copy(
                kind = DepotScreenshotImportRowKind.ADD,
                status = ScreenshotReviewStatus.NEW_POSITION,
                reason = null,
                resolved = true,
                customAssetDraft = null
            )
        }
    }

    fun confirmCustomNewPosition(rowKey: String, draft: CustomAssetDraft) {
        val source = sourceSnapshot ?: return
        mutateReviewRow(rowKey) { row ->
            if (row.observation == null || !validObservation(row.observation)) return@mutateReviewRow row
            val custom = runCatching { draft.toCustomInvestment() }.getOrNull() ?: return@mutateReviewRow row
            if (
                custom.id in source.positions ||
                source.builtInItems.any { it.id == custom.id } ||
                source.customItems.any { it.id == custom.id }
            ) return@mutateReviewRow row
            row.copy(
                itemId = custom.id,
                kind = DepotScreenshotImportRowKind.ADD,
                status = ScreenshotReviewStatus.NEW_POSITION,
                reason = null,
                resolved = true,
                customAssetDraft = draft
            )
        }
    }

    fun confirmReactivation(rowKey: String) {
        val source = sourceSnapshot ?: return
        mutateReviewRow(rowKey) { row ->
            val itemId = row.itemId ?: return@mutateReviewRow row
            val current = source.positions[itemId] ?: return@mutateReviewRow row
            if (current.lifecycleStatus != PortfolioLifecycleStatus.SOLD_MARKED || row.observation == null || !validObservation(row.observation)) {
                return@mutateReviewRow row
            }
            row.copy(
                kind = DepotScreenshotImportRowKind.REACTIVATE,
                status = ScreenshotReviewStatus.SOLD_REAPPEARED,
                reason = null,
                resolved = true
            )
        }
    }

    /**
     * In normal (partial) imports, unmatched existing positions remain untouched.
     * Full-coverage mode is opt-in and can only be enabled after every selected
     * screenshot has been parsed safely. Preserve all manually reviewed rows
     * when the user toggles this option.
     */
    fun setCompleteCoverageConfirmed(confirmed: Boolean) {
        val reviewing = _state.value as? DepotScreenshotImportState.Reviewing ?: return
        val source = sourceSnapshot ?: return
        if (confirmed && !reviewing.canConfirmCompleteCoverage) return
        completeCoverageConfirmed = confirmed

        val observedRows = reviewing.rows.filterNot { it.kind == DepotScreenshotImportRowKind.MISSING }
        val observedIds = observedRows.filter { it.observation != null }.mapNotNull { it.itemId }.toSet()
        val missing = if (confirmed) {
            val allObservations = imageResults.flatMap { it.parseResult?.observations.orEmpty() }
            ScreenshotDepotReconciliation.reconcileTradeRepublic(
                observations = allObservations,
                builtInItems = source.builtInItems,
                customItems = source.customItems,
                existing = source.positions,
                includeMissingPositions = true
            ).filter { it.status == ScreenshotReviewStatus.NOT_FOUND && it.itemId !in observedIds }
                .mapIndexed { index, item -> item.toCoordinatorRow(index + observedRows.size, source) }
        } else emptyList()

        // A manually resolved ambiguous match may leave other candidate
        // positions absent. Recompute these only in explicit full-coverage mode,
        // even if the user enabled it after resolving the row.
        val reservedByUnresolved = observedRows
            .filter { it.itemId == null && it.observation != null }
            .flatMap { it.candidateItemIds }
            .toSet()
        val missingIds = missing.mapNotNull { it.itemId }.toSet()
        val ambiguousMissing = if (confirmed) observedRows.asSequence()
            .filter { it.itemId != null && it.observation != null }
            .flatMap { row -> row.candidateItemIds.asSequence().filter { it != row.itemId } }
            .distinct()
            .filter { candidateId ->
                candidateId !in observedIds &&
                    candidateId !in reservedByUnresolved &&
                    candidateId !in missingIds &&
                    shouldTrackMissingCandidate(candidateId, source)
            }
            .mapIndexed { index, candidateId ->
                DepotScreenshotImportReviewRow(
                    key = "confirmed-missing|$candidateId|$index",
                    itemId = candidateId,
                    observation = null,
                    status = ScreenshotReviewStatus.NOT_FOUND,
                    reason = "Nach manueller Zuordnung auf den vollständigen Screenshots nicht gefunden",
                    kind = DepotScreenshotImportRowKind.MISSING,
                    resolved = false
                )
            }
            .toList() else emptyList()

        publishReview(observedRows + missing + ambiguousMissing, reviewing.failedImages)
    }

    private fun canReviewFullCoverage(): Boolean =
        imageResults.isNotEmpty() && imageResults.all { result ->
            result.errorMessage == null &&
                result.parseResult?.recognizedAsTradeRepublic == true &&
                result.parseResult.observations.isNotEmpty() &&
                result.parseResult.observations.none { it.requiresConfirmation }
        }

    fun chooseMissingPosition(itemId: String, decision: MissingPositionDecision) {
        if (!completeCoverageConfirmed) return
        val normalized = itemId.trim()
        mutateReviewRowByItemId(normalized) { row ->
            if (row.kind != DepotScreenshotImportRowKind.MISSING) return@mutateReviewRowByItemId row
            row.copy(missingDecision = decision, reason = null, resolved = true)
        }
    }

    suspend fun retryFailedImage(sourceId: String) {
        val source = sourceSnapshot ?: return
        val imageIndex = selectedImages.indexOfFirst { it.sourceId == sourceId }
        if (imageIndex < 0) return
        val image = selectedImages[imageIndex]
        completeCoverageConfirmed = false
        _state.value = DepotScreenshotImportState.Recognizing(selectedImages, 0, 1)
        try {
            val retried = batchLoader.load(listOf(image)) { completed, total ->
                _state.value = DepotScreenshotImportState.Recognizing(selectedImages, completed, total)
            }.images.firstOrNull() ?: ScreenshotImportImageResult(image, errorMessage = "Screenshot konnte nicht gelesen werden")
            val normalized = remapResultSourceIndex(retried, imageIndex)
            val mutable = imageResults.toMutableList()
            val resultIndex = mutable.indexOfFirst { it.image.sourceId == sourceId }
            if (resultIndex >= 0) mutable[resultIndex] = normalized else mutable += normalized
            imageResults = normalizeBatchResults(mutable)
            rebuildReview(source)
        } catch (error: Exception) {
            val mutable = imageResults.toMutableList()
            val failed = ScreenshotImportImageResult(
                image = image,
                errorMessage = error.message?.takeIf { it.isNotBlank() } ?: "Screenshot konnte nicht gelesen werden"
            )
            val resultIndex = mutable.indexOfFirst { it.image.sourceId == sourceId }
            if (resultIndex >= 0) mutable[resultIndex] = failed else mutable += failed
            imageResults = normalizeBatchResults(mutable)
            rebuildReview(source)
        }
    }

    fun apply() {
        val reviewing = _state.value as? DepotScreenshotImportState.Reviewing ?: return
        if (!reviewing.canApply) return
        if (!completeCoverageConfirmed && reviewing.rows.any { it.kind == DepotScreenshotImportRowKind.MISSING }) return
        val source = sourceSnapshot ?: return
        val latest = runCatching { sourceProvider() }.getOrNull()
        if (latest == null || latest.positions != source.positions || latest.customItems != source.customItems) {
            _state.value = DepotScreenshotImportState.Failed(
                "Das Depot wurde seit der Auswertung geändert. Bitte die Screenshots erneut auswerten, " +
                    "damit keine neueren Änderungen überschrieben werden."
            )
            return
        }
        val capturedAt = capturedAtProvider()

        val result: PortfolioImportResult
        val summary: PortfolioImportSummary
        try {
            val changes = reviewing.rows.map { it.toImportChange() }
            result = PortfolioImportApplier.preview(
                existingPositions = source.positions,
                existingCustomAssets = source.customItems,
                changes = changes,
                capturedAt = capturedAt
            )
            summary = result.summary
            val metadata = PortfolioImportMetadata(
                importId = importIdProvider(),
                capturedAt = capturedAt,
                screenshotCount = selectedImages.size,
                updatedCount = summary.updatedCount + summary.reactivatedCount,
                addedCount = summary.addedCount,
                soldCount = summary.soldCount,
                deletedCount = summary.deletedCount
            )
            _state.value = DepotScreenshotImportState.Applying(summary)
            if (!persistImport(result, metadata)) {
                _state.value = DepotScreenshotImportState.Failed("Depot-Update konnte nicht gespeichert werden.", reviewing)
                return
            }
        } catch (error: Exception) {
            _state.value = DepotScreenshotImportState.Failed(
                error.message?.takeIf { it.isNotBlank() } ?: "Depot-Update konnte nicht übernommen werden.",
                reviewing
            )
            return
        }

        val previousActive = source.positions
            .filterValues { it.isActiveHolding() }
            .keys
            .toSet()
        runCatching { onCommitted(previousActive) }
        _state.value = DepotScreenshotImportState.Completed(summary)
    }

    fun cancel() {
        selectedImages = emptyList()
        imageResults = emptyList()
        sourceSnapshot = null
        completeCoverageConfirmed = false
        _state.value = DepotScreenshotImportState.Selecting()
    }

    private fun rebuildReview(source: DepotImportSourceSnapshot) {
        val successful = imageResults.filter { result ->
            result.errorMessage == null &&
                result.parseResult != null &&
                result.parseResult.recognizedAsTradeRepublic
        }
        val failed = imageResults.map { result ->
            if (result.errorMessage == null && result.parseResult != null && !result.parseResult.recognizedAsTradeRepublic) {
                val message = if (result.recognizedTextLines == 0) {
                    "Keine Textzeile im Bild erkannt. Bitte Original-Screenshot statt Vorschau wählen und erneut versuchen."
                } else {
                    "${result.recognizedTextLines} Textzeilen erkannt, aber keine sichere Aktienposition mit Euro-Depotwert. Bitte die Positionsliste statt einer Kurs- oder Transaktionsansicht auswählen."
                }
                result.copy(errorMessage = message, parseResult = null)
            } else {
                result
            }
        }.filter { it.errorMessage != null }
        // Do not label all stored holdings as missing if OCR has not read a single image.
        if (successful.isEmpty()) {
            publishReview(emptyList(), failed)
            return
        }
        val observations = successful.flatMap { it.parseResult?.observations.orEmpty() }
        val reconciled = ScreenshotDepotReconciliation.reconcileTradeRepublic(
            observations = observations,
            builtInItems = source.builtInItems,
            customItems = source.customItems,
            existing = source.positions,
            includeMissingPositions = completeCoverageConfirmed && canReviewFullCoverage()
        )
        val rows = reconciled.mapIndexed { index, item -> item.toCoordinatorRow(index, source) }
        publishReview(rows, failed)
    }

    private fun publishReview(
        rows: List<DepotScreenshotImportReviewRow>,
        failed: List<ScreenshotImportImageResult>
    ) {
        val fullCoverageAvailable = failed.isEmpty() && canReviewFullCoverage()
        if (!fullCoverageAvailable) completeCoverageConfirmed = false
        val safeRows = if (completeCoverageConfirmed) rows else rows.filterNot { it.kind == DepotScreenshotImportRowKind.MISSING }
        val summary = summaryOf(safeRows)
        // Manually assigned OCR rows can target the same holding. Never allow
        // last-write-wins or a simultaneous UPDATE and MARK_SOLD/DELETE.
        val assignedIds = safeRows.mapNotNull { it.itemId?.takeIf(String::isNotBlank) }
        val hasDuplicateAssignments = assignedIds.size != assignedIds.toSet().size
        val canApply = failed.isEmpty() && !hasDuplicateAssignments && safeRows.isNotEmpty() &&
            safeRows.all { it.resolved && rowIsValidForApply(it) }
        _state.value = DepotScreenshotImportState.Reviewing(
            images = selectedImages,
            rows = safeRows,
            failedImages = failed,
            summary = summary,
            canApply = canApply,
            completeCoverageConfirmed = completeCoverageConfirmed,
            canConfirmCompleteCoverage = fullCoverageAvailable
        )
    }

    private fun mutateReviewRow(
        rowKey: String,
        transform: (DepotScreenshotImportReviewRow) -> DepotScreenshotImportReviewRow
    ) {
        val reviewing = _state.value as? DepotScreenshotImportState.Reviewing ?: return
        val rows = reviewing.rows.map { row -> if (row.key == rowKey) transform(row) else row }
        publishReview(rows, reviewing.failedImages)
    }

    private fun mutateReviewRowByItemId(
        itemId: String,
        transform: (DepotScreenshotImportReviewRow) -> DepotScreenshotImportReviewRow
    ) {
        val reviewing = _state.value as? DepotScreenshotImportState.Reviewing ?: return
        val rows = reviewing.rows.map { row -> if (row.itemId == itemId) transform(row) else row }
        publishReview(rows, reviewing.failedImages)
    }

    private fun shouldTrackMissingCandidate(
        itemId: String,
        source: DepotImportSourceSnapshot
    ): Boolean {
        val position = source.positions[itemId] ?: return false
        if (!position.isActiveHolding()) return false
        source.builtInItems.firstOrNull { it.id == itemId }?.let { item ->
            return PortfolioAssetMatcher.isTradeRepublicCandidate(item) && item.tradeRepublicName.isNotBlank()
        }
        source.customItems.firstOrNull { it.id == itemId }?.let { item ->
            return PortfolioAssetMatcher.isTradeRepublicCandidate(item) &&
                (item.tradeRepublicUrl.isNotBlank() || !position.snapshotCapturedAt.isNullOrBlank())
        }
        return false
    }

    private fun TradeRepublicScreenshotReviewItem.toCoordinatorRow(
        index: Int,
        source: DepotImportSourceSnapshot
    ): DepotScreenshotImportReviewRow {
        val current = itemId?.let(source.positions::get)
        val kind = when (status) {
            ScreenshotReviewStatus.NOT_FOUND -> DepotScreenshotImportRowKind.MISSING
            ScreenshotReviewStatus.SOLD_REAPPEARED -> DepotScreenshotImportRowKind.REACTIVATE
            ScreenshotReviewStatus.NEW_POSITION -> DepotScreenshotImportRowKind.ADD
            else -> when {
                current?.lifecycleStatus == PortfolioLifecycleStatus.SOLD_MARKED -> DepotScreenshotImportRowKind.REACTIVATE
                current != null -> DepotScreenshotImportRowKind.UPDATE
                else -> DepotScreenshotImportRowKind.ADD
            }
        }
        val resolved = status == ScreenshotReviewStatus.READY && kind == DepotScreenshotImportRowKind.UPDATE
        val stableKey = buildString {
            append(itemId.orEmpty())
            append('|')
            append(observation?.name.orEmpty())
            append('|')
            append(sourceIndices.sorted().joinToString(","))
            append('|')
            append(index)
        }
        return DepotScreenshotImportReviewRow(
            key = stableKey,
            itemId = itemId,
            observation = observation,
            status = status,
            reason = reason,
            candidateItemIds = candidateItemIds,
            sourceIndices = sourceIndices,
            kind = kind,
            resolved = resolved
        )
    }

    private fun summaryOf(rows: List<DepotScreenshotImportReviewRow>): PortfolioImportSummary {
        var summary = PortfolioImportSummary()
        rows.filter { it.resolved }.forEach { row ->
            summary = when (row.kind) {
                DepotScreenshotImportRowKind.UPDATE -> summary.copy(updatedCount = summary.updatedCount + 1)
                DepotScreenshotImportRowKind.ADD -> summary.copy(addedCount = summary.addedCount + 1)
                DepotScreenshotImportRowKind.REACTIVATE -> summary.copy(reactivatedCount = summary.reactivatedCount + 1)
                DepotScreenshotImportRowKind.MISSING -> when (row.missingDecision) {
                    MissingPositionDecision.KEEP -> summary.copy(keptCount = summary.keptCount + 1)
                    MissingPositionDecision.MARK_SOLD -> summary.copy(soldCount = summary.soldCount + 1)
                    MissingPositionDecision.DELETE -> summary.copy(deletedCount = summary.deletedCount + 1)
                    null -> summary
                }
            }
        }
        return summary
    }

    private fun DepotScreenshotImportReviewRow.toImportChange(): PortfolioImportChange {
        val id = requireNotNull(itemId) { "Wertpapier-ID fehlt." }
        return when (kind) {
            DepotScreenshotImportRowKind.UPDATE -> {
                val value = requireNotNull(observation?.valueEur) { "Depotwert fehlt." }
                PortfolioImportChange.UpdateSnapshot(
                    itemId = id,
                    valueEur = value,
                    costBasisEur = observation.derivedCostBasisEur,
                    trackedShares = observation.shares
                )
            }
            DepotScreenshotImportRowKind.ADD -> {
                val value = requireNotNull(observation?.valueEur) { "Depotwert fehlt." }
                PortfolioImportChange.AddPosition(
                    itemId = id,
                    valueEur = value,
                    costBasisEur = observation.derivedCostBasisEur,
                    trackedShares = observation.shares,
                    customAssetDraft = customAssetDraft
                )
            }
            DepotScreenshotImportRowKind.REACTIVATE -> {
                val value = requireNotNull(observation?.valueEur) { "Depotwert fehlt." }
                PortfolioImportChange.Reactivate(
                    itemId = id,
                    valueEur = value,
                    costBasisEur = observation.derivedCostBasisEur,
                    trackedShares = observation.shares
                )
            }
            DepotScreenshotImportRowKind.MISSING -> PortfolioImportChange.MissingPosition(
                itemId = id,
                decision = requireNotNull(missingDecision) { "Entscheidung für fehlende Position fehlt." }
            )
        }
    }

    private fun rowIsValidForApply(row: DepotScreenshotImportReviewRow): Boolean = when (row.kind) {
        DepotScreenshotImportRowKind.MISSING -> row.itemId?.isNotBlank() == true && row.missingDecision != null
        else -> row.itemId?.isNotBlank() == true && row.observation?.let(::validObservation) == true
    }

    private fun validObservation(observation: TradeRepublicScreenshotObservation): Boolean =
        observation.name.isNotBlank() &&
            observation.valueEur?.let { it.isFinite() && it >= 0.0 } == true &&
            (observation.derivedCostBasisEur == null || (observation.derivedCostBasisEur.isFinite() && observation.derivedCostBasisEur >= 0.0)) &&
            (observation.shares == null || (observation.shares.isFinite() && observation.shares > 0.0))

    private fun normalizeBatchResults(results: List<ScreenshotImportImageResult>): List<ScreenshotImportImageResult> =
        selectedImages.mapIndexed { index, selected ->
            val result = results.firstOrNull { it.image.sourceId == selected.sourceId }
                ?: ScreenshotImportImageResult(selected, errorMessage = "Screenshot-Ergebnis fehlt")
            remapResultSourceIndex(result.copy(image = selected), index)
        }

    private fun remapResultSourceIndex(
        result: ScreenshotImportImageResult,
        sourceIndex: Int
    ): ScreenshotImportImageResult = result.copy(
        parseResult = result.parseResult?.copy(
            observations = result.parseResult.observations.map { it.copy(sourceIndex = sourceIndex) }
        )
    )
}
