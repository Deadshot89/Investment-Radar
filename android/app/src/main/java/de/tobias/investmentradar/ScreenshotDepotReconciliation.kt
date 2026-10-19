package de.tobias.investmentradar

import kotlin.math.abs

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

enum class ScreenshotReviewStatus {
    READY,
    NEEDS_REVIEW,
    NEW_POSITION,
    NOT_FOUND,
    CONFLICT,
    INVALID,
    SOLD_REAPPEARED
}

data class ScreenshotReviewItem(
    val observation: ScreenshotHoldingObservation,
    val status: ScreenshotReviewStatus,
    val reason: String?
)

data class TradeRepublicScreenshotReviewItem(
    val itemId: String?,
    val observation: TradeRepublicScreenshotObservation?,
    val status: ScreenshotReviewStatus,
    val reason: String? = null,
    val candidateItemIds: List<String> = emptyList(),
    val sourceIndices: Set<Int> = emptySet()
)

object ScreenshotDepotReconciliation {
    private const val MONEY_EPSILON = 0.011
    private const val PERCENT_EPSILON = 0.011
    private const val SHARES_EPSILON = 0.000001

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
     * Reconciles all observations from one import session. Equal overlaps are merged. Conflicting
     * overlaps are surfaced for review and are never resolved by last-write-wins behavior.
     */
    fun reconcileTradeRepublic(
        observations: List<TradeRepublicScreenshotObservation>,
        builtInItems: List<InvestmentItem>,
        customItems: List<CustomInvestment>,
        existing: Map<String, PortfolioPosition>
    ): List<TradeRepublicScreenshotReviewItem> {
        val immediate = mutableListOf<TradeRepublicScreenshotReviewItem>()
        val exactGroups = linkedMapOf<String, MutableList<TradeRepublicScreenshotObservation>>()
        val potentiallyObservedIds = mutableSetOf<String>()

        observations.forEach { observation ->
            val match = PortfolioAssetMatcher.match(
                observedName = observation.name,
                builtInItems = builtInItems,
                customItems = customItems
            )
            when (match) {
                is AssetMatch.Exact -> potentiallyObservedIds += match.itemId
                is AssetMatch.Review -> potentiallyObservedIds += match.candidateItemIds
                AssetMatch.Unknown -> Unit
            }

            val invalidReason = invalidReason(observation)
            if (invalidReason != null) {
                immediate += TradeRepublicScreenshotReviewItem(
                    itemId = (match as? AssetMatch.Exact)?.itemId,
                    observation = observation,
                    status = ScreenshotReviewStatus.INVALID,
                    reason = invalidReason,
                    candidateItemIds = (match as? AssetMatch.Review)?.candidateItemIds.orEmpty(),
                    sourceIndices = setOf(observation.sourceIndex)
                )
                return@forEach
            }

            when (match) {
                is AssetMatch.Exact -> exactGroups.getOrPut(match.itemId) { mutableListOf() } += observation
                is AssetMatch.Review -> immediate += TradeRepublicScreenshotReviewItem(
                    itemId = null,
                    observation = observation,
                    status = ScreenshotReviewStatus.NEEDS_REVIEW,
                    reason = "Zuordnung muss bestätigt werden",
                    candidateItemIds = match.candidateItemIds,
                    sourceIndices = setOf(observation.sourceIndex)
                )
                AssetMatch.Unknown -> immediate += TradeRepublicScreenshotReviewItem(
                    itemId = null,
                    observation = observation,
                    status = ScreenshotReviewStatus.NEW_POSITION,
                    reason = "Neue Position muss bestätigt oder zugeordnet werden",
                    sourceIndices = setOf(observation.sourceIndex)
                )
            }
        }

        val matched = exactGroups.map { (itemId, grouped) ->
            val sourceIndices = grouped.mapTo(linkedSetOf()) { it.sourceIndex }
            if (!areCompatible(grouped)) {
                TradeRepublicScreenshotReviewItem(
                    itemId = itemId,
                    observation = mergeCompatibleFields(grouped),
                    status = ScreenshotReviewStatus.CONFLICT,
                    reason = "Mehrere Screenshots enthalten unterschiedliche Werte für dieselbe Position",
                    sourceIndices = sourceIndices
                )
            } else {
                val merged = mergeCompatibleFields(grouped)
                val current = existing[itemId]
                val status = when {
                    current == null -> ScreenshotReviewStatus.NEW_POSITION
                    current.lifecycleStatus == PortfolioLifecycleStatus.SOLD_MARKED -> ScreenshotReviewStatus.SOLD_REAPPEARED
                    else -> ScreenshotReviewStatus.READY
                }
                val reason = when (status) {
                    ScreenshotReviewStatus.NEW_POSITION -> "Neue Position muss bestätigt werden"
                    ScreenshotReviewStatus.SOLD_REAPPEARED -> "Als verkauft markierte Position ist wieder auf Trade Republic sichtbar"
                    else -> null
                }
                TradeRepublicScreenshotReviewItem(
                    itemId = itemId,
                    observation = merged,
                    status = status,
                    reason = reason,
                    sourceIndices = sourceIndices
                )
            }
        }

        val notFound = existing.entries
            .asSequence()
            .filter { (itemId, position) ->
                itemId !in potentiallyObservedIds &&
                    position.lifecycleStatus == PortfolioLifecycleStatus.ACTIVE &&
                    position.isActiveHolding() &&
                    isTradeRepublicEligibleExistingPosition(
                        itemId = itemId,
                        position = position,
                        builtInItems = builtInItems,
                        customItems = customItems
                    )
            }
            .sortedBy { it.key }
            .map { (itemId, _) ->
                TradeRepublicScreenshotReviewItem(
                    itemId = itemId,
                    observation = null,
                    status = ScreenshotReviewStatus.NOT_FOUND,
                    reason = "Auf den aktuellen Trade-Republic-Screenshots nicht gefunden"
                )
            }
            .toList()

        return (matched + immediate + notFound).sortedWith(
            compareBy<TradeRepublicScreenshotReviewItem>({ statusOrder(it.status) }, { it.itemId.orEmpty() }, { it.observation?.sourceIndex ?: Int.MAX_VALUE })
        )
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

    private fun invalidReason(observation: TradeRepublicScreenshotObservation): String? = when {
        observation.name.isBlank() -> "Wertpapiername fehlt"
        observation.valueEur == null || !observation.valueEur.isFinite() || observation.valueEur < 0.0 ->
            "Depotwert fehlt oder ist ungültig"
        observation.profitLossEur != null && !observation.profitLossEur.isFinite() ->
            "Gewinn/Verlust ist ungültig"
        observation.profitLossPct != null && !observation.profitLossPct.isFinite() ->
            "Performance ist ungültig"
        observation.shares != null && (!observation.shares.isFinite() || observation.shares <= 0.0) ->
            "Stückzahl ist ungültig"
        observation.derivedCostBasisEur != null && (!observation.derivedCostBasisEur.isFinite() || observation.derivedCostBasisEur < 0.0) ->
            "Einstandswert ist ungültig"
        observation.sourceIndex < 0 -> "Screenshot-Quelle ist ungültig"
        else -> null
    }

    private fun areCompatible(observations: List<TradeRepublicScreenshotObservation>): Boolean {
        if (observations.size <= 1) return true
        // Compare every present value. If the first screenshot omits a field, comparing only
        // against that null value would accept contradictory data in later screenshots.
        return compatibleObservedValues(observations.map { it.valueEur }, MONEY_EPSILON, required = true) &&
            compatibleObservedValues(observations.map { it.profitLossEur }, MONEY_EPSILON) &&
            compatibleObservedValues(observations.map { it.profitLossPct }, PERCENT_EPSILON) &&
            compatibleObservedValues(observations.map { it.shares }, SHARES_EPSILON) &&
            compatibleObservedValues(observations.map { it.derivedCostBasisEur }, MONEY_EPSILON)
    }

    private fun compatibleObservedValues(
        values: List<Double?>,
        epsilon: Double,
        required: Boolean = false
    ): Boolean {
        if (required && values.any { it == null }) return false
        val observed = values.filterNotNull()
        if (observed.size <= 1) return true
        val first = observed.first()
        return observed.drop(1).all { abs(it - first) <= epsilon }
    }

    private fun mergeCompatibleFields(
        observations: List<TradeRepublicScreenshotObservation>
    ): TradeRepublicScreenshotObservation {
        val first = observations.first()
        return first.copy(
            valueEur = observations.firstNotNullOfOrNull { it.valueEur },
            profitLossEur = observations.firstNotNullOfOrNull { it.profitLossEur },
            profitLossPct = observations.firstNotNullOfOrNull { it.profitLossPct },
            shares = observations.firstNotNullOfOrNull { it.shares },
            derivedCostBasisEur = observations.firstNotNullOfOrNull { it.derivedCostBasisEur },
            sourceIndex = observations.minOf { it.sourceIndex }
        )
    }

    private fun isTradeRepublicEligibleExistingPosition(
        itemId: String,
        position: PortfolioPosition,
        builtInItems: List<InvestmentItem>,
        customItems: List<CustomInvestment>
    ): Boolean {
        builtInItems.firstOrNull { it.id == itemId }?.let { item ->
            return PortfolioAssetMatcher.isTradeRepublicCandidate(item) && item.tradeRepublicName.isNotBlank()
        }
        customItems.firstOrNull { it.id == itemId }?.let { item ->
            return PortfolioAssetMatcher.isTradeRepublicCandidate(item) &&
                (item.tradeRepublicUrl.isNotBlank() || !position.snapshotCapturedAt.isNullOrBlank())
        }
        return false
    }

    private fun statusOrder(status: ScreenshotReviewStatus): Int = when (status) {
        ScreenshotReviewStatus.CONFLICT -> 0
        ScreenshotReviewStatus.INVALID -> 1
        ScreenshotReviewStatus.NEEDS_REVIEW -> 2
        ScreenshotReviewStatus.SOLD_REAPPEARED -> 3
        ScreenshotReviewStatus.NEW_POSITION -> 4
        ScreenshotReviewStatus.NOT_FOUND -> 5
        ScreenshotReviewStatus.READY -> 6
    }
}
