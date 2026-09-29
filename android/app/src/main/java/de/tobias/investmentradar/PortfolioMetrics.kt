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
    val calculablePositionCount: Int,
    val partialProfitLoss: Double?,
    val partialProfitLossPct: Double?,
    val performancePositionCount: Int,
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
            val hasUsablePrice: Boolean,
            val fixedIncomePrincipal: Double?
        )

        val drafts = positions.values.map { position ->
            val id = position.itemId
            val custom = customById[id]
            val fixedIncomePrincipal = custom
                ?.takeIf { it.type.equals("Festzins", ignoreCase = true) }
                ?.fixedPrincipalEur
                ?.takeIf { it.isFinite() && it > 0.0 }
            val marketPrice = itemById[id]?.priceEur?.takeIf { it.isFinite() && it > 0.0 }
            val manualPrice = custom?.manualPriceEur?.takeIf { it.isFinite() && it > 0.0 }
            val usablePrice = marketPrice ?: manualPrice
            val active = fixedIncomePrincipal != null || position.isActiveHolding()
            val currentValue = when {
                !active -> 0.0
                fixedIncomePrincipal != null -> fixedIncomePrincipal
                else -> position.currentValue(usablePrice)
            }
            val hasUsablePrice = !active || currentValue != null
            Draft(id, position, active, usablePrice, currentValue, hasUsablePrice, fixedIncomePrincipal)
        }

        val activeDrafts = drafts.filter { it.active }
        val missingPriceCount = activeDrafts.count { !it.hasUsablePrice }
        val currentValueComplete = missingPriceCount == 0
        val calculableCurrentValue = activeDrafts.sumOf { it.currentValue ?: 0.0 }
        val calculablePositionCount = activeDrafts.count { it.currentValue != null }
        val weightDenominator = calculableCurrentValue.takeIf { it > 0.0 }

        val metrics = drafts.map { draft ->
            val position = draft.position
            val fixedIncome = draft.fixedIncomePrincipal != null
            val rowCostBasis = when {
                !draft.active -> 0.0
                fixedIncome -> draft.fixedIncomePrincipal ?: 0.0
                else -> position.activeCostBasis
            }
            val realized = if (fixedIncome) 0.0 else position.realizedProfitLoss()
            val costBasisKnown = !draft.active || fixedIncome || position.performanceCostBasisKnown
            val unrealized = when {
                !draft.active -> 0.0
                fixedIncome -> 0.0
                !costBasisKnown -> null
                else -> position.unrealizedProfitLoss(draft.usablePrice)
            }
            val unrealizedPct = when {
                !draft.active || !costBasisKnown -> null
                fixedIncome -> 0.0
                else -> position.unrealizedProfitLossPercent(draft.usablePrice)
            }
            val total = when {
                !draft.active -> realized
                fixedIncome -> 0.0
                !costBasisKnown -> null
                else -> position.totalProfitLoss(draft.usablePrice)
            }
            val totalPct = when {
                !draft.active -> if (position.totalPurchasedAmount > 0.0) realized / position.totalPurchasedAmount * 100.0 else null
                fixedIncome -> 0.0
                !costBasisKnown -> null
                else -> position.totalProfitLossPercent(draft.usablePrice)
            }
            val weight = if (draft.active && draft.currentValue != null && weightDenominator != null) draft.currentValue / weightDenominator * 100.0 else null

            PortfolioPositionMetrics(
                itemId = draft.itemId,
                active = draft.active,
                investedCostBasis = rowCostBasis,
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

        val activeMetrics = metrics.filter { it.active }
        val investedCostBasis = activeMetrics.filter { it.costBasisKnown }.sumOf { it.investedCostBasis }
        val performanceDenominator = activeMetrics.filter { it.costBasisKnown }.sumOf { it.investedCostBasis }
        val performanceMetrics = activeMetrics.filter { it.currentValue != null && it.totalProfitLoss != null && it.costBasisKnown }
        val partialProfitLoss = performanceMetrics.takeIf { it.isNotEmpty() }?.sumOf { it.totalProfitLoss ?: 0.0 }
        val partialPerformanceCostBasis = performanceMetrics.sumOf { it.investedCostBasis }
        val partialProfitLossPct = if (partialProfitLoss != null && partialPerformanceCostBasis > 0.0) partialProfitLoss / partialPerformanceCostBasis * 100.0 else null
        val completeTotalProfitLoss = if (currentValueComplete && activeMetrics.all { it.totalProfitLoss != null }) metrics.sumOf { it.totalProfitLoss ?: 0.0 } else null
        val completeTotalProfitLossPct = if (completeTotalProfitLoss != null && performanceDenominator > 0.0) completeTotalProfitLoss / performanceDenominator * 100.0 else null
        val largest = metrics.asSequence().filter { it.active && it.weightPct != null }.maxByOrNull { it.weightPct ?: Double.NEGATIVE_INFINITY }

        return PortfolioMetricsSummary(
            investedCostBasis = investedCostBasis,
            calculableCurrentValue = calculableCurrentValue,
            currentValueComplete = currentValueComplete,
            missingPriceCount = missingPriceCount,
            calculablePositionCount = calculablePositionCount,
            partialProfitLoss = partialProfitLoss,
            partialProfitLossPct = partialProfitLossPct,
            performancePositionCount = performanceMetrics.size,
            totalProfitLoss = completeTotalProfitLoss,
            totalProfitLossPct = completeTotalProfitLossPct,
            heldPositionCount = activeDrafts.size,
            largestPositionId = largest?.itemId,
            largestWeightPct = largest?.weightPct,
            positions = metrics
        )
    }
}
