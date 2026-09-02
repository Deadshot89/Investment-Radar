package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiModelDefaultsTest {
    @Test fun legacyStatusMapsToRecommendationWhenNewFieldMissing() {
        assertEquals("BUY", recommendationFallback("", "KAUFEN"))
        assertEquals("WATCH", recommendationFallback("", "BEOBACHTEN"))
        assertEquals("REVIEW", recommendationFallback("", "VERKAUF PRÜFEN"))
    }

    @Test fun missingScoresStayNull() {
        val item = testInvestmentItem(scoreTotal = null)
        assertNull(item.scoreTotal)
        assertNull(item.scoreQuality)
    }
}
