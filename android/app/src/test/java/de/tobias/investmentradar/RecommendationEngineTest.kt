package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationEngineTest {
    @Test fun blocksAllocationAboveFortyPercentConcentration() {
        val result = RecommendationEngine.plan(
            listOf(testInvestmentItem("a", recommendation = "BUY", scoreTotal = 90), testInvestmentItem("b", recommendation = "BUY", scoreTotal = 82)),
            100,
            mapOf("a" to 600.0, "b" to 400.0)
        )
        assertEquals(0, result.items.first { it.itemId == "a" }.allocationEur)
        assertEquals(100, result.items.first { it.itemId == "b" }.allocationEur)
    }

    @Test fun eligibleAllocationsSumExactlyToBudget() {
        val result = RecommendationEngine.plan(
            listOf(
                testInvestmentItem("a", recommendation = "BUY", scoreTotal = 90),
                testInvestmentItem("b", recommendation = "BUY", scoreTotal = 80),
                testInvestmentItem("c", recommendation = "BUY", scoreTotal = 76)
            ), 137, emptyMap()
        )
        assertEquals(137, result.items.sumOf { it.allocationEur })
        assertEquals(0, result.cashAmount)
    }

    @Test fun noEligibleBuyKeepsCash() {
        val result = RecommendationEngine.plan(listOf(testInvestmentItem("a", recommendation = "WATCH")), 100, emptyMap())
        assertEquals(100, result.cashAmount)
    }
}
