package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PortfolioAnalysisTest {
    @Test
    fun usesMarketValueWhenQuoteExistsAndOmitsHoldingWithoutCurrentValue() {
        val values = PortfolioAnalysis.values(
            items = listOf(
                testInvestmentItem("a", priceEur = 20.0),
                testInvestmentItem("b", priceEur = null)
            ),
            positions = mapOf(
                "a" to PortfolioPosition("a", investedAmount = 100.0, shares = 10.0),
                "b" to PortfolioPosition("b", investedAmount = 75.0, shares = 3.0)
            ),
            customItems = emptyList()
        )

        assertEquals(200.0, values.getValue("a"), 0.001)
        assertFalse(values.containsKey("b"))
    }
}
