package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioAdvisorLiveBudgetTest {
    @Test
    fun `advisor uses remaining available cash instead of static monthly budget`() {
        val summary = InvestmentBudgetSummary(
            monthlyDepositsEur = 100.0,
            extraDepositsEur = 0.0,
            executedBuysEur = 40.0,
            saleCreditsEur = 0.0,
            availableEur = 60.0,
            reservedEur = 0.0
        )

        val plan = PortfolioAdvisorEngine.allocate(emptyList(), summary)

        assertEquals(60, plan.budgetEur)
        assertEquals(60, plan.cashEur)
    }

    @Test
    fun `advisor never receives negative live cash`() {
        val summary = InvestmentBudgetSummary(100.0, 0.0, 110.0, 0.0, -10.0, 0.0)
        val plan = PortfolioAdvisorEngine.allocate(emptyList(), summary)
        assertEquals(0, plan.budgetEur)
        assertEquals(0, plan.cashEur)
    }
}
