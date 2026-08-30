package de.tobias.investmentradar

data class DashboardData(
    val generatedAt: String,
    val marketLight: String,
    val budget: Int,
    val topPickId: String,
    val items: List<InvestmentItem>,
    val alerts: List<SignalAlert>
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
    val currency: String,
    val percentChange: Double?,
    val marketOpen: Boolean?
)

data class SignalAlert(
    val id: String,
    val itemId: String,
    val level: String,
    val title: String,
    val message: String,
    val createdAt: String
)
