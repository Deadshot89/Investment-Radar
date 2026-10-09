package de.tobias.investmentradar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioAnalysisTest {
    @Test
    fun fixedIncomeUsesPrincipalWithoutQuoteOrShares() {
        val custom = CustomInvestment(
            id = "fixed",
            name = "Festzins Dez. 2026",
            ticker = "FIXED2026",
            isin = "",
            type = "Festzins",
            fixedPrincipalEur = 2_000.0,
            fixedMaturityValueEur = 2_007.0
        )

        val values = PortfolioAnalysis.values(
            items = emptyList(),
            positions = mapOf("fixed" to PortfolioPosition("fixed")),
            customItems = listOf(custom)
        )

        assertEquals(2_000.0, values.getValue("fixed"), 0.001)
    }

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

    @Test
    fun leadingConcentrationFlagsDominantPositionAndIgnoresInvalidValues() {
        val warning = PortfolioAnalysis.leadingConcentration(
            mapOf(
                "meta" to 760.58,
                "world" to 132.26,
                "nel" to 107.16,
                "invalid" to Double.NaN,
                "negative" to -10.0,
                "rest" to 212.93
            )
        )

        assertEquals("meta", warning?.first)
        assertEquals(62.7, warning?.second ?: 0.0, 0.1)
    }

    @Test
    fun leadingConcentrationReturnsNullBelowWarningThreshold() {
        val warning = PortfolioAnalysis.leadingConcentration(
            mapOf("a" to 35.0, "b" to 34.0, "c" to 31.0)
        )

        assertNull(warning)
    }

    @Test
    fun liveDashboardUsesCalculatedConcentrationInsteadOfDisabledPlaceholder() {
        val mainActivity = listOf(
            File(System.getProperty("user.dir"), "src/main/java/de/tobias/investmentradar/MainActivity.kt"),
            File(System.getProperty("user.dir"), "app/src/main/java/de/tobias/investmentradar/MainActivity.kt")
        ).firstOrNull { it.exists() } ?: error("MainActivity.kt not found")
        val source = mainActivity.readText()

        assertTrue(source.contains("PortfolioAnalysis.values(data.items, positions, customItems)"))
        assertTrue(source.contains("PortfolioAnalysis.leadingConcentration(portfolioValues)"))
        assertFalse(source.contains("val concentrationWarning: Pair<InvestmentItem, Double>? = null"))
    }
}
