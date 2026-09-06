package de.tobias.investmentradar

object PortfolioAdvisorCandidateFactory {
    fun create(
        item: InvestmentItem,
        isHolding: Boolean,
        currentValueEur: Double?,
        monthlySavingsEur: Int,
        freshness: DataFreshnessSummary
    ): PortfolioAdvisorCandidate {
        val forecast = ForecastEngine.forecast(item)
        val input = AdvisorInputFactory.from(item, forecast, freshness)
        val advisor = AdvisorEngine.evaluate(input)
        val action = when {
            !advisor.reliable -> PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG
            isHolding -> when (advisor.signal) {
                AdvisorSignal.NACHKAUFEN -> PortfolioAdvisorAction.NACHKAUFEN
                AdvisorSignal.HALTEN -> PortfolioAdvisorAction.HALTEN
                AdvisorSignal.REDUZIEREN -> PortfolioAdvisorAction.REDUZIEREN
                AdvisorSignal.VERKAUFEN -> PortfolioAdvisorAction.VERKAUFEN
                AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG
            }
            advisor.signal == AdvisorSignal.NACHKAUFEN -> PortfolioAdvisorAction.NEU_AUFNEHMEN
            else -> PortfolioAdvisorAction.NICHT_AUFNEHMEN
        }

        return PortfolioAdvisorCandidate(
            itemId = item.id,
            isHolding = isHolding,
            action = action,
            advisor = advisor,
            currentValueEur = currentValueEur?.takeIf { it.isFinite() && it >= 0.0 },
            monthlySavingsEur = monthlySavingsEur.coerceAtLeast(0)
        )
    }
}
