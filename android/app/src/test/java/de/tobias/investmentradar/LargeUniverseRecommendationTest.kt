package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeUniverseRecommendationTest {
    @Test
    fun largeUniverseKeepsBudgetExactAndExcludesPortfolioOnly() {
        val items = (0 until 1000).map { index ->
            testInvestmentItem(
                id = "item-$index",
                recommendation = if (index % 17 == 0) "BUY" else "WATCH",
                scoreTotal = 75 + (index % 20),
                risk = 1 + (index % 5),
                portfolioOnly = index == 34
            )
        }
        val plan = RecommendationEngine.plan(items, 100, emptyMap())
        assertEquals(100, plan.items.sumOf { it.allocationEur } + plan.cashAmount)
        assertEquals(0, plan.items.first { it.itemId == "item-34" }.allocationEur)
        assertTrue(plan.items.count { it.allocationEur > 0 } > 1)
    }

    @Test
    fun concentrationBrakeSurvivesLargeUniverse() {
        val heavy = testInvestmentItem(id = "heavy", recommendation = "BUY", scoreTotal = 95, risk = 1)
        val fresh = testInvestmentItem(id = "fresh", recommendation = "BUY", scoreTotal = 80, risk = 2)
        val rest = (0 until 998).map { testInvestmentItem(id = "watch-$it", recommendation = "WATCH", scoreTotal = 70) }
        val plan = RecommendationEngine.plan(listOf(heavy, fresh) + rest, 100, mapOf("heavy" to 900.0, "fresh" to 100.0))
        assertEquals(0, plan.items.first { it.itemId == "heavy" }.allocationEur)
        assertEquals(100, plan.items.first { it.itemId == "fresh" }.allocationEur)
        assertEquals(0, plan.cashAmount)
    }
}
