package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentBudgetPresentationTest {
    @Test
    fun `cockpit exposes available invested reserved and funding amounts`() {
        val summary = InvestmentBudgetSummary(
            monthlyDepositsEur = 100.0,
            extraDepositsEur = 6.42,
            executedBuysEur = 31.42,
            saleCreditsEur = 12.0,
            availableEur = 77.0,
            reservedEur = 10.0
        )

        val cockpit = InvestmentBudgetPresentation.from(summary)

        assertEquals(100.0, cockpit.monthlyBudgetEur, 0.0001)
        assertEquals(6.42, cockpit.extraFundingEur, 0.0001)
        assertEquals(31.42, cockpit.investedEur, 0.0001)
        assertEquals(12.0, cockpit.saleCreditsEur, 0.0001)
        assertEquals(10.0, cockpit.reservedEur, 0.0001)
        assertEquals(77.0, cockpit.availableEur, 0.0001)
        assertEquals(77, cockpit.advisorBudgetEur)
    }

    @Test
    fun `advisor budget never becomes negative and floors cents`() {
        val summary = InvestmentBudgetSummary(
            monthlyDepositsEur = 20.0,
            extraDepositsEur = 0.0,
            executedBuysEur = 19.8,
            saleCreditsEur = 0.0,
            availableEur = -4.75,
            reservedEur = 4.95
        )

        assertEquals(0, InvestmentBudgetPresentation.from(summary).advisorBudgetEur)

        val positive = summary.copy(availableEur = 42.99)
        assertEquals(42, InvestmentBudgetPresentation.from(positive).advisorBudgetEur)
    }
}
