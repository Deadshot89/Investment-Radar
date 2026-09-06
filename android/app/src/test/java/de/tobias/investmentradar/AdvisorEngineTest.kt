package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorEngineTest {
    @Test
    fun strongStockBecomesNachkaufen() {
        val result = AdvisorEngine.evaluate(
            stockInput(
                quality = 85,
                valuation = 78,
                growth = 82,
                momentum = 76,
                riskScore = 72,
                six = 14.0,
                twelve = 18.0
            )
        )

        assertEquals(AdvisorSignal.NACHKAUFEN, result.signal)
        assertTrue(result.reliable)
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test
    fun balancedStockBecomesHalten() {
        val result = AdvisorEngine.evaluate(
            stockInput(
                quality = 67,
                valuation = 58,
                growth = 61,
                momentum = 55,
                riskScore = 64,
                six = 4.0,
                twelve = 6.0
            )
        )

        assertEquals(AdvisorSignal.HALTEN, result.signal)
    }

    @Test
    fun weakStockBecomesReduzieren() {
        val result = AdvisorEngine.evaluate(
            stockInput(
                quality = 48,
                valuation = 42,
                growth = 39,
                momentum = 38,
                riskScore = 45,
                six = -4.0,
                twelve = -5.0
            )
        )

        assertEquals(AdvisorSignal.REDUZIEREN, result.signal)
    }

    @Test
    fun veryWeakStockBecomesVerkaufen() {
        val result = AdvisorEngine.evaluate(
            stockInput(
                quality = 30,
                valuation = 28,
                growth = 24,
                momentum = 20,
                riskScore = 25,
                six = -14.0,
                twelve = -18.0
            )
        )

        assertEquals(AdvisorSignal.VERKAUFEN, result.signal)
    }

    @Test
    fun shortTermWeaknessChangesSizingButNotStrategicSignal() {
        val positive = AdvisorEngine.evaluate(
            stockInput(short1 = 10.0, short3 = 16.0, six = 12.0, twelve = 18.0)
        )
        val negative = AdvisorEngine.evaluate(
            stockInput(short1 = -10.0, short3 = -16.0, six = 12.0, twelve = 18.0)
        )

        assertEquals(positive.signal, negative.signal)
        assertTrue(negative.timingFactor < positive.timingFactor)
        assertTrue(negative.timingFactor >= 0.80)
        assertTrue(positive.timingFactor <= 1.10)
    }

    @Test
    fun confidenceUsesStrategicForecastConfidence() {
        val result = AdvisorEngine.evaluate(
            stockInput(six = 12.0, twelve = 18.0, strategicConfidence = 73)
        )

        assertEquals(73, result.confidencePct)
    }

    @Test
    fun missingReliableSixMonthForecastBlocksStrategicAction() {
        val input = stockInput(six = 12.0, twelve = 18.0).copy(
            forecastRanges = forecastRanges(2.0, 4.0, 12.0, 18.0).map {
                if (it.horizon == ForecastHorizon.SIX_MONTHS) it.copy(reliable = false) else it
            }
        )

        val result = AdvisorEngine.evaluate(input)

        assertEquals(AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG, result.signal)
        assertFalse(result.reliable)
    }

    @Test
    fun staleDataNeverCreatesActionSignal() {
        val result = AdvisorEngine.evaluate(stockInput(isFresh = false))

        assertEquals(AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG, result.signal)
        assertFalse(result.reliable)
    }

    @Test
    fun insufficientCoverageNeverCreatesActionSignal() {
        val result = AdvisorEngine.evaluate(stockInput(coveragePct = 49))

        assertEquals(AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG, result.signal)
        assertFalse(result.reliable)
    }

    @Test
    fun missingRequiredStockMetricNeverCreatesActionSignal() {
        val result = AdvisorEngine.evaluate(stockInput(growth = null))

        assertEquals(AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG, result.signal)
    }

    @Test
    fun etfDoesNotRequireStockOnlyQualityOrGrowthMetrics() {
        val result = AdvisorEngine.evaluate(
            AdvisorInput(
                instrumentId = "spyi",
                instrumentType = AdvisorInstrumentType.ETF,
                quality = null,
                valuation = 68,
                growth = null,
                momentum = 65,
                riskScore = 70,
                forecastRanges = forecastRanges(2.0, 4.0, 7.0, 9.0),
                coveragePct = 82,
                isFresh = true
            )
        )

        assertTrue(result.reliable)
        assertEquals(AdvisorSignal.HALTEN, result.signal)
    }

    @Test
    fun fixedIncomeUsesItsOwnReducedMetricSet() {
        val result = AdvisorEngine.evaluate(
            AdvisorInput(
                instrumentId = "bond",
                instrumentType = AdvisorInstrumentType.FIXED_INCOME,
                quality = null,
                valuation = null,
                growth = null,
                momentum = 58,
                riskScore = 80,
                forecastRanges = forecastRanges(1.0, 2.0, 3.0, 4.0),
                coveragePct = 80,
                isFresh = true
            )
        )

        assertTrue(result.reliable)
        assertEquals(AdvisorSignal.HALTEN, result.signal)
    }

    @Test
    fun portfolioWeightCannotInfluenceAdvisorInput() {
        val fields = AdvisorInput::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fields.any { it.contains("weight") || it.contains("allocation") || it.contains("portfolio") })
    }

    private fun stockInput(
        quality: Int? = 80,
        valuation: Int? = 72,
        growth: Int? = 75,
        momentum: Int? = 70,
        riskScore: Int? = 68,
        short1: Double = 2.0,
        short3: Double = 4.0,
        six: Double = 8.0,
        twelve: Double = 12.0,
        strategicConfidence: Int = 80,
        coveragePct: Int = 85,
        isFresh: Boolean = true
    ) = AdvisorInput(
        instrumentId = "meta",
        instrumentType = AdvisorInstrumentType.STOCK,
        quality = quality,
        valuation = valuation,
        growth = growth,
        momentum = momentum,
        riskScore = riskScore,
        forecastRanges = forecastRanges(short1, short3, six, twelve, strategicConfidence),
        coveragePct = coveragePct,
        isFresh = isFresh
    )

    private fun forecastRanges(
        short1: Double,
        short3: Double,
        six: Double,
        twelve: Double,
        confidence: Int = 80
    ): List<AdvisorForecastRange> = listOf(
        range(ForecastHorizon.ONE_MONTH, short1, confidence),
        range(ForecastHorizon.THREE_MONTHS, short3, confidence),
        range(ForecastHorizon.SIX_MONTHS, six, confidence),
        range(ForecastHorizon.TWELVE_MONTHS, twelve, confidence)
    )

    private fun range(
        horizon: ForecastHorizon,
        change: Double,
        confidence: Int
    ) = AdvisorForecastRange(
        horizon = horizon,
        expectedChangePct = change,
        lowerChangePct = change - 10.0,
        upperChangePct = change + 10.0,
        lowerTargetPriceEur = 90.0,
        upperTargetPriceEur = 110.0,
        confidencePct = confidence,
        reliable = true,
        reasons = listOf("test")
    )
}
