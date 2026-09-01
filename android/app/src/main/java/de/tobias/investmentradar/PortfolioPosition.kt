package de.tobias.investmentradar

data class PortfolioPurchase(
    val id: String,
    val date: String,
    val investedAmount: Double,
    val shares: Double
) {
    fun buyPrice(): Double? =
        if (shares > 0.0 && investedAmount >= 0.0) investedAmount / shares else null
}

data class PortfolioPosition(
    val itemId: String,
    val purchases: List<PortfolioPurchase> = emptyList()
) {
    constructor(itemId: String, investedAmount: Double, shares: Double) : this(
        itemId = itemId,
        purchases = if (investedAmount > 0.0 || shares > 0.0) {
            listOf(
                PortfolioPurchase(
                    id = "legacy-runtime-$itemId",
                    date = "",
                    investedAmount = investedAmount.coerceAtLeast(0.0),
                    shares = shares.coerceAtLeast(0.0)
                )
            )
        } else {
            emptyList()
        }
    )

    val investedAmount: Double
        get() = purchases.sumOf { it.investedAmount.coerceAtLeast(0.0) }

    val shares: Double
        get() = purchases.sumOf { it.shares.coerceAtLeast(0.0) }

    fun averageBuyPrice(): Double? =
        if (shares > 0.0 && investedAmount >= 0.0) investedAmount / shares else null

    fun currentValue(currentPrice: Double?): Double? =
        if (shares > 0.0 && currentPrice != null && currentPrice >= 0.0) shares * currentPrice else null

    fun profitLoss(currentPrice: Double?): Double? =
        currentValue(currentPrice)?.minus(investedAmount)

    fun profitLossPercent(currentPrice: Double?): Double? =
        if (investedAmount > 0.0) profitLoss(currentPrice)?.div(investedAmount)?.times(100.0) else null

    fun upsertPurchase(purchase: PortfolioPurchase): PortfolioPosition {
        val normalized = purchase.copy(
            investedAmount = purchase.investedAmount.coerceAtLeast(0.0),
            shares = purchase.shares.coerceAtLeast(0.0)
        )
        val existingIndex = purchases.indexOfFirst { it.id == normalized.id }
        val next = if (existingIndex >= 0) {
            purchases.toMutableList().apply { set(existingIndex, normalized) }
        } else {
            purchases + normalized
        }
        return copy(purchases = next)
    }

    fun removePurchase(purchaseId: String): PortfolioPosition =
        copy(purchases = purchases.filterNot { it.id == purchaseId })
}
