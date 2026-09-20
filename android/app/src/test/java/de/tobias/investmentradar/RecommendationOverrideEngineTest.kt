package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationOverrideEngineTest {
    @Test
    fun `manual buy gets priority and automatic allocations use only remaining purchase budget`() {
        val base = PortfolioAdvisorEngine.allocate(
            listOf(
                candidate("manual", PortfolioAdvisorAction.NACHKAUFEN, 85),
                candidate("auto", PortfolioAdvisorAction.NEU_AUFNEHMEN, 82, holding = false)
            ),
            100
        )

        val result = RecommendationOverrideEngine.apply(
            base,
            mapOf("manual" to RecommendationOverride("manual", PortfolioAdvisorAction.NACHKAUFEN, 70))
        )

        assertEquals(70, result.allocations.first { it.itemId == "manual" }.amountEur)
        assertTrue(result.allocations.sumOf { it.amountEur } <= 100)
        assertEquals(100, result.allocations.sumOf { it.amountEur } + result.cashEur)
    }

    @Test
    fun `manual sell removes automatic buy allocation and changes action`() {
        val base = PortfolioAdvisorEngine.allocate(
            listOf(candidate("held", PortfolioAdvisorAction.NACHKAUFEN, 85)),
            100
        )

        val result = RecommendationOverrideEngine.apply(
            base,
            mapOf("held" to RecommendationOverride("held", PortfolioAdvisorAction.VERKAUFEN))
        )

        assertFalse(result.allocations.any { it.itemId == "held" })
        assertEquals(PortfolioAdvisorAction.VERKAUFEN, result.candidates.single().action)
        assertEquals(100, result.cashEur)
    }

    @Test
    fun `savings plans reduce maximum editable manual buy amount`() {
        val base = PortfolioAdvisorEngine.allocate(
            listOf(
                candidate("one", PortfolioAdvisorAction.NACHKAUFEN, 85, savings = 20),
                candidate("two", PortfolioAdvisorAction.NEU_AUFNEHMEN, 80, holding = false)
            ),
            100
        )
        val overrides = mapOf(
            "one" to RecommendationOverride("one", PortfolioAdvisorAction.NACHKAUFEN, 30)
        )

        assertEquals(50, RecommendationOverrideEngine.maxBuyAmount(base, overrides, "two"))
    }

    @Test
    fun `invalid holding action for non holding candidate is ignored defensively`() {
        val base = PortfolioAdvisorEngine.allocate(
            listOf(candidate("new", PortfolioAdvisorAction.NEU_AUFNEHMEN, 85, holding = false)),
            100
        )

        val result = RecommendationOverrideEngine.apply(
            base,
            mapOf("new" to RecommendationOverride("new", PortfolioAdvisorAction.VERKAUFEN))
        )

        assertEquals(PortfolioAdvisorAction.NEU_AUFNEHMEN, result.candidates.single().action)
        assertTrue(result.allocations.any { it.itemId == "new" })
    }

    @Test
    fun `override codec round trips and drops buy amount for non buy action`() {
        val encoded = RecommendationOverrideCodec.encode(
            listOf(
                RecommendationOverride("a", PortfolioAdvisorAction.NACHKAUFEN, 35),
                RecommendationOverride("b", PortfolioAdvisorAction.HALTEN, 99)
            )
        )
        val decoded = RecommendationOverrideCodec.decode(encoded)

        assertEquals(35, decoded["a"]?.amountEur)
        assertEquals(PortfolioAdvisorAction.NACHKAUFEN, decoded["a"]?.action)
        assertEquals(PortfolioAdvisorAction.HALTEN, decoded["b"]?.action)
        assertNull(decoded["b"]?.amountEur)
    }

    private fun candidate(
        id: String,
        action: PortfolioAdvisorAction,
        score: Int,
        holding: Boolean = true,
        savings: Int = 0
    ) = PortfolioAdvisorCandidate(
        itemId = id,
        isHolding = holding,
        action = action,
        advisor = AdvisorResult(
            instrumentId = id,
            signal = when (action) {
                PortfolioAdvisorAction.NACHKAUFEN, PortfolioAdvisorAction.NEU_AUFNEHMEN -> AdvisorSignal.NACHKAUFEN
                PortfolioAdvisorAction.HALTEN, PortfolioAdvisorAction.NICHT_AUFNEHMEN -> AdvisorSignal.HALTEN
                PortfolioAdvisorAction.REDUZIEREN -> AdvisorSignal.REDUZIEREN
                PortfolioAdvisorAction.VERKAUFEN -> AdvisorSignal.VERKAUFEN
                PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG
            },
            score = score,
            reliable = true,
            reasons = listOf("test"),
            risks = emptyList(),
            confidencePct = 80,
            timingFactor = 1.0
        ),
        currentValueEur = if (holding) 500.0 else null,
        monthlySavingsEur = savings
    )
}
