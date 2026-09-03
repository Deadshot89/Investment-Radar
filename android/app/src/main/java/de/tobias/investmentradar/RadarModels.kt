package de.tobias.investmentradar

data class RadarQuery(
    val query: String = "",
    val type: String? = null,
    val region: String? = null,
    val country: String? = null,
    val sector: String? = null,
    val recommendation: String? = null,
    val qualityTier: String? = null,
    val riskMax: Int? = null,
    val sort: String = "SCORE_DESC",
    val page: Int = 1,
    val pageSize: Int = 40,
    val tradeRepublicVerified: Boolean = false
)

data class RadarFacet(val value: String, val count: Int)

data class RadarFacets(
    val types: List<RadarFacet> = emptyList(),
    val regions: List<RadarFacet> = emptyList(),
    val countries: List<RadarFacet> = emptyList(),
    val sectors: List<RadarFacet> = emptyList(),
    val qualityTiers: List<RadarFacet> = emptyList()
)

data class RadarSummaryItem(
    val id: String,
    val type: String,
    val name: String,
    val ticker: String,
    val isin: String,
    val tradeRepublicName: String,
    val region: String,
    val country: String,
    val sector: String,
    val industry: String,
    val marketCapBucket: String,
    val tradeRepublicEligible: Boolean?,
    val dataQualityTier: String,
    val risk: Int,
    val price: Double?,
    val priceEur: Double?,
    val currency: String,
    val percentChange: Double?,
    val scoreTotal: Int?,
    val scoreQuality: Int?,
    val scoreValuation: Int?,
    val scoreGrowth: Int?,
    val scoreMomentum: Int?,
    val scoreRisk: Int?,
    val coverage: Int?,
    val recommendation: String,
    val recommendationReasons: List<String>,
    val purchaseEligible: Boolean,
    val dataSource: String,
    val dataDelayed: Boolean,
    val dataError: String?,
    val analysisAsOf: String?,
    val momentum: MomentumSnapshot? = null,
    val fundamentals: FundamentalSnapshot? = null
) {
    fun asInvestmentItem(): InvestmentItem = InvestmentItem(
        id = id,
        type = type,
        name = name,
        ticker = ticker,
        isin = isin,
        tradeRepublicName = tradeRepublicName,
        status = when (recommendation) {
            "BUY" -> "KAUFEN"
            "NO_BUY" -> "NICHT KAUFEN"
            "REVIEW" -> "PRÜFEN"
            else -> "BEOBACHTEN"
        },
        allocation = 0,
        risk = risk,
        price = price,
        priceEur = priceEur,
        currency = currency,
        fxRateToEur = null,
        fxSource = "",
        fxDelayed = false,
        fxAsOf = null,
        percentChange = percentChange,
        marketOpen = null,
        dataSource = dataSource,
        dataDelayed = dataDelayed,
        dataError = dataError,
        scoreTotal = scoreTotal,
        scoreQuality = scoreQuality,
        scoreValuation = scoreValuation,
        scoreGrowth = scoreGrowth,
        scoreMomentum = scoreMomentum,
        scoreRisk = scoreRisk,
        coverage = coverage,
        recommendation = recommendation,
        recommendationReasons = recommendationReasons,
        momentum = momentum,
        fundamentals = fundamentals,
        analysisAsOf = analysisAsOf,
        portfolioOnly = false
    )
}

data class RadarPage(
    val generatedAt: String,
    val total: Int,
    val universeTotal: Int,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean,
    val items: List<RadarSummaryItem>,
    val facets: RadarFacets,
    val tradeRepublicVerifiedCount: Int,
    val tradeRepublicUnverifiedCount: Int
)
