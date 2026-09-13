package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationTradePrefillTest {
    @Test
    fun buyCalculatesFractionalSharesFromRecommendedEuroAmount() {
        val result = RecommendationTradePrefill.calculate(
            type = ActionType.BUY_MORE,
            amountEur = 35.0,
            priceEur = 244.0,
            heldShares = 0.0
        )

        assertEquals(35.0, result.amountEur, 0.000001)
        assertEquals(35.0 / 244.0, result.shares!!, 0.000001)
        assertTrue(result.message.contains("Anteile"))
    }

    @Test
    fun fullSellUsesActuallyHeldShares() {
        val result = RecommendationTradePrefill.calculate(
            type = ActionType.SELL,
            amountEur = 67.0,
            priceEur = 100.0,
            heldShares = 0.812345
        )

        assertEquals(0.812345, result.shares!!, 0.000001)
        assertTrue(result.message.contains("vollständigen Verkauf", ignoreCase = true))
    }

    @Test
    fun reduceCalculatesSharesAndNeverExceedsHolding() {
        val result = RecommendationTradePrefill.calculate(
            type = ActionType.REDUCE,
            amountEur = 80.0,
            priceEur = 100.0,
            heldShares = 0.5
        )

        assertEquals(0.5, result.shares!!, 0.000001)
    }

    @Test
    fun missingReliablePriceLeavesSharesOpen() {
        val result = RecommendationTradePrefill.calculate(
            type = ActionType.OPEN_POSITION,
            amountEur = 35.0,
            priceEur = null,
            heldShares = 0.0
        )

        assertNull(result.shares)
        assertTrue(result.message.contains("Kurs", ignoreCase = true))
    }
}
