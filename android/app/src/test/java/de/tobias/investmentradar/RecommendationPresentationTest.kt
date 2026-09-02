package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationPresentationTest {
    @Test fun v2RecommendationTakesPrecedenceOverLegacyStatus() {
        val item = testInvestmentItem(status = "KAUFEN", recommendation = "NO_BUY", scoreTotal = 51)
        assertEquals("NICHT KAUFEN", RecommendationPresentation.label(item))
    }

    @Test fun reducedCoverageIsVisibleInConfidence() {
        val item = testInvestmentItem(recommendation = "WATCH", coverage = 58)
        assertTrue(RecommendationPresentation.confidence(item).contains("DATEN"))
    }

    @Test fun missingScoreIsRenderedAsDash() {
        assertEquals("—", RecommendationPresentation.scoreText(null))
    }
}
