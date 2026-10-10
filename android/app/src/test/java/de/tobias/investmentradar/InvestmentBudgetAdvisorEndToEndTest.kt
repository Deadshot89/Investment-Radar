package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class InvestmentBudgetAdvisorEndToEndTest {
    @Test
    fun `confirmed buy survives restart reduces next budget and concentration still applies`() {
        val today = LocalDate.of(2026, 10, 10)
        val initialEntries = listOf(
            BudgetJournalEntry(
                id = "monthly-budget-2026-10",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 100.0,
                date = "2026-10-01"
            )
        )

        val initialView = InvestmentBudgetViewState.from(
            summary = InvestmentBudgetJournalEngine.summarize(initialEntries, emptyList()),
            entries = initialEntries,
            today = today
        )
        assertEquals(100, initialView.advisorBudgetEur)

        val executed = InvestmentBudgetExecutionService.executeBuy(
            position = PortfolioPosition("bought"),
            entries = initialEntries,
            reservations = emptyList(),
            request = BudgetBuyExecution(
                eventId = "confirmed-buy",
                reservationId = null,
                itemId = "bought",
                date = today.toString(),
                amountEur = 40.0,
                shares = 1.0,
                source = BudgetJournalSource.RECOMMENDATION
            )
        )

        assertNull(executed.error)
        val executedPosition = requireNotNull(executed.position)
        val afterPurchaseView = InvestmentBudgetViewState.from(
            summary = InvestmentBudgetJournalEngine.summarize(executed.entries, executed.reservations),
            entries = executed.entries,
            activeInvestedEur = executedPosition.activeCostBasis,
            today = today
        )
        assertEquals(60.0, afterPurchaseView.monthlyAvailableEur, 0.000001)
        assertEquals(60, afterPurchaseView.advisorBudgetEur)

        val restartedEntries = InvestmentBudgetMigration.reconcileCurrentMonthTransactions(
            existing = executed.entries,
            positions = mapOf("bought" to executedPosition),
            today = today
        )
        assertEquals(executed.entries, restartedEntries)

        val restartedView = InvestmentBudgetViewState.from(
            summary = InvestmentBudgetJournalEngine.summarize(restartedEntries, executed.reservations),
            entries = restartedEntries,
            activeInvestedEur = executedPosition.activeCostBasis,
            today = today
        )
        assertEquals(60, restartedView.advisorBudgetEur)

        val duplicateExecution = InvestmentBudgetExecutionService.executeBuy(
            position = executedPosition,
            entries = restartedEntries,
            reservations = executed.reservations,
            request = BudgetBuyExecution(
                eventId = "confirmed-buy",
                reservationId = null,
                itemId = "bought",
                date = today.toString(),
                amountEur = 40.0,
                shares = 1.0,
                source = BudgetJournalSource.RECOMMENDATION
            )
        )
        val duplicatePosition = requireNotNull(duplicateExecution.position)
        val duplicateView = InvestmentBudgetViewState.from(
            summary = InvestmentBudgetJournalEngine.summarize(duplicateExecution.entries, duplicateExecution.reservations),
            entries = duplicateExecution.entries,
            activeInvestedEur = duplicatePosition.activeCostBasis,
            today = today
        )
        assertEquals(60, duplicateView.advisorBudgetEur)

        val nextPlan = PortfolioAdvisorEngine.allocate(
            candidates = listOf(
                candidate(
                    id = "concentrated",
                    action = PortfolioAdvisorAction.NACHKAUFEN,
                    score = 90,
                    currentValueEur = 600.0
                ),
                candidate(
                    id = "other-holding",
                    action = PortfolioAdvisorAction.HALTEN,
                    score = 65,
                    currentValueEur = 400.0
                ),
                candidate(
                    id = "new-opportunity",
                    action = PortfolioAdvisorAction.NEU_AUFNEHMEN,
                    score = 85,
                    holding = false,
                    currentValueEur = null
                )
            ),
            budgetEur = restartedView.advisorBudgetEur
        )

        assertFalse(nextPlan.allocations.any { it.itemId == "concentrated" })
        assertEquals(60, nextPlan.allocations.single { it.itemId == "new-opportunity" }.amountEur)
        assertEquals(0, nextPlan.cashEur)
    }

    private fun candidate(
        id: String,
        action: PortfolioAdvisorAction,
        score: Int,
        holding: Boolean = true,
        currentValueEur: Double?
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
            reasons = listOf("e2e-regression"),
            risks = emptyList(),
            confidencePct = 80,
            timingFactor = 1.0
        ),
        currentValueEur = currentValueEur,
        monthlySavingsEur = 0
    )
}
