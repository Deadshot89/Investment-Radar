package de.tobias.investmentradar

object InvestmentBudgetRuntime {
    @Volatile
    private var liveAdvisorBudgetEur: Int? = null

    fun refresh(summary: InvestmentBudgetSummary) {
        liveAdvisorBudgetEur = InvestmentBudgetPresentation.from(summary).advisorBudgetEur
    }

    fun resolveAdvisorBudget(fallbackBudgetEur: Int): Int =
        liveAdvisorBudgetEur ?: fallbackBudgetEur.coerceAtLeast(0)

    internal fun clearForTest() {
        liveAdvisorBudgetEur = null
    }
}
