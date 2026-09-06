package de.tobias.investmentradar

enum class MarketEventType {
    EARNINGS,
    GUIDANCE,
    PROFIT_WARNING,
    DIVIDEND,
    CAPITAL_ACTION,
    MANAGEMENT,
    M_AND_A,
    PRODUCT_OR_APPROVAL,
    REGULATORY_OR_LEGAL,
    CREDIT_RATING,
    MACRO,
    FUND_STRUCTURE
}

enum class MarketEventDirection {
    POSITIVE,
    NEGATIVE,
    MIXED,
    NEUTRAL
}

enum class MarketEventHorizon {
    SHORT,
    MEDIUM,
    LONG
}

enum class MarketEventVerification {
    VERIFIED_PRIMARY,
    VERIFIED_SECONDARY,
    UNVERIFIED
}

data class MarketEvent(
    val eventId: String,
    val instrumentId: String,
    val type: MarketEventType,
    val title: String,
    val summary: String,
    val eventAt: String,
    val publishedAt: String,
    val sourceName: String,
    val sourceUrl: String,
    val verification: MarketEventVerification,
    val direction: MarketEventDirection,
    val horizon: MarketEventHorizon,
    val materiality: Int,
    val confidencePct: Int,
    val fingerprint: String
)

data class MarketEventImpact(
    val event: MarketEvent,
    val scoreAdjustment: Int,
    val criticalThesisBreak: Boolean,
    val reasons: List<String>
)
