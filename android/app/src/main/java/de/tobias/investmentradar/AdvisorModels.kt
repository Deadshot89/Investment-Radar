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

data class AdvisorInput(
    val instrumentId: String,
    val instrumentType: AdvisorInstrumentType,
    val quality: Int?,
    val valuation: Int?,
    val growth: Int?,
    val momentum: Int?,
    val riskScore: Int?,
    val forecast12mPct: Double?,
    val coveragePct: Int,
    val isFresh: Boolean
)

data class AdvisorResult(
    val instrumentId: String,
    val signal: AdvisorSignal,
    val score: Int?,
    val reliable: Boolean,
    val reasons: List<String>,
    val risks: List<String>
)
