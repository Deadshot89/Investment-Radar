package de.tobias.investmentradar

enum class MissingPositionDecision {
    KEEP,
    MARK_SOLD,
    DELETE
}

data class CustomAssetDraft(
    val id: String,
    val name: String,
    val ticker: String,
    val isin: String = "",
    val type: String
) {
    fun toCustomInvestment(): CustomInvestment {
        val normalizedId = id.trim()
        val normalizedName = name.trim()
        val normalizedTicker = ticker.trim().uppercase()
        val normalizedIsin = isin.trim().uppercase()
        val normalizedType = when (type.trim().lowercase()) {
            "aktie", "stock" -> "Aktie"
            "etf" -> "ETF"
            else -> throw IllegalArgumentException("Nur Aktien und ETFs können aus Trade-Republic-Screenshots neu angelegt werden.")
        }
        require(normalizedId.isNotBlank()) { "Custom-Asset-ID fehlt." }
        require(normalizedName.isNotBlank()) { "Name des neuen Wertpapiers fehlt." }
        require(normalizedTicker.isNotBlank()) { "Ticker des neuen Wertpapiers fehlt." }
        return CustomInvestment(
            id = normalizedId,
            name = normalizedName,
            ticker = normalizedTicker,
            isin = normalizedIsin,
            type = normalizedType
        )
    }
}

sealed interface PortfolioImportChange {
    val itemId: String

    data class UpdateSnapshot(
        override val itemId: String,
        val valueEur: Double,
        val costBasisEur: Double? = null,
        val trackedShares: Double? = null
    ) : PortfolioImportChange

    data class AddPosition(
        override val itemId: String,
        val valueEur: Double,
        val costBasisEur: Double? = null,
        val trackedShares: Double? = null,
        val customAssetDraft: CustomAssetDraft? = null
    ) : PortfolioImportChange

    data class Reactivate(
        override val itemId: String,
        val valueEur: Double,
        val costBasisEur: Double? = null,
        val trackedShares: Double? = null
    ) : PortfolioImportChange

    data class MissingPosition(
        override val itemId: String,
        val decision: MissingPositionDecision
    ) : PortfolioImportChange
}

data class PortfolioImportSummary(
    val updatedCount: Int = 0,
    val addedCount: Int = 0,
    val reactivatedCount: Int = 0,
    val soldCount: Int = 0,
    val deletedCount: Int = 0,
    val keptCount: Int = 0
)

data class PortfolioImportResult(
    val positions: Map<String, PortfolioPosition>,
    val customAssets: List<CustomInvestment> = emptyList(),
    val summary: PortfolioImportSummary
)

data class PortfolioImportMetadata(
    val importId: String,
    val capturedAt: String,
    val screenshotCount: Int,
    val updatedCount: Int,
    val addedCount: Int,
    val soldCount: Int,
    val deletedCount: Int
)

object PortfolioImportApplier {
    fun preview(
        existing: Map<String, PortfolioPosition>,
        changes: List<PortfolioImportChange>,
        capturedAt: String
    ): PortfolioImportResult = preview(
        existingPositions = existing,
        existingCustomAssets = emptyList(),
        changes = changes,
        capturedAt = capturedAt
    )

    fun preview(
        existingPositions: Map<String, PortfolioPosition>,
        existingCustomAssets: List<CustomInvestment>,
        changes: List<PortfolioImportChange>,
        capturedAt: String
    ): PortfolioImportResult {
        require(capturedAt.isNotBlank()) { "Snapshot-Zeitpunkt fehlt." }
        val next = existingPositions.toMutableMap()
        val nextCustomAssets = existingCustomAssets.toMutableList()
        var summary = PortfolioImportSummary()

        changes.forEach { change ->
            require(change.itemId.isNotBlank()) { "Wertpapier-ID fehlt." }
            when (change) {
                is PortfolioImportChange.UpdateSnapshot -> {
                    validateSnapshot(change.valueEur, change.costBasisEur, change.trackedShares)
                    val current = next[change.itemId]
                        ?: throw IllegalArgumentException("Position '${change.itemId}' ist nicht vorhanden.")
                    require(current.lifecycleStatus == PortfolioLifecycleStatus.ACTIVE) {
                        "Verkaufte Position '${change.itemId}' muss ausdrücklich reaktiviert werden."
                    }
                    next[change.itemId] = current.copy(
                        snapshotValueEur = change.valueEur,
                        snapshotCostBasisEur = change.costBasisEur ?: current.snapshotCostBasisEur,
                        trackedShares = change.trackedShares ?: current.trackedShares,
                        snapshotCapturedAt = capturedAt
                    )
                    summary = summary.copy(updatedCount = summary.updatedCount + 1)
                }

                is PortfolioImportChange.AddPosition -> {
                    validateSnapshot(change.valueEur, change.costBasisEur, change.trackedShares)
                    require(change.itemId !in next) { "Position '${change.itemId}' ist bereits vorhanden." }
                    change.customAssetDraft?.let { draft ->
                        require(draft.id.trim() == change.itemId.trim()) {
                            "Custom-Asset-ID und Depotposition stimmen nicht überein."
                        }
                        require(nextCustomAssets.none { it.id == change.itemId }) {
                            "Custom-Wert '${change.itemId}' ist bereits vorhanden."
                        }
                        nextCustomAssets += draft.toCustomInvestment()
                    }
                    next[change.itemId] = PortfolioPosition(
                        itemId = change.itemId,
                        snapshotValueEur = change.valueEur,
                        snapshotCostBasisEur = change.costBasisEur,
                        trackedShares = change.trackedShares,
                        lifecycleStatus = PortfolioLifecycleStatus.ACTIVE,
                        snapshotCapturedAt = capturedAt
                    )
                    summary = summary.copy(addedCount = summary.addedCount + 1)
                }

                is PortfolioImportChange.Reactivate -> {
                    validateSnapshot(change.valueEur, change.costBasisEur, change.trackedShares)
                    val current = next[change.itemId]
                        ?: throw IllegalArgumentException("Position '${change.itemId}' ist nicht vorhanden.")
                    require(current.lifecycleStatus == PortfolioLifecycleStatus.SOLD_MARKED) {
                        "Position '${change.itemId}' ist nicht als verkauft markiert."
                    }
                    next[change.itemId] = current.copy(
                        snapshotValueEur = change.valueEur,
                        snapshotCostBasisEur = change.costBasisEur ?: current.snapshotCostBasisEur,
                        trackedShares = change.trackedShares ?: current.trackedShares,
                        lifecycleStatus = PortfolioLifecycleStatus.ACTIVE,
                        snapshotCapturedAt = capturedAt
                    )
                    summary = summary.copy(reactivatedCount = summary.reactivatedCount + 1)
                }

                is PortfolioImportChange.MissingPosition -> {
                    val current = next[change.itemId]
                        ?: throw IllegalArgumentException("Position '${change.itemId}' ist nicht vorhanden.")
                    when (change.decision) {
                        MissingPositionDecision.KEEP -> {
                            summary = summary.copy(keptCount = summary.keptCount + 1)
                        }
                        MissingPositionDecision.MARK_SOLD -> {
                            next[change.itemId] = current.copy(
                                lifecycleStatus = PortfolioLifecycleStatus.SOLD_MARKED
                            )
                            summary = summary.copy(soldCount = summary.soldCount + 1)
                        }
                        MissingPositionDecision.DELETE -> {
                            next.remove(change.itemId)
                            summary = summary.copy(deletedCount = summary.deletedCount + 1)
                        }
                    }
                }
            }
        }

        return PortfolioImportResult(
            positions = next.toMap(),
            customAssets = nextCustomAssets.toList(),
            summary = summary
        )
    }

    private fun validateSnapshot(valueEur: Double, costBasisEur: Double?, trackedShares: Double?) {
        require(valueEur.isFinite() && valueEur >= 0.0) { "Depotwert ist ungültig." }
        require(costBasisEur == null || (costBasisEur.isFinite() && costBasisEur >= 0.0)) {
            "Einstandswert ist ungültig."
        }
        require(trackedShares == null || (trackedShares.isFinite() && trackedShares > 0.0)) {
            "Stückzahl ist ungültig."
        }
    }
}
