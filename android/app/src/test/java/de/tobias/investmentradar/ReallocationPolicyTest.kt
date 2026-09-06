package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReallocationPolicyTest {
    @Test
    fun fivePointBetterTargetDoesNotTriggerReallocation() {
        val suggestions = ReallocationPolicy.suggest(
            listOf(
                candidate("source", PortfolioAdvisorAction.REDUZIEREN, score = 40, value = 200.0),
                candidate("target", PortfolioAdvisorAction.NACHKAUFEN, score = 45)
            )
        )

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun fifteenPointBetterTargetTriggersCappedReduceSuggestion() {
        val suggestions = ReallocationPolicy.suggest(
            listOf(
                candidate("source", PortfolioAdvisorAction.REDUZIEREN, score = 40, value = 200.0),
                candidate("target", PortfolioAdvisorAction.NEU_AUFNEHMEN, score = 55, holding = false)
            )
        )

        val suggestion = suggestions.single()
        assertEquals("source", suggestion.fromItemId)
        assertEquals("target", suggestion.toItemId)
        assertEquals(50, suggestion.amountEur)
    }

    @Test
    fun sellSuggestionIsCappedAtOneHundredEuros() {
        val suggestion = ReallocationPolicy.suggest(
            listOf(
                candidate("source", PortfolioAdvisorAction.VERKAUFEN, score = 20, value = 500.0),
                candidate("target", PortfolioAdvisorAction.NACHKAUFEN, score = 90)
            )
        ).single()

        assertEquals(100, suggestion.amountEur)
    }

    @Test
    fun unknownSourceValueNeverInventsSaleAmount() {
        val suggestions = ReallocationPolicy.suggest(
            listOf(
                candidate("source", PortfolioAdvisorAction.REDUZIEREN, score = 40, value = null),
                candidate("target", PortfolioAdvisorAction.NACHKAUFEN, score = 90)
            )
        )

        assertTrue(suggestions.isEmpty())
    }

    private fun candidate(
        id: String,
        action: PortfolioAdvisorAction,
        score: Int,
        value: Double? = null,
        holding: Boolean = true,
        reliable: Boolean = true
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
            reliable = reliable,
            reasons = listOf("test"),
            risks = emptyList(),
            confidencePct = if (reliable) 80 else 0,
            timingFactor = 1.0
        ),
        currentValueEur = value,
        monthlySavingsEur = 0
    )
}
