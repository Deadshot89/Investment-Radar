package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorForecastPolicyTest {
    @Test
    fun returnsAllFourHorizonsInOrder() {
        val ranges = AdvisorForecastPolicy.from(
            forecast(priceAvailable = true, coverage = 85),
            freshness(FreshnessStatus.CURRENT, 85)
        )

        assertEquals(listOf(1, 3, 6, 12), ranges.map { it.horizon.months })
        assertTrue(ranges.all { it.reliable })
    }

    @Test
    fun staleForecastKeepsHorizonsButMarksAllRangesUnreliable() {
        val ranges = AdvisorForecastPolicy.from(
            forecast(priceAvailable = true, coverage = 85),
            freshness(FreshnessStatus.STALE, 85)
        )

        assertEquals(listOf(1, 3, 6, 12), ranges.map { it.horizon.months })
        assertTrue(ranges.none { it.reliable })
    }

    @Test
    fun lowCoverageMarksAllRangesUnreliable() {
        val ranges = AdvisorForecastPolicy.from(
            forecast(priceAvailable = true, coverage = 59),
            freshness(FreshnessStatus.CURRENT, 59)
        )

        assertTrue(ranges.none { it.reliable })
    }

    @Test
    fun missingPriceNeverSynthesizesTargetRange() {
        val ranges = AdvisorForecastPolicy.from(
            forecast(priceAvailable = false, coverage = 90),
            freshness(FreshnessStatus.CURRENT, 90)
        )

        assertTrue(ranges.all { it.lowerTargetPriceEur == null && it.upperTargetPriceEur == null })
        assertTrue(ranges.none { it.reliable })
    }

    @Test
    fun missingOneTargetBoundKeepsRangeHiddenAndUnreliable() {
        val base = forecast(priceAvailable = true, coverage = 90)
        val altered = base.copy(points = base.points.map {
            if (it.horizon == ForecastHorizon.SIX_MONTHS) it.copy(bearTargetPriceEur = null) else it
        })

        val ranges = AdvisorForecastPolicy.from(altered, freshness(FreshnessStatus.CURRENT, 90))
        val six = ranges.single { it.horizon == ForecastHorizon.SIX_MONTHS }

        assertNull(six.lowerTargetPriceEur)
        assertFalse(six.reliable)
    }

    private fun forecast(priceAvailable: Boolean, coverage: Int) = InvestmentForecast(
        points = ForecastHorizon.entries.map { horizon ->
            val expected = when (horizon) {
                ForecastHorizon.ONE_MONTH -> 3.0
                ForecastHorizon.THREE_MONTHS -> 6.0
                ForecastHorizon.SIX_MONTHS -> 9.0
                ForecastHorizon.TWELVE_MONTHS -> 15.0
            }
            ForecastPoint(
                horizon = horizon,
                expectedChangePct = expected,
                bearChangePct = expected - 8.0,
                bullChangePct = expected + 10.0,
                targetPriceEur = if (priceAvailable) 100.0 * (1.0 + expected / 100.0) else null,
                bearTargetPriceEur = if (priceAvailable) 100.0 * (1.0 + (expected - 8.0) / 100.0) else null,
                bullTargetPriceEur = if (priceAvailable) 100.0 * (1.0 + (expected + 10.0) / 100.0) else null,
                direction = "",
                reasons = listOf("test")
            )
        },
        coveragePct = coverage
    )

    private fun freshness(status: FreshnessStatus, coverage: Int?) = DataFreshnessSummary(
        status = status,
        label = status.name,
        analysisAsOf = "2026-09-06T08:00:00Z",
        quoteSource = "test",
        historySource = "test",
        fundamentalSource = "test",
        coverage = coverage
    )
}
