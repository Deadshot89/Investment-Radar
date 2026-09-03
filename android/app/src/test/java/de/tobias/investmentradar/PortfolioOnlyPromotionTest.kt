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
    fun duplicateCustomAssetMatchesBuiltInByNormalizedIsinBeforeTicker() {
        val custom = CustomInvestment(
            id = "custom-old-nel",
            name = "Nel alt",
            ticker = "WRONG",
            isin = " no0010081235 ",
            type = "Aktie"
        )
        val builtIn = testInvestmentItem(id = "custom-nel-asa", priceEur = 0.25)
            .copy(ticker = "NEL.OL", isin = "NO0010081235")

        assertEquals(
            mapOf("custom-old-nel" to "custom-nel-asa"),
            CustomInvestmentStore.promotedTargets(listOf(custom), listOf(builtIn))
        )
    }

    @Test
    fun duplicateCustomAssetFallsBackToUniqueNormalizedTicker() {
        val custom = CustomInvestment(
            id = "custom-old-samsung",
            name = "Samsung alt",
            ticker = " smsn ",
            isin = "",
            type = "Aktie"
        )
        val builtIn = testInvestmentItem(id = "custom-samsung-gdr", priceEur = 1000.0)
            .copy(ticker = "SMSN", isin = "US7960508882")

        assertEquals(
            mapOf("custom-old-samsung" to "custom-samsung-gdr"),
            CustomInvestmentStore.promotedTargets(listOf(custom), listOf(builtIn))
        )
    }

    @Test
    fun ambiguousTickerIsNotPromoted() {
        val custom = CustomInvestment(
            id = "custom-ambiguous",
            name = "Ambiguous",
            ticker = "DUP",
            isin = "",
            type = "Aktie"
        )
        val builtIns = listOf(
            testInvestmentItem(id = "one", priceEur = 1.0).copy(ticker = "DUP", isin = "AAA"),
            testInvestmentItem(id = "two", priceEur = 2.0).copy(ticker = "DUP", isin = "BBB")
        )

        assertTrue(CustomInvestmentStore.promotedTargets(listOf(custom), builtIns).isEmpty())
    }

    @Test
    fun safePositionPromotionNeverOverwritesExistingTarget() {
        val promotions = mapOf("custom-old-nel" to "custom-nel-asa")
        val conflict = CustomInvestmentStore.safePositionPromotions(
            promotions = promotions,
            existingPositionIds = setOf("custom-old-nel", "custom-nel-asa")
        )
        val transferable = CustomInvestmentStore.safePositionPromotions(
            promotions = promotions,
            existingPositionIds = setOf("custom-old-nel")
        )

        assertTrue(conflict.isEmpty())
        assertEquals(promotions, transferable)
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
