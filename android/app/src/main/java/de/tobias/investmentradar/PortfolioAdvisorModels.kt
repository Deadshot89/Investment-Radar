package de.tobias.investmentradar

enum class PortfolioAdvisorAction {
    NACHKAUFEN,
    HALTEN,
    REDUZIEREN,
    VERKAUFEN,
    NEU_AUFNEHMEN,
    NICHT_AUFNEHMEN,
    KEINE_BELASTBARE_BEWERTUNG
}

data class PortfolioAdvisorCandidate(
    val itemId: String,
    val isHolding: Boolean,
    val action: PortfolioAdvisorAction,
    val advisor: AdvisorResult,
    val currentValueEur: Double?,
    val monthlySavingsEur: Int
)
