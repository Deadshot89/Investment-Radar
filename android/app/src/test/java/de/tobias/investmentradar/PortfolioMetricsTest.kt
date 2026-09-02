package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioMetricsTest {
    private fun portfolioItem(id: String, priceEur: Double?): InvestmentItem =
        testInvestmentItem(id = id, priceEur = priceEur)

    @Test
    fun completePortfolioCalculatesValueProfitAndLargestWeight() {
        val a = portfolioItem("a", 20.0)
        val b = portfolioItem("b", 10.0)
        val positions = mapOf(
            "a" to PortfolioPosition("a", investedAmount = 100.0, shares = 10.0),
            "b" to PortfolioPosition("b", investedAmount = 50.0, shares = 5.0)
        )

        val result = PortfolioMetrics.calculate(listOf(a, b), positions, emptyList())

        assertTrue(result.currentValueComplete)
        assertEquals(250.0, result.calculableCurrentValue, 0.001)
        assertEquals(100.0, result.totalProfitLoss!!, 0.001)
        assertEquals(66.666, result.totalProfitLossPct!!, 0.01)
        assertEquals(2, result.heldPositionCount)
        assertEquals("a", result.largestPositionId)
        assertEquals(80.0, result.largestWeightPct!!, 0.001)
    }

    @Test
    fun missingActivePriceProducesPartialValueAndNoCompletePerformance() {
        val priced = portfolioItem("a", 20.0)
        val missing = portfolioItem("b", null)
        val positions = mapOf(
            "a" to PortfolioPosition("a", investedAmount = 100.0, shares = 10.0),
            "b" to PortfolioPosition("b", investedAmount = 50.0, shares = 5.0)
        )

        val result = PortfolioMetrics.calculate(listOf(priced, missing), positions, emptyList())

        assertFalse(result.currentValueComplete)
        assertEquals(1, result.missingPriceCount)
        assertEquals(200.0, result.calculableCurrentValue, 0.001)
        assertNull(result.totalProfitLoss)
        assertNull(result.totalProfitLossPct)
        assertNull(result.positions.first { it.itemId == "b" }.currentValue)
    }

    @Test
    fun fullySoldPositionDoesNotRequireQuoteOrCountAsHeld() {
        val sold = PortfolioPosition(
            itemId = "sold",
            purchases = listOf(PortfolioPurchase("p", "01.01.2026", 100.0, 10.0)),
            sales = listOf(PortfolioSale("s", "02.01.2026", 120.0, 10.0))
        )

        val result = PortfolioMetrics.calculate(
            listOf(portfolioItem("sold", null)),
            mapOf("sold" to sold),
            emptyList()
        )

        assertEquals(0, result.heldPositionCount)
        assertEquals(0, result.missingPriceCount)
        assertTrue(result.currentValueComplete)
    }
}
