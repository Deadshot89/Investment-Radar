package de.tobias.investmentradar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioAdvisorLiveBudgetTest {
    @After
    fun tearDown() {
        InvestmentBudgetRuntime.clearForTest()
    }

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

    @Test
    fun `explicit advisor budget is deterministic even when runtime cache differs`() {
        InvestmentBudgetRuntime.refresh(
            InvestmentBudgetSummary(
                monthlyDepositsEur = 7.0,
                extraDepositsEur = 0.0,
                executedBuysEur = 0.0,
                saleCreditsEur = 0.0,
                availableEur = 7.0,
                reservedEur = 0.0
            )
        )

        val plan = PortfolioAdvisorEngine.allocate(emptyList(), 100)

        assertEquals(100, plan.budgetEur)
        assertEquals(100, plan.cashEur)
    }
}
