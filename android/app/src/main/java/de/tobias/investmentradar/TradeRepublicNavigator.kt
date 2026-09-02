package de.tobias.investmentradar

object TradeRepublicNavigator {
    private const val STOCK_BASE_URL = "https://app.traderepublic.com/stocks/"
    const val BROWSE_URL = "https://app.traderepublic.com/browse/stock"
    private val ISIN_PATTERN = Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]")

    fun stockUrl(isin: String): String? {
        val normalized = isin.trim().uppercase()
        if (!ISIN_PATTERN.matches(normalized)) return null
        return STOCK_BASE_URL + normalized
    }
}
