package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentBudgetRuntimeTest {
    @Test
    fun `runtime exposes live advisor budget after refresh`() {
        InvestmentBudgetRuntime.clearForTest()
        assertEquals(100, InvestmentBudgetRuntime.resolveAdvisorBudget(100))

        InvestmentBudgetRuntime.refresh(
            InvestmentBudgetSummary(
                monthlyDepositsEur = 100.0,
                extraDepositsEur = 6.42,
                executedBuysEur = 31.42,
                saleCreditsEur = 0.0,
                availableEur = 65.0,
                reservedEur = 10.0
            )
        )

        assertEquals(65, InvestmentBudgetRuntime.resolveAdvisorBudget(100))
    }

    @Test
    fun `runtime never returns negative advisor budget`() {
        InvestmentBudgetRuntime.clearForTest()
        InvestmentBudgetRuntime.refresh(
            InvestmentBudgetSummary(0.0, 0.0, 0.0, 0.0, -12.0, 0.0)
        )
        assertEquals(0, InvestmentBudgetRuntime.resolveAdvisorBudget(100))
    }
}
