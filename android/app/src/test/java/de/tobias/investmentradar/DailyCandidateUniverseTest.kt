package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCandidateUniverseTest {
    @Test
    fun topRadarQueryUsesOneVerifiedBuyPageOfTwenty() {
        val query = DailyCandidateUniverse.topRadarQuery()

        assertEquals("BUY", query.recommendation)
        assertEquals("SCORE_DESC", query.sort)
        assertEquals(1, query.page)
        assertEquals(20, query.pageSize)
        assertTrue(query.tradeRepublicVerified)
    }

    @Test
    fun candidateIdsComeOnlyFromReturnedVerifiedBuyPage() {
        val page = RadarPage(
            generatedAt = "2026-09-06T07:00:00Z",
            total = 2,
            universeTotal = 1000,
            page = 1,
            pageSize = 20,
            hasMore = true,
            items = listOf(
                radarItem("alpha", recommendation = "BUY", verified = true),
                radarItem("watch", recommendation = "WATCH", verified = true),
                radarItem("unverified", recommendation = "BUY", verified = false)
            ),
            facets = RadarFacets(),
            tradeRepublicVerifiedCount = 2,
            tradeRepublicUnverifiedCount = 1
        )

        val ids = DailyCandidateUniverse.candidateIds(page)

        assertEquals(setOf("alpha"), ids)
        assertFalse(ids.contains("watch"))
        assertFalse(ids.contains("unverified"))
    }

    private fun radarItem(
        id: String,
        recommendation: String,
        verified: Boolean
    ) = RadarSummaryItem(
        id = id,
        type = "Aktie",
        name = id,
        ticker = id.uppercase(),
        isin = "",
        tradeRepublicName = id,
        region = "",
        country = "",
        sector = "",
        industry = "",
        marketCapBucket = "",
        tradeRepublicEligible = verified,
        dataQualityTier = "A",
        risk = 2,
        price = 100.0,
        priceEur = 100.0,
        currency = "EUR",
        percentChange = 1.0,
        scoreTotal = 85,
        scoreQuality = 85,
        scoreValuation = 80,
        scoreGrowth = 82,
        scoreMomentum = 78,
        scoreRisk = 75,
        coverage = 90,
        recommendation = recommendation,
        recommendationReasons = listOf("test"),
        purchaseEligible = verified,
        dataSource = "test",
        dataDelayed = false,
        dataError = null,
        analysisAsOf = "2026-09-06T07:00:00Z"
    )
}
