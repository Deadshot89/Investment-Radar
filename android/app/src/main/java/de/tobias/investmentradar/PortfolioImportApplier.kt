package de.tobias.investmentradar

enum class MissingPositionDecision {
    KEEP,
    MARK_SOLD,
    DELETE
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
        val trackedShares: Double? = null
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
    ): PortfolioImportResult {
        require(capturedAt.isNotBlank()) { "Snapshot-Zeitpunkt fehlt." }
        val next = existing.toMutableMap()
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

        return PortfolioImportResult(next.toMap(), summary)
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
