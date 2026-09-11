package de.tobias.investmentradar

enum class TradeSide { BUY, SELL }
enum class TradeSource { MANUAL, SAVINGS_PLAN, SPARE_CHANGE, RECOMMENDATION }
enum class CashEntryType { MONTHLY_DEPOSIT, EXTRA_DEPOSIT, ADJUSTMENT }

data class InvestmentTrade(
    val id: String,
    val itemId: String,
    val isin: String,
    val side: TradeSide,
    val shares: Double,
    val priceEur: Double,
    val amountEur: Double,
    val date: String,
    val source: TradeSource
) {
    val positionKey: String get() = isin.trim().uppercase().ifBlank { itemId }
}

data class CashEntry(
    val id: String,
    val type: CashEntryType,
    val amountEur: Double,
    val date: String,
    val note: String = ""
)

data class BudgetReservation(
    val id: String,
    val itemId: String,
    val amountEur: Double
)

data class LedgerPosition(
    val key: String,
    val itemId: String,
    val shares: Double,
    val costBasisEur: Double,
    val averageBuyInEur: Double,
    val realizedProfitLossEur: Double
)

data class BudgetSummary(
    val monthlyDepositsEur: Double,
    val extraDepositsEur: Double,
    val availableEur: Double,
    val reservedEur: Double,
    val investedEur: Double,
    val realizedProfitLossEur: Double
)

object InvestmentLedgerEngine {
    fun summarize(
        cashEntries: List<CashEntry>,
        trades: List<InvestmentTrade>,
        reservations: List<BudgetReservation>
    ): BudgetSummary {
        val monthly = cashEntries.filter { it.type == CashEntryType.MONTHLY_DEPOSIT }.sumOf { it.amountEur }
        val extra = cashEntries.filter { it.type == CashEntryType.EXTRA_DEPOSIT }.sumOf { it.amountEur }
        val adjustments = cashEntries.filter { it.type == CashEntryType.ADJUSTMENT }.sumOf { it.amountEur }
        val buySpend = trades.filter { it.side == TradeSide.BUY }.sumOf { it.amountEur }
        val saleProceeds = trades.filter { it.side == TradeSide.SELL }.sumOf { it.amountEur }
        val reserved = reservations.sumOf { it.amountEur.coerceAtLeast(0.0) }

        val positions = trades
            .groupBy { it.positionKey }
            .values
            .map(::aggregatePosition)

        return BudgetSummary(
            monthlyDepositsEur = monthly,
            extraDepositsEur = extra,
            availableEur = monthly + extra + adjustments + saleProceeds - buySpend - reserved,
            reservedEur = reserved,
            investedEur = positions.sumOf { it.costBasisEur },
            realizedProfitLossEur = positions.sumOf { it.realizedProfitLossEur }
        )
    }

    fun aggregatePosition(trades: List<InvestmentTrade>): LedgerPosition {
        require(trades.isNotEmpty()) { "At least one trade is required" }
        val key = trades.first().positionKey
        require(trades.all { it.positionKey == key }) { "Trades must belong to one position" }

        var shares = 0.0
        var costBasis = 0.0
        var realized = 0.0

        trades.forEach { trade ->
            require(trade.shares >= 0.0 && trade.amountEur >= 0.0) { "Trade values must not be negative" }
            when (trade.side) {
                TradeSide.BUY -> {
                    shares += trade.shares
                    costBasis += trade.amountEur
                }
                TradeSide.SELL -> {
                    require(trade.shares <= shares + 1e-9) { "Sell exceeds held shares" }
                    val average = if (shares > 0.0) costBasis / shares else 0.0
                    val removedCost = average * trade.shares
                    shares -= trade.shares
                    costBasis -= removedCost
                    if (kotlin.math.abs(shares) < 1e-10) {
                        shares = 0.0
                        costBasis = 0.0
                    }
                    realized += trade.amountEur - removedCost
                }
            }
        }

        return LedgerPosition(
            key = key,
            itemId = trades.first().itemId,
            shares = shares,
            costBasisEur = costBasis,
            averageBuyInEur = if (shares > 0.0) costBasis / shares else 0.0,
            realizedProfitLossEur = realized
        )
    }
}
