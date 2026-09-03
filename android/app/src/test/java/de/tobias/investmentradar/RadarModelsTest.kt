package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarModelsTest {
    @Test
    fun radarSummaryConvertsToInvestmentItemWithoutInventingPortfolioOnly() {
        val summary = RadarSummaryItem(
            id = "us-test",
            type = "AKTIE",
            name = "Test Corp",
            ticker = "TEST",
            isin = "",
            tradeRepublicName = "Test Corp",
            region = "NORTH_AMERICA",
            country = "US",
            sector = "Technology",
            industry = "Software",
            marketCapBucket = "LARGE",
            tradeRepublicEligible = null,
            dataQualityTier = "B",
            risk = 3,
            price = 10.0,
            priceEur = 9.0,
            currency = "USD",
            percentChange = 1.2,
            scoreTotal = 71,
            scoreQuality = 80,
            scoreValuation = 60,
            scoreGrowth = 75,
            scoreMomentum = 70,
            scoreRisk = 65,
            coverage = 68,
            recommendation = "WATCH",
            recommendationReasons = listOf("Trade-Republic-Prüfung offen"),
            purchaseEligible = false,
            dataSource = "TEST",
            dataDelayed = false,
            dataError = null,
            analysisAsOf = "2026-09-03T12:00:00Z"
        )

        val item = summary.asInvestmentItem()
        assertEquals("us-test", item.id)
        assertEquals("WATCH", item.recommendation)
        assertEquals(71, item.scoreTotal)
        assertFalse(item.portfolioOnly)
        assertNull(summary.tradeRepublicEligible)
        assertFalse(summary.purchaseEligible)
    }

    @Test
    fun radarPageCarriesRealUniverseResultCountIndependentOfLoadedItems() {
        val page = RadarPage(
            generatedAt = "now",
            total = 987,
            universeTotal = 1000,
            page = 1,
            pageSize = 40,
            hasMore = true,
            items = emptyList(),
            facets = RadarFacets(),
            tradeRepublicVerifiedCount = 120,
            tradeRepublicUnverifiedCount = 880
        )
        assertEquals(1000, page.universeTotal)
        assertEquals(987, page.total)
        assertTrue(page.hasMore)
    }
}
