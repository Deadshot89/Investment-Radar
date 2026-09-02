package de.tobias.investmentradar

data class PortfolioPositionMetrics(
    val itemId: String,
    val active: Boolean,
    val investedCostBasis: Double,
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
    private const val ACTIVE_EPSILON = 0.0000001

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
            val active = position.shares > ACTIVE_EPSILON
            val hasUsablePrice = !active || usablePrice != null
            val currentValue = if (active) usablePrice?.let { position.shares * it } else 0.0

            Draft(
                itemId = id,
                position = position,
                active = active,
                usablePrice = usablePrice,
                currentValue = currentValue,
                hasUsablePrice = hasUsablePrice
            )
        }

        val activeDrafts = drafts.filter { it.active }
        val missingPriceCount = activeDrafts.count { !it.hasUsablePrice }
        val currentValueComplete = missingPriceCount == 0
        val calculableCurrentValue = activeDrafts.sumOf { it.currentValue ?: 0.0 }
        val weightDenominator = calculableCurrentValue.takeIf { it > 0.0 }

        val metrics = drafts.map { draft ->
            val position = draft.position
            val realized = position.realizedProfitLoss()
            val unrealized = if (draft.active) draft.usablePrice?.let { position.unrealizedProfitLoss(it) } else 0.0
            val unrealizedPct = if (draft.active) draft.usablePrice?.let { position.unrealizedProfitLossPercent(it) } else null
            val total = if (draft.active) draft.usablePrice?.let { position.totalProfitLoss(it) } else realized
            val totalPct = if (draft.active) draft.usablePrice?.let { position.totalProfitLossPercent(it) }
            else if (position.totalPurchasedAmount > 0.0) realized / position.totalPurchasedAmount * 100.0 else null
            val weight = if (draft.active && draft.currentValue != null && weightDenominator != null) {
                draft.currentValue / weightDenominator * 100.0
            } else {
                null
            }

            PortfolioPositionMetrics(
                itemId = draft.itemId,
                active = draft.active,
                investedCostBasis = if (draft.active) position.investedAmount else 0.0,
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

        val investedCostBasis = activeDrafts.sumOf { it.position.investedAmount }
        val totalPurchasedAmount = drafts.sumOf { it.position.totalPurchasedAmount }
        val completeTotalProfitLoss = if (currentValueComplete) {
            metrics.sumOf { it.totalProfitLoss ?: 0.0 }
        } else {
            null
        }
        val completeTotalProfitLossPct = if (completeTotalProfitLoss != null && totalPurchasedAmount > 0.0) {
            completeTotalProfitLoss / totalPurchasedAmount * 100.0
        } else {
            null
        }
        val largest = metrics
            .asSequence()
            .filter { it.active && it.weightPct != null }
            .maxByOrNull { it.weightPct ?: Double.NEGATIVE_INFINITY }

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
