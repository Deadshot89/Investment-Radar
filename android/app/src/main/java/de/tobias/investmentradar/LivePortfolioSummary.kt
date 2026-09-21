package de.tobias.investmentradar

data class LivePortfolioPosition(
    val itemId: String,
    val currentValue: Double,
    val weightPct: Double
)

data class LivePortfolioSummaryData(
    val currentValue: Double,
    val currentValueComplete: Boolean,
    val missingPriceCount: Int,
    val calculablePositionCount: Int,
    val missingValueItemIds: List<String>,
    val costBasis: Double,
    val profitLoss: Double,
    val profitLossPct: Double,
    val partialProfitLoss: Double?,
    val partialProfitLossPct: Double?,
    val performancePositionCount: Int,
    val performanceComplete: Boolean,
    val positionCount: Int,
    val largestPositionId: String?,
    val largestWeightPct: Double?,
    val positions: List<LivePortfolioPosition>
)

object LivePortfolioSummary {
    fun build(
        items: List<InvestmentItem>,
        positions: Map<String, PortfolioPosition>,
        customItems: List<CustomInvestment>
    ): LivePortfolioSummaryData {
        val metrics = PortfolioMetrics.calculate(items, positions, customItems)
        val performanceComplete = metrics.totalProfitLoss != null && metrics.totalProfitLossPct != null &&
            metrics.positions.filter { it.active }.all { it.costBasisKnown }

        return LivePortfolioSummaryData(
            currentValue = metrics.calculableCurrentValue,
            currentValueComplete = metrics.currentValueComplete,
            missingPriceCount = metrics.missingPriceCount,
            calculablePositionCount = metrics.calculablePositionCount,
            missingValueItemIds = metrics.positions.filter { it.active && it.currentValue == null }.map { it.itemId },
            costBasis = metrics.investedCostBasis,
            profitLoss = metrics.totalProfitLoss ?: 0.0,
            profitLossPct = metrics.totalProfitLossPct ?: 0.0,
            partialProfitLoss = metrics.partialProfitLoss,
            partialProfitLossPct = metrics.partialProfitLossPct,
            performancePositionCount = metrics.performancePositionCount,
            performanceComplete = performanceComplete,
            positionCount = metrics.heldPositionCount,
            largestPositionId = metrics.largestPositionId,
            largestWeightPct = metrics.largestWeightPct,
            positions = metrics.positions
                .asSequence()
                .filter { it.active && it.currentValue != null }
                .map {
                    LivePortfolioPosition(
                        itemId = it.itemId,
                        currentValue = it.currentValue ?: 0.0,
                        weightPct = it.weightPct ?: 0.0
                    )
                }
                .sortedByDescending { it.currentValue }
                .toList()
        )
    }
}
