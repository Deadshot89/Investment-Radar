package de.tobias.investmentradar

data class PortfolioPosition(
    val itemId: String,
    val investedAmount: Double = 0.0,
    val shares: Double = 0.0
) {
    fun averageBuyPrice(): Double? =
        if (shares > 0.0 && investedAmount >= 0.0) investedAmount / shares else null

    fun currentValue(currentPrice: Double?): Double? =
        if (shares > 0.0 && currentPrice != null && currentPrice >= 0.0) shares * currentPrice else null

    fun profitLoss(currentPrice: Double?): Double? =
        currentValue(currentPrice)?.minus(investedAmount)

    fun profitLossPercent(currentPrice: Double?): Double? =
        if (investedAmount > 0.0) profitLoss(currentPrice)?.div(investedAmount)?.times(100.0) else null
}
