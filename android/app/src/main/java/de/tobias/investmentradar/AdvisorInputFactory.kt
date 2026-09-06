package de.tobias.investmentradar

object AdvisorInputFactory {
    fun from(
        item: InvestmentItem,
        forecast: InvestmentForecast,
        freshness: DataFreshnessSummary
    ): AdvisorInput {
        val type = instrumentType(item.type)
        val forecast12m = forecast.points
            .firstOrNull { it.horizon == ForecastHorizon.TWELVE_MONTHS }
            ?.expectedChangePct

        return AdvisorInput(
            instrumentId = item.id,
            instrumentType = type,
            quality = if (type == AdvisorInstrumentType.STOCK) item.scoreQuality else null,
            valuation = when (type) {
                AdvisorInstrumentType.STOCK,
                AdvisorInstrumentType.ETF -> item.scoreValuation
                AdvisorInstrumentType.FIXED_INCOME -> null
            },
            growth = if (type == AdvisorInstrumentType.STOCK) item.scoreGrowth else null,
            momentum = item.scoreMomentum,
            riskScore = item.scoreRisk,
            forecast12mPct = forecast12m,
            coveragePct = freshness.coverage ?: item.coverage ?: forecast.coveragePct ?: 0,
            isFresh = freshness.status != FreshnessStatus.STALE
        )
    }

    private fun instrumentType(rawType: String): AdvisorInstrumentType {
        val normalized = rawType.trim().uppercase()
        return when {
            normalized == "ETF" -> AdvisorInstrumentType.ETF
            normalized.contains("FIXED") ||
                normalized.contains("BOND") ||
                normalized.contains("ANLEIHE") -> AdvisorInstrumentType.FIXED_INCOME
            else -> AdvisorInstrumentType.STOCK
        }
    }
}
