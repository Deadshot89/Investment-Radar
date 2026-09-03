package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioOnlyFilteringTest {
    @Test fun portfolioOnlyAssetsStayOutOfRadarAndMonthlyBuyAllocation() {
        val normalBuy = testInvestmentItem(
            id = "normal-buy",
            recommendation = "BUY",
            scoreTotal = 90,
            portfolioOnly = false
        )
        val portfolioOnlyBuy = testInvestmentItem(
            id = "custom-nel-asa",
            recommendation = "BUY",
            scoreTotal = 99,
            portfolioOnly = true
        )

        val radar = RadarFilterEngine.apply(
            items = listOf(normalBuy, portfolioOnlyBuy),
            state = RadarFilterState(),
            holdingIds = setOf(portfolioOnlyBuy.id),
            watchlistIds = emptySet(),
            allocationById = emptyMap()
        )
        assertEquals(listOf("normal-buy"), radar.map { it.id })

        val plan = RecommendationEngine.plan(
            candidates = listOf(normalBuy, portfolioOnlyBuy),
            budget = 100,
            currentValues = mapOf(portfolioOnlyBuy.id to 50.0)
        )
        assertEquals(100, plan.items.first { it.itemId == normalBuy.id }.allocationEur)
        assertEquals(0, plan.items.first { it.itemId == portfolioOnlyBuy.id }.allocationEur)
        assertFalse(plan.items.first { it.itemId == portfolioOnlyBuy.id }.allocationEur > 0)
        assertTrue(portfolioOnlyBuy.portfolioOnly)
    }
}
