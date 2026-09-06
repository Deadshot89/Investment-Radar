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
                forecast12mPct = 18.0
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
                forecast12mPct = 6.0
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
                forecast12mPct = -5.0
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
                forecast12mPct = -18.0
            )
        )

        assertEquals(AdvisorSignal.VERKAUFEN, result.signal)
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
                forecast12mPct = 9.0,
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
                forecast12mPct = 4.0,
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
        forecast12mPct: Double? = 12.0,
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
        forecast12mPct = forecast12mPct,
        coveragePct = coveragePct,
        isFresh = isFresh
    )
}
