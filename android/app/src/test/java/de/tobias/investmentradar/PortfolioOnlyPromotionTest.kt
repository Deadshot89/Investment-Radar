package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioOnlyPromotionTest {
    @Test
    fun promotedNelAndSamsungAreDetectedWithoutTouchingOtherCustomAssets() {
        val customItems = listOf(
            CustomInvestment(
                id = "custom-nel-asa",
                name = "Nel ASA",
                ticker = "NEL.OL",
                isin = "NO0010081235",
                type = "Aktie"
            ),
            CustomInvestment(
                id = "custom-samsung-gdr",
                name = "Samsung (GDR)",
                ticker = "SMSN",
                isin = "US7960508882",
                type = "Aktie"
            ),
            CustomInvestment(
                id = "custom-example",
                name = "Example",
                ticker = "EXM",
                isin = "",
                type = "Aktie"
            )
        )

        val promoted = CustomInvestmentStore.promotedIds(
            customItems = customItems,
            builtInIds = setOf("custom-nel-asa", "custom-samsung-gdr", "meta")
        )

        assertEquals(setOf("custom-nel-asa", "custom-samsung-gdr"), promoted)
        assertFalse("custom-example" in promoted)
    }

    @Test
    fun trackedPromotedHoldingUsesLiveBackendPriceAndKeepsUnknownCostBasis() {
        val position = PortfolioPosition(
            itemId = "custom-nel-asa",
            snapshotValueEur = 113.80,
            trackedShares = 100.0
        )
        val result = PortfolioMetrics.calculate(
            items = listOf(testInvestmentItem(id = "custom-nel-asa", priceEur = 0.25)),
            positions = mapOf(position.itemId to position),
            customItems = emptyList()
        )
        val metric = result.positions.single()

        assertEquals(25.0, metric.currentValue!!, 0.001)
        assertTrue(metric.active)
        assertFalse(metric.costBasisKnown)
        assertEquals(100.0, metric.weightPct!!, 0.001)
    }
}
