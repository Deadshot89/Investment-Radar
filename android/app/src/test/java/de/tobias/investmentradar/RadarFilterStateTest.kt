package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarFilterStateTest {
    @Test
    fun searchMatchesNameTickerIsinAndType() {
        val stock = radarItem("msft", name = "Microsoft", ticker = "MSFT", isin = "US5949181045", type = "Aktie")
        val etf = radarItem("spyi", name = "SPDR ACWI IMI", ticker = "SPYI", isin = "IE00B3YLTY66", type = "ETF")

        listOf("micro", "MSFT", "594918", "aktie").forEach { query ->
            val result = RadarFilterEngine.apply(listOf(stock, etf), RadarFilterState(query = query), emptySet(), emptySet(), emptyMap())
            assertEquals(listOf("msft"), result.map { it.id })
        }
    }

    @Test
    fun filtersCombineInsteadOfReplacingEachOther() {
        val buyHeld = radarItem("a", recommendation = "BUY", type = "ETF", coverage = 82, risk = 2)
        val buyNotHeld = radarItem("b", recommendation = "BUY", type = "ETF", coverage = 82, risk = 2)
        val state = RadarFilterState(
            recommendation = RadarRecommendationFilter.BUY,
            type = RadarTypeFilter.ETF,
            holding = RadarHoldingFilter.HELD,
            dataQuality = RadarDataQualityFilter.FULL,
            risk = RadarRiskFilter.LOW
        )
        val result = RadarFilterEngine.apply(listOf(buyHeld, buyNotHeld), state, setOf("a"), emptySet(), emptyMap())
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun buyFilterFallsBackToBestWatchCandidatesWhenNoBuySignalExists() {
        val best = radarItem("best", recommendation = "WATCH", scoreTotal = 84, coverage = 90, risk = 2)
        val second = radarItem("second", recommendation = "WATCH", scoreTotal = 79, coverage = 80, risk = 3)
        val weak = radarItem("weak", recommendation = "WATCH", scoreTotal = 61, coverage = 70, risk = 2)
        val noBuy = radarItem("blocked", recommendation = "NO_BUY", scoreTotal = 95, coverage = 100, risk = 2)

        val result = RadarFilterEngine.evaluate(
            items = listOf(weak, noBuy, second, best),
            state = RadarFilterState(recommendation = RadarRecommendationFilter.BUY),
            holdingIds = emptySet(),
            watchlistIds = emptySet(),
            allocationById = emptyMap()
        )

        assertTrue(result.buyFallbackActive)
        assertEquals(listOf("best", "second", "weak"), result.items.map { it.id })
    }

    @Test
    fun watchlistAndNotHeldFiltersCombine() {
        val a = radarItem("a")
        val b = radarItem("b")
        val state = RadarFilterState(holding = RadarHoldingFilter.NOT_HELD, watchlistOnly = true)
        val result = RadarFilterEngine.apply(listOf(a, b), state, setOf("a"), setOf("a", "b"), emptyMap())
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun coverageAndRiskBucketsFollowApprovedBoundaries() {
        val fullLow = radarItem("full-low", coverage = 70, risk = 2)
        val reducedMedium = radarItem("reduced-medium", coverage = 50, risk = 3)
        val insufficientHigh = radarItem("insufficient-high", coverage = 49, risk = 4)
        val missingCoverage = radarItem("missing-coverage", coverage = null, risk = 5)
        val items = listOf(fullLow, reducedMedium, insufficientHigh, missingCoverage)
        assertEquals(listOf("full-low"), RadarFilterEngine.apply(items, RadarFilterState(dataQuality = RadarDataQualityFilter.FULL, risk = RadarRiskFilter.LOW), emptySet(), emptySet(), emptyMap()).map { it.id })
        assertEquals(listOf("reduced-medium"), RadarFilterEngine.apply(items, RadarFilterState(dataQuality = RadarDataQualityFilter.REDUCED, risk = RadarRiskFilter.MEDIUM), emptySet(), emptySet(), emptyMap()).map { it.id })
        assertEquals(listOf("insufficient-high", "missing-coverage"), RadarFilterEngine.apply(items, RadarFilterState(dataQuality = RadarDataQualityFilter.INSUFFICIENT, risk = RadarRiskFilter.HIGH), emptySet(), emptySet(), emptyMap()).map { it.id })
    }

    @Test
    fun sixMonthMomentumSortsNullLast() {
        val high = radarItem("high", momentumM6 = 18.0)
        val low = radarItem("low", momentumM6 = -3.0)
        val missing = radarItem("missing", momentumM6 = null)
        val result = RadarFilterEngine.apply(listOf(missing, low, high), RadarFilterState(sort = RadarSortMode.MOMENTUM_6M), emptySet(), emptySet(), emptyMap())
        assertEquals(listOf("high", "low", "missing"), result.map { it.id })
    }

    @Test
    fun allocationSortUsesPersonalPlanAmounts() {
        val a = radarItem("a")
        val b = radarItem("b")
        val result = RadarFilterEngine.apply(listOf(a, b), RadarFilterState(sort = RadarSortMode.ALLOCATION), emptySet(), emptySet(), mapOf("a" to 10, "b" to 60))
        assertEquals(listOf("b", "a"), result.map { it.id })
    }

    @Test
    fun dayAscendingAndDescendingKeepNullLast() {
        val up = radarItem("up", percentChange = 3.0)
        val down = radarItem("down", percentChange = -2.0)
        val missing = radarItem("missing", percentChange = null)
        val asc = RadarFilterEngine.apply(listOf(missing, up, down), RadarFilterState(sort = RadarSortMode.DAY_ASC), emptySet(), emptySet(), emptyMap())
        val desc = RadarFilterEngine.apply(listOf(missing, up, down), RadarFilterState(sort = RadarSortMode.DAY_DESC), emptySet(), emptySet(), emptyMap())
        assertEquals(listOf("down", "up", "missing"), asc.map { it.id })
        assertEquals(listOf("up", "down", "missing"), desc.map { it.id })
    }

    private fun radarItem(
        id: String,
        name: String = id,
        ticker: String = id.uppercase(),
        isin: String = "",
        type: String = "AKTIE",
        recommendation: String = "WATCH",
        coverage: Int? = 100,
        risk: Int = 2,
        scoreTotal: Int? = 70,
        percentChange: Double? = null,
        momentumM6: Double? = null
    ): InvestmentItem = testInvestmentItem(id = id, type = type, recommendation = recommendation, scoreTotal = scoreTotal, coverage = coverage, risk = risk).copy(
        name = name,
        ticker = ticker,
        isin = isin,
        percentChange = percentChange,
        momentum = MomentumSnapshot(m6 = momentumM6)
    )
}
