package de.tobias.investmentradar

data class PortfolioPositionMetrics(
    val itemId: String,
    val active: Boolean,
    val investedCostBasis: Double,
    val costBasisKnown: Boolean,
    val currentValue: Double?,
    val unrealizedProfitLoss: Double?,
    val unrealizedProfitLossPct: Double?,
    val realizedProfitLoss: Double,
    val totalProfitLoss: Double?,
    val totalProfitLossPct: Double?,
    val weightPct: Double?,
    val hasUsablePrice: Boolean
)

data class PortfolioMetricsSummary(
    val investedCostBasis: Double,
    val calculableCurrentValue: Double,
    val currentValueComplete: Boolean,
    val missingPriceCount: Int,
    val totalProfitLoss: Double?,
    val totalProfitLossPct: Double?,
    val heldPositionCount: Int,
    val largestPositionId: String?,
    val largestWeightPct: Double?,
    val positions: List<PortfolioPositionMetrics>
)

object PortfolioMetrics {
    fun calculate(
        items: List<InvestmentItem>,
        positions: Map<String, PortfolioPosition>,
        customItems: List<CustomInvestment>
    ): PortfolioMetricsSummary {
        val itemById = items.associateBy { it.id }
        val customById = customItems.associateBy { it.id }

        data class Draft(
            val itemId: String,
            val position: PortfolioPosition,
            val active: Boolean,
            val usablePrice: Double?,
            val currentValue: Double?,
            val hasUsablePrice: Boolean
        )

        val drafts = positions.values.map { position ->
            val id = position.itemId
            val marketPrice = itemById[id]?.priceEur?.takeIf { it.isFinite() && it > 0.0 }
            val manualPrice = customById[id]?.manualPriceEur?.takeIf { it.isFinite() && it > 0.0 }
            val usablePrice = marketPrice ?: manualPrice
            val active = position.isActiveHolding()
            val currentValue = if (active) {
                position.snapshotValueEur?.takeIf { it.isFinite() && it >= 0.0 }
                    ?: usablePrice?.let { position.shares * it }
            } else 0.0
            val hasUsablePrice = !active || position.snapshotValueEur != null || usablePrice != null
            Draft(id, position, active, usablePrice, currentValue, hasUsablePrice)
        }

        val activeDrafts = drafts.filter { it.active }
        val missingPriceCount = activeDrafts.count { !it.hasUsablePrice }
        val currentValueComplete = missingPriceCount == 0
        val calculableCurrentValue = activeDrafts.sumOf { it.currentValue ?: 0.0 }
        val weightDenominator = calculableCurrentValue.takeIf { it > 0.0 }

        val metrics = drafts.map { draft ->
            val position = draft.position
            val realized = position.realizedProfitLoss()
            val snapshotOnly = position.shares <= 0.0000001 && position.snapshotValueEur != null
            val costBasisKnown = !snapshotOnly
            val unrealized = when {
                !draft.active -> 0.0
                snapshotOnly -> null
                else -> draft.usablePrice?.let { position.unrealizedProfitLoss(it) }
            }
            val unrealizedPct = when {
                !draft.active || snapshotOnly -> null
                else -> draft.usablePrice?.let { position.unrealizedProfitLossPercent(it) }
            }
            val total = when {
                !draft.active -> realized
                snapshotOnly -> null
                else -> draft.usablePrice?.let { position.totalProfitLoss(it) }
            }
            val totalPct = when {
                !draft.active -> if (position.totalPurchasedAmount > 0.0) realized / position.totalPurchasedAmount * 100.0 else null
                snapshotOnly -> null
                else -> draft.usablePrice?.let { position.totalProfitLossPercent(it) }
            }
            val weight = if (draft.active && draft.currentValue != null && weightDenominator != null) draft.currentValue / weightDenominator * 100.0 else null

            PortfolioPositionMetrics(
                itemId = draft.itemId,
                active = draft.active,
                investedCostBasis = if (draft.active) position.investedAmount else 0.0,
                costBasisKnown = costBasisKnown,
                currentValue = draft.currentValue,
                unrealizedProfitLoss = unrealized,
                unrealizedProfitLossPct = unrealizedPct,
                realizedProfitLoss = realized,
                totalProfitLoss = total,
                totalProfitLossPct = totalPct,
                weightPct = weight,
                hasUsablePrice = draft.hasUsablePrice
            )
        }

        val investedCostBasis = activeDrafts.filterNot { it.position.shares <= 0.0000001 && it.position.snapshotValueEur != null }.sumOf { it.position.investedAmount }
        val totalPurchasedAmount = drafts.sumOf { it.position.totalPurchasedAmount }
        val activeMetrics = metrics.filter { it.active }
        val completeTotalProfitLoss = if (currentValueComplete && activeMetrics.all { it.totalProfitLoss != null }) metrics.sumOf { it.totalProfitLoss ?: 0.0 } else null
        val completeTotalProfitLossPct = if (completeTotalProfitLoss != null && totalPurchasedAmount > 0.0) completeTotalProfitLoss / totalPurchasedAmount * 100.0 else null
        val largest = metrics.asSequence().filter { it.active && it.weightPct != null }.maxByOrNull { it.weightPct ?: Double.NEGATIVE_INFINITY }

        return PortfolioMetricsSummary(
            investedCostBasis = investedCostBasis,
            calculableCurrentValue = calculableCurrentValue,
            currentValueComplete = currentValueComplete,
            missingPriceCount = missingPriceCount,
            totalProfitLoss = completeTotalProfitLoss,
            totalProfitLossPct = completeTotalProfitLossPct,
            heldPositionCount = activeDrafts.size,
            largestPositionId = largest?.itemId,
            largestWeightPct = largest?.weightPct,
            positions = metrics
        )
    }
}
