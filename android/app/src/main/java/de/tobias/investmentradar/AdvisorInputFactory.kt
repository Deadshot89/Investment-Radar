package de.tobias.investmentradar

object AdvisorInputFactory {
    fun from(
        item: InvestmentItem,
        forecast: InvestmentForecast,
        freshness: DataFreshnessSummary
    ): AdvisorInput {
        val type = instrumentType(item.type)
        val forecastRanges = AdvisorForecastPolicy.from(forecast, freshness)
        val backendForecastBlocked = item.forecast?.quality.equals("NICHT_BELASTBAR", ignoreCase = true) ||
            item.dataQuality?.missingBlocks.orEmpty().any { it.equals("forecast", ignoreCase = true) }
        val effectiveCoverage = if (backendForecastBlocked) {
            item.dataQuality?.forecastInputCoverage ?: 0
        } else {
            freshness.coverage ?: item.coverage ?: forecast.coveragePct ?: 0
        }

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
            forecastRanges = forecastRanges,
            coveragePct = effectiveCoverage.coerceIn(0, 100),
            isFresh = freshness.status != FreshnessStatus.STALE && !backendForecastBlocked
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
