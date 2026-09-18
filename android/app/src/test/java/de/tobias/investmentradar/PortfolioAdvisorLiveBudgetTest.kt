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
    fun `advisor accepts only explicit monthly buy budget`() {
        val plan = PortfolioAdvisorEngine.allocate(emptyList(), 60)

        assertEquals(60, plan.budgetEur)
        assertEquals(60, plan.cashEur)
    }

    @Test
    fun `advisor never receives negative explicit budget`() {
        val plan = PortfolioAdvisorEngine.allocate(emptyList(), -10)

        assertEquals(0, plan.budgetEur)
        assertEquals(0, plan.cashEur)
    }

    @Test
    fun `explicit advisor budget is deterministic even when runtime cash differs`() {
        InvestmentBudgetRuntime.refresh(
            InvestmentBudgetSummary(
                monthlyDepositsEur = 7.0,
                extraDepositsEur = 500.0,
                executedBuysEur = 0.0,
                saleCreditsEur = 500.0,
                availableEur = 1007.0,
                reservedEur = 0.0
            )
        )

        val plan = PortfolioAdvisorEngine.allocate(emptyList(), 100)

        assertEquals(100, plan.budgetEur)
        assertEquals(100, plan.cashEur)
    }
}
