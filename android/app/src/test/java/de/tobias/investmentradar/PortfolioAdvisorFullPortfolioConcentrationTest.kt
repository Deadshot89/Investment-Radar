package de.tobias.investmentradar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioAdvisorFullPortfolioConcentrationTest {
    @Test
    fun hiddenHoldingIsIncludedInConcentrationDenominator() {
        val candidates = listOf(
            candidate(
                id = "visible-buy",
                action = PortfolioAdvisorAction.NACHKAUFEN,
                score = 90,
                currentValueEur = 350.0
            ),
            candidate(
                id = "visible-hold",
                action = PortfolioAdvisorAction.HALTEN,
                score = 65,
                currentValueEur = 300.0
            ),
            candidate(
                id = "new",
                action = PortfolioAdvisorAction.NEU_AUFNEHMEN,
                score = 85,
                holding = false
            )
        )

        val plan = allocateWithFullPortfolioContext(
            candidates = candidates,
            budgetEur = 100,
            holdingIds = setOf("visible-buy", "visible-hold", "hidden-holding"),
            valuesEur = mapOf(
                "visible-buy" to 350.0,
                "visible-hold" to 300.0,
                "hidden-holding" to 350.0
            )
        )

        assertTrue(
            "35% Gesamtdepot-Anteil darf nicht als >=40% Konzentration blockiert werden",
            plan.allocations.any { it.itemId == "visible-buy" && it.amountEur > 0 }
        )
    }

    @Test
    fun realFortyPercentHoldingStaysBlockedWithFullPortfolioContext() {
        val candidates = listOf(
            candidate(
                id = "visible-buy",
                action = PortfolioAdvisorAction.NACHKAUFEN,
                score = 90,
                currentValueEur = 400.0
            ),
            candidate(
                id = "new",
                action = PortfolioAdvisorAction.NEU_AUFNEHMEN,
                score = 85,
                holding = false
            )
        )

        val plan = allocateWithFullPortfolioContext(
            candidates = candidates,
            budgetEur = 100,
            holdingIds = setOf("visible-buy", "hidden-holding"),
            valuesEur = mapOf(
                "visible-buy" to 400.0,
                "hidden-holding" to 600.0
            )
        )

        assertFalse(
            "Eine echte 40%-Position muss weiterhin für zusätzliche Käufe gesperrt bleiben",
            plan.allocations.any { it.itemId == "visible-buy" }
        )
        assertTrue(plan.allocations.any { it.itemId == "new" && it.amountEur > 0 })
    }

    @Test
    fun incompleteFullPortfolioValuationDoesNotInventConcentrationBlock() {
        val candidates = listOf(
            candidate(
                id = "visible-buy",
                action = PortfolioAdvisorAction.NACHKAUFEN,
                score = 90,
                currentValueEur = 600.0
            ),
            candidate(
                id = "visible-hold",
                action = PortfolioAdvisorAction.HALTEN,
                score = 65,
                currentValueEur = 400.0
            ),
            candidate(
                id = "new",
                action = PortfolioAdvisorAction.NEU_AUFNEHMEN,
                score = 85,
                holding = false
            )
        )

        val plan = allocateWithFullPortfolioContext(
            candidates = candidates,
            budgetEur = 100,
            holdingIds = setOf("visible-buy", "visible-hold", "hidden-without-value"),
            valuesEur = mapOf(
                "visible-buy" to 600.0,
                "visible-hold" to 400.0
            )
        )

        assertTrue(
            "Bei unvollständiger Depotbewertung darf kein 60%-Anteil aus einem Teildepot erfunden werden",
            plan.allocations.any { it.itemId == "visible-buy" && it.amountEur > 0 }
        )
    }

    private fun allocateWithFullPortfolioContext(
        candidates: List<PortfolioAdvisorCandidate>,
        budgetEur: Int,
        holdingIds: Set<String>,
        valuesEur: Map<String, Double>
    ): PortfolioAdvisorPlan = PortfolioAdvisorEngine.allocate(
        candidates = candidates,
        budgetEur = budgetEur,
        concentrationContext = PortfolioConcentrationContext(
            holdingIds = holdingIds,
            valuesEur = valuesEur
        )
    )

    private fun candidate(
        id: String,
        action: PortfolioAdvisorAction,
        score: Int?,
        holding: Boolean = true,
        currentValueEur: Double? = null
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
        currentValueEur = currentValueEur,
        monthlySavingsEur = 0
    )
}
