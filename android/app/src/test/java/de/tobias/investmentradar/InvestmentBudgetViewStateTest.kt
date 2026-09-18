package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentBudgetViewStateTest {
    @Test
    fun `cockpit exposes all user visible budget buckets`() {
        val summary = InvestmentBudgetSummary(
            monthlyDepositsEur = 100.0,
            extraDepositsEur = 6.42,
            executedBuysEur = 40.0,
            saleCreditsEur = 12.0,
            availableEur = 68.42,
            reservedEur = 10.0
        )

        val state = InvestmentBudgetViewState.from(summary)

        assertEquals(100.0, state.monthlyBudgetEur, 0.000001)
        assertEquals(6.42, state.extraFundingEur, 0.000001)
        assertEquals(40.0, state.investedEur, 0.000001)
        assertEquals(12.0, state.saleCreditsEur, 0.000001)
        assertEquals(10.0, state.reservedEur, 0.000001)
        assertEquals(68.42, state.availableEur, 0.000001)
        assertEquals(68, state.advisorBudgetEur)
    }

    @Test
    fun `negative or invalid available cash is never shown as spendable`() {
        val summary = InvestmentBudgetSummary(
            monthlyDepositsEur = 100.0,
            extraDepositsEur = 0.0,
            executedBuysEur = 120.0,
            saleCreditsEur = 0.0,
            availableEur = -20.0,
            reservedEur = 0.0
        )

        val state = InvestmentBudgetViewState.from(summary)

        assertEquals(0.0, state.availableEur, 0.000001)
        assertEquals(0, state.advisorBudgetEur)
    }

    @Test
    fun `cockpit shows current month spend and reduced remaining budget after purchase`() {
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("buy-1", BudgetJournalType.BUY_DEBIT, 35.0, "2026-09-18", itemId = "meta")
        )
        val summary = InvestmentBudgetJournalEngine.summarize(entries, emptyList())

        val state = InvestmentBudgetViewState.from(
            summary = summary,
            entries = entries,
            activeInvestedEur = 35.0,
            today = java.time.LocalDate.of(2026, 9, 18)
        )

        assertEquals(100.0, state.monthlyBudgetEur, 0.001)
        assertEquals(35.0, state.spentThisMonthEur, 0.001)
        assertEquals(65.0, state.availableEur, 0.001)
    }
}
