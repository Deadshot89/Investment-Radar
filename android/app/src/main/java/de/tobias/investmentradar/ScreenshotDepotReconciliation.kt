package de.tobias.investmentradar

/**
 * A screenshot is a valuation snapshot, never proof of a purchase, sale or cash movement.
 * Parsing/OCR must supply observations; only explicitly confirmed matches can be applied.
 */
data class ScreenshotHoldingObservation(
    val itemId: String?,
    val valueEur: Double?,
    val costBasisEur: Double? = null,
    val shares: Double? = null,
    val confirmedByUser: Boolean = false
)

enum class ScreenshotReviewStatus { READY, NEEDS_REVIEW, INVALID }

data class ScreenshotReviewItem(
    val observation: ScreenshotHoldingObservation,
    val status: ScreenshotReviewStatus,
    val reason: String?
)

object ScreenshotDepotReconciliation {
    fun review(
        observations: List<ScreenshotHoldingObservation>,
        existing: Map<String, PortfolioPosition>
    ): List<ScreenshotReviewItem> {
        val duplicateIds = observations.mapNotNull { it.itemId?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return observations.map { observation ->
            val id = observation.itemId?.trim().orEmpty()
            val value = observation.valueEur
            val cost = observation.costBasisEur
            val shares = observation.shares
            val reason = when {
                id.isBlank() -> "Wertpapier nicht eindeutig zugeordnet"
                id in duplicateIds -> "Wertpapier mehrfach im Screenshot"
                value == null || !value.isFinite() || value < 0.0 -> "Depotwert fehlt oder ist ungültig"
                cost != null && (!cost.isFinite() || cost < 0.0) -> "Einstandswert ungültig"
                shares != null && (!shares.isFinite() || shares <= 0.0) -> "Stückzahl ungültig"
                id !in existing -> "Neue Position muss einzeln geprüft werden"
                !observation.confirmedByUser -> "Bestätigung ausstehend"
                else -> null
            }
            ScreenshotReviewItem(
                observation,
                when {
                    reason == null -> ScreenshotReviewStatus.READY
                    id.isBlank() || id in duplicateIds || value == null ||
                        !value.isFinite() || value < 0.0 ||
                        (cost != null && (!cost.isFinite() || cost < 0.0)) ||
                        (shares != null && (!shares.isFinite() || shares <= 0.0)) ->
                        ScreenshotReviewStatus.INVALID
                    else -> ScreenshotReviewStatus.NEEDS_REVIEW
                },
                reason
            )
        }
    }

    /**
     * Pure preview: never writes storage, budget, sales, purchases or other positions.
     * Existing ledger cost basis and tracked shares are deliberately preserved.
     */
    fun previewConfirmed(
        reviewed: List<ScreenshotReviewItem>,
        existing: Map<String, PortfolioPosition>
    ): Map<String, PortfolioPosition> {
        val updated = existing.toMutableMap()
        reviewed.filter { it.status == ScreenshotReviewStatus.READY }.forEach { item ->
            val id = item.observation.itemId!!.trim()
            val current = updated[id] ?: return@forEach
            updated[id] = current.copy(snapshotValueEur = item.observation.valueEur)
        }
        return updated
    }
}
