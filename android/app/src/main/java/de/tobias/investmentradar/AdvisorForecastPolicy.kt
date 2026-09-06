package de.tobias.investmentradar

import kotlin.math.roundToInt

object AdvisorForecastPolicy {
    const val MIN_FORECAST_COVERAGE = 60

    fun from(
        forecast: InvestmentForecast,
        freshness: DataFreshnessSummary
    ): List<AdvisorForecastRange> {
        val coverage = (freshness.coverage ?: forecast.coveragePct ?: 0).coerceIn(0, 100)
        val freshEnough = freshness.status != FreshnessStatus.STALE
        val byHorizon = forecast.points.associateBy { it.horizon }

        return ForecastHorizon.entries.mapNotNull { horizon ->
            val point = byHorizon[horizon] ?: return@mapNotNull null
            val lowerTarget = point.bearTargetPriceEur?.takeIf { it.isFinite() && it >= 0.0 }
            val upperTarget = point.bullTargetPriceEur?.takeIf { it.isFinite() && it >= 0.0 }
            val targetRangeKnown = lowerTarget != null && upperTarget != null
            val scenarioWidth = (point.bullChangePct - point.bearChangePct).coerceAtLeast(0.0)
            val uncertaintyPenalty = (scenarioWidth / 2.0).roundToInt().coerceAtMost(40)
            val confidence = (coverage - uncertaintyPenalty).coerceIn(0, 100)
            val reliable = freshEnough &&
                coverage >= MIN_FORECAST_COVERAGE &&
                targetRangeKnown &&
                point.expectedChangePct.isFinite() &&
                point.bearChangePct.isFinite() &&
                point.bullChangePct.isFinite()

            AdvisorForecastRange(
                horizon = horizon,
                expectedChangePct = point.expectedChangePct,
                lowerChangePct = point.bearChangePct,
                upperChangePct = point.bullChangePct,
                lowerTargetPriceEur = if (targetRangeKnown) lowerTarget else null,
                upperTargetPriceEur = if (targetRangeKnown) upperTarget else null,
                confidencePct = confidence,
                reliable = reliable,
                reasons = point.reasons
            )
        }
    }
}
