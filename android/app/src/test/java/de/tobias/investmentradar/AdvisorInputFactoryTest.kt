package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorInputFactoryTest {
    @Test
    fun mapsStockScoresAndTwelveMonthForecastWithoutInventingValues() {
        val item = testInvestmentItem(id = "meta", type = "Aktie", coverage = 86, risk = 2).copy(
            scoreQuality = 84,
            scoreValuation = 72,
            scoreGrowth = 79,
            scoreMomentum = 68,
            scoreRisk = 74
        )
        val forecast = InvestmentForecast(
            points = listOf(
                ForecastPoint(
                    horizon = ForecastHorizon.TWELVE_MONTHS,
                    expectedChangePct = 14.5,
                    bearChangePct = -3.0,
                    bullChangePct = 31.0,
                    targetPriceEur = 610.0,
                    bearTargetPriceEur = 520.0,
                    bullTargetPriceEur = 700.0,
                    direction = "↗ Aufwärts",
                    reasons = emptyList()
                )
            ),
            coveragePct = 86
        )

        val input = AdvisorInputFactory.from(
            item = item,
            forecast = forecast,
            freshness = freshness(FreshnessStatus.CURRENT, 86)
        )

        assertEquals(AdvisorInstrumentType.STOCK, input.instrumentType)
        assertEquals(84, input.quality)
        assertEquals(72, input.valuation)
        assertEquals(79, input.growth)
        assertEquals(68, input.momentum)
        assertEquals(74, input.riskScore)
        assertEquals(14.5, input.forecast12mPct!!, 0.0001)
        assertTrue(input.isFresh)
    }

    @Test
    fun etfKeepsStockOnlyMetricsAbsent() {
        val item = testInvestmentItem(id = "spyi", type = "ETF", coverage = 82, risk = 2).copy(
            scoreQuality = 91,
            scoreValuation = 66,
            scoreGrowth = 88,
            scoreMomentum = 61,
            scoreRisk = 73
        )

        val input = AdvisorInputFactory.from(
            item = item,
            forecast = forecast12m(8.0, 82),
            freshness = freshness(FreshnessStatus.CURRENT, 82)
        )

        assertEquals(AdvisorInstrumentType.ETF, input.instrumentType)
        assertNull(input.quality)
        assertNull(input.growth)
        assertEquals(66, input.valuation)
        assertEquals(61, input.momentum)
    }

    @Test
    fun fixedIncomeUsesReducedMetricsAndDoesNotInventValuation() {
        val item = testInvestmentItem(id = "bond", type = "FIXED_INCOME", coverage = 78, risk = 1).copy(
            scoreValuation = 90,
            scoreMomentum = 57,
            scoreRisk = 82
        )

        val input = AdvisorInputFactory.from(
            item = item,
            forecast = forecast12m(3.5, 78),
            freshness = freshness(FreshnessStatus.CACHED, 78)
        )

        assertEquals(AdvisorInstrumentType.FIXED_INCOME, input.instrumentType)
        assertNull(input.quality)
        assertNull(input.valuation)
        assertNull(input.growth)
        assertEquals(57, input.momentum)
        assertEquals(82, input.riskScore)
        assertTrue(input.isFresh)
    }

    @Test
    fun staleOrMissingDataRemainsMissingInsteadOfUsingZeroDefaults() {
        val item = testInvestmentItem(id = "x", type = "Aktie", coverage = null, risk = 3)

        val input = AdvisorInputFactory.from(
            item = item,
            forecast = InvestmentForecast(emptyList(), null),
            freshness = freshness(FreshnessStatus.STALE, null)
        )

        assertNull(input.quality)
        assertNull(input.valuation)
        assertNull(input.growth)
        assertNull(input.momentum)
        assertNull(input.riskScore)
        assertNull(input.forecast12mPct)
        assertEquals(0, input.coveragePct)
        assertFalse(input.isFresh)
    }

    private fun forecast12m(change: Double, coverage: Int) = InvestmentForecast(
        points = listOf(
            ForecastPoint(
                horizon = ForecastHorizon.TWELVE_MONTHS,
                expectedChangePct = change,
                bearChangePct = change - 10.0,
                bullChangePct = change + 10.0,
                targetPriceEur = null,
                bearTargetPriceEur = null,
                bullTargetPriceEur = null,
                direction = "",
                reasons = emptyList()
            )
        ),
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
