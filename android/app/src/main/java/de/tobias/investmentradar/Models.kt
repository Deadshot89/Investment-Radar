package de.tobias.investmentradar

data class DashboardData(
    val generatedAt: String,
    val marketLight: String,
    val budget: Int,
    val topPickId: String,
    val items: List<InvestmentItem>,
    val alerts: List<SignalAlert>
)

data class MomentumSnapshot(
    val d1: Double? = null,
    val m1: Double? = null,
    val m3: Double? = null,
    val m6: Double? = null,
    val m12: Double? = null,
    val score: Int? = null,
    val coveragePct: Int? = null,
    val stale: Boolean = false,
    val source: String = "",
    val asOf: String? = null,
    val error: String? = null
)

data class FundamentalSnapshot(
    val pe: Double? = null,
    val priceToSales: Double? = null,
    val evToEbitda: Double? = null,
    val freeCashFlowYield: Double? = null,
    val revenueGrowth: Double? = null,
    val epsGrowth: Double? = null,
    val operatingMargin: Double? = null,
    val netMargin: Double? = null,
    val roe: Double? = null,
    val roic: Double? = null,
    val debtToEquity: Double? = null,
    val coveragePct: Int? = null,
    val stale: Boolean = false,
    val source: String = "",
    val asOf: String? = null,
    val error: String? = null
)

data class InvestmentItem(
    val id: String,
    val type: String,
    val name: String,
    val ticker: String,
    val isin: String,
    val tradeRepublicName: String,
    val status: String,
    val allocation: Int,
    val risk: Int,
    val price: Double?,
    val priceEur: Double?,
    val currency: String,
    val fxRateToEur: Double?,
    val fxSource: String,
    val fxDelayed: Boolean,
    val fxAsOf: String?,
    val percentChange: Double?,
    val marketOpen: Boolean?,
    val dataSource: String,
    val dataDelayed: Boolean,
    val dataError: String?,
    val scoreTotal: Int? = null,
    val scoreQuality: Int? = null,
    val scoreValuation: Int? = null,
    val scoreGrowth: Int? = null,
    val scoreMomentum: Int? = null,
    val scoreRisk: Int? = null,
    val coverage: Int? = null,
    val recommendation: String = "",
    val recommendationReasons: List<String> = emptyList(),
    val momentum: MomentumSnapshot? = null,
    val fundamentals: FundamentalSnapshot? = null,
    val analysisAsOf: String? = null,
    val portfolioOnly: Boolean = false
)

data class SignalAlert(
    val id: String,
    val itemId: String,
    val level: String,
    val title: String,
    val message: String,
    val createdAt: String
)