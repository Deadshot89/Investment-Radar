package de.tobias.investmentradar

enum class AdvisorSignal {
    NACHKAUFEN,
    HALTEN,
    REDUZIEREN,
    VERKAUFEN,
    KEINE_BELASTBARE_BEWERTUNG
}

enum class AdvisorInstrumentType {
    STOCK,
    ETF,
    FIXED_INCOME
}

data class AdvisorForecastRange(
    val horizon: ForecastHorizon,
    val expectedChangePct: Double,
    val lowerChangePct: Double,
    val upperChangePct: Double,
    val lowerTargetPriceEur: Double?,
    val upperTargetPriceEur: Double?,
    val confidencePct: Int,
    val reliable: Boolean,
    val reasons: List<String>
)

data class AdvisorInput(
    val instrumentId: String,
    val instrumentType: AdvisorInstrumentType,
    val quality: Int?,
    val valuation: Int?,
    val growth: Int?,
    val momentum: Int?,
    val riskScore: Int?,
    val forecastRanges: List<AdvisorForecastRange>,
    val coveragePct: Int,
    val isFresh: Boolean
)

data class AdvisorResult(
    val instrumentId: String,
    val signal: AdvisorSignal,
    val score: Int?,
    val reliable: Boolean,
    val reasons: List<String>,
    val risks: List<String>,
    val confidencePct: Int = 0,
    val timingFactor: Double = 1.0,
    val pendingWorseSignal: AdvisorSignal? = null,
    val pendingWorseCount: Int = 0
)
