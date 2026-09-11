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
}
