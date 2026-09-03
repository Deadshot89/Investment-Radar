package de.tobias.investmentradar

import kotlin.math.abs

data class PortfolioPurchase(
    val id: String,
    val date: String,
    val investedAmount: Double,
    val shares: Double
) {
    fun buyPrice(): Double? =
        if (shares > 0.0 && investedAmount >= 0.0) investedAmount / shares else null
}

data class PortfolioSale(
    val id: String,
    val date: String,
    val proceeds: Double,
    val shares: Double
) {
    fun salePrice(): Double? =
        if (shares > 0.0 && proceeds >= 0.0) proceeds / shares else null
}

data class PortfolioLedgerSummary(
    val remainingShares: Double,
    val remainingCostBasis: Double,
    val realizedProfitLoss: Double,
    val totalPurchaseAmount: Double,
    val totalSaleProceeds: Double,
    val valid: Boolean
)

data class PortfolioPosition(
    val itemId: String,
    val snapshotValueEur: Double? = null,
    val purchases: List<PortfolioPurchase> = emptyList(),
    val sales: List<PortfolioSale> = emptyList()
) {
    constructor(itemId: String, investedAmount: Double, shares: Double) : this(
        itemId = itemId,
        snapshotValueEur = null,
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
        },
        sales = emptyList()
    )

    private sealed interface LedgerEvent {
        val date: String
        val id: String

        data class Buy(val purchase: PortfolioPurchase) : LedgerEvent {
            override val date: String get() = purchase.date
            override val id: String get() = purchase.id
        }

        data class Sell(val sale: PortfolioSale) : LedgerEvent {
            override val date: String get() = sale.date
            override val id: String get() = sale.id
        }
    }

    private fun ledgerSummary(): PortfolioLedgerSummary {
        var heldShares = 0.0
        var costBasis = 0.0
        var realized = 0.0
        var totalPurchases = 0.0
        var totalSales = 0.0
        var valid = true

        val events = buildList<LedgerEvent> {
            purchases.forEach { add(LedgerEvent.Buy(it)) }
            sales.forEach { add(LedgerEvent.Sell(it)) }
        }.sortedWith(
            compareBy<LedgerEvent>({ transactionDateKey(it.date) }, { if (it is LedgerEvent.Buy) 0 else 1 }, { it.id })
        )

        events.forEach { event ->
            when (event) {
                is LedgerEvent.Buy -> {
                    val purchase = event.purchase
                    if (purchase.investedAmount < 0.0 || purchase.shares < 0.0 || !purchase.investedAmount.isFinite() || !purchase.shares.isFinite()) {
                        valid = false
                    } else {
                        totalPurchases += purchase.investedAmount
                        costBasis += purchase.investedAmount
                        heldShares += purchase.shares
                    }
                }

                is LedgerEvent.Sell -> {
                    val sale = event.sale
                    if (sale.proceeds < 0.0 || sale.shares <= 0.0 || !sale.proceeds.isFinite() || !sale.shares.isFinite() || sale.shares > heldShares + EPSILON) {
                        valid = false
                    } else {
                        val averageCost = if (heldShares > EPSILON) costBasis / heldShares else 0.0
                        val soldCostBasis = averageCost * sale.shares
                        heldShares = (heldShares - sale.shares).coerceAtLeast(0.0)
                        costBasis = (costBasis - soldCostBasis).coerceAtLeast(0.0)
                        if (heldShares <= EPSILON) {
                            heldShares = 0.0
                            costBasis = 0.0
                        }
                        totalSales += sale.proceeds
                        realized += sale.proceeds - soldCostBasis
                    }
                }
            }
        }

        return PortfolioLedgerSummary(
            remainingShares = heldShares,
            remainingCostBasis = costBasis,
            realizedProfitLoss = realized,
            totalPurchaseAmount = totalPurchases,
            totalSaleProceeds = totalSales,
            valid = valid
        )
    }

    val investedAmount: Double
        get() = ledgerSummary().remainingCostBasis

    val shares: Double
        get() = ledgerSummary().remainingShares

    val totalPurchasedAmount: Double
        get() = ledgerSummary().totalPurchaseAmount

    val totalSaleProceeds: Double
        get() = ledgerSummary().totalSaleProceeds

    fun averageBuyPrice(): Double? =
        if (shares > EPSILON && investedAmount >= 0.0) investedAmount / shares else null

    fun realizedProfitLoss(): Double = ledgerSummary().realizedProfitLoss

    fun isActiveHolding(): Boolean =
        shares > EPSILON || snapshotValueEur?.let { it.isFinite() && it > 0.0 } == true

    fun currentValue(currentPrice: Double?): Double? =
        snapshotValueEur?.takeIf { it.isFinite() && it >= 0.0 }
            ?: if (shares <= EPSILON && sales.isNotEmpty()) 0.0
            else if (shares > EPSILON && currentPrice != null && currentPrice >= 0.0) shares * currentPrice
            else null

    fun unrealizedProfitLoss(currentPrice: Double?): Double? =
        if (shares <= EPSILON && snapshotValueEur != null) null else currentValue(currentPrice)?.minus(investedAmount)

    fun unrealizedProfitLossPercent(currentPrice: Double?): Double? =
        if (shares <= EPSILON && snapshotValueEur != null) null
        else if (investedAmount > EPSILON) unrealizedProfitLoss(currentPrice)?.div(investedAmount)?.times(100.0) else null

    fun totalProfitLoss(currentPrice: Double?): Double? =
        if (shares <= EPSILON && snapshotValueEur != null) null else unrealizedProfitLoss(currentPrice)?.plus(realizedProfitLoss())

    fun totalProfitLossPercent(currentPrice: Double?): Double? {
        val totalBuys = totalPurchasedAmount
        return if (totalBuys > EPSILON) totalProfitLoss(currentPrice)?.div(totalBuys)?.times(100.0) else null
    }

    fun profitLoss(currentPrice: Double?): Double? = unrealizedProfitLoss(currentPrice)

    fun profitLossPercent(currentPrice: Double?): Double? = unrealizedProfitLossPercent(currentPrice)

    fun isLedgerValid(): Boolean = ledgerSummary().valid

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

    fun upsertPurchaseIfValid(purchase: PortfolioPurchase): PortfolioPosition? =
        upsertPurchase(purchase).takeIf { it.isLedgerValid() }

    fun removePurchase(purchaseId: String): PortfolioPosition =
        copy(purchases = purchases.filterNot { it.id == purchaseId })

    fun removePurchaseIfValid(purchaseId: String): PortfolioPosition? =
        removePurchase(purchaseId).takeIf { it.isLedgerValid() }

    fun upsertSale(sale: PortfolioSale): PortfolioPosition? {
        val normalized = sale.copy(
            proceeds = sale.proceeds.coerceAtLeast(0.0),
            shares = sale.shares.coerceAtLeast(0.0)
        )
        val existingIndex = sales.indexOfFirst { it.id == normalized.id }
        val next = if (existingIndex >= 0) {
            sales.toMutableList().apply { set(existingIndex, normalized) }
        } else {
            sales + normalized
        }
        return copy(sales = next).takeIf { it.isLedgerValid() }
    }

    fun removeSale(saleId: String): PortfolioPosition =
        copy(sales = sales.filterNot { it.id == saleId })

    companion object {
        private const val EPSILON = 0.0000001

        private fun transactionDateKey(value: String): Int {
            val raw = value.trim()
            if (raw.isBlank()) return 0
            val dot = Regex("^(\\d{2})\\.(\\d{2})\\.(\\d{4})$").matchEntire(raw)
            if (dot != null) {
                val (day, month, year) = dot.destructured
                return year.toInt() * 10000 + month.toInt() * 100 + day.toInt()
            }
            val iso = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(raw)
            if (iso != null) {
                val (year, month, day) = iso.destructured
                return year.toInt() * 10000 + month.toInt() * 100 + day.toInt()
            }
            return Int.MAX_VALUE
        }
    }
}
