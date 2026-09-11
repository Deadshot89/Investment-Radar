package de.tobias.investmentradar

data class InvestmentBudgetViewState(
    val monthlyBudgetEur: Double,
    val extraFundingEur: Double,
    val investedEur: Double,
    val saleCreditsEur: Double,
    val reservedEur: Double,
    val availableEur: Double,
    val advisorBudgetEur: Int
) {
    companion object {
        fun from(summary: InvestmentBudgetSummary): InvestmentBudgetViewState {
            val cockpit = InvestmentBudgetPresentation.from(summary)
            return InvestmentBudgetViewState(
                monthlyBudgetEur = cockpit.monthlyBudgetEur,
                extraFundingEur = cockpit.extraFundingEur,
                investedEur = cockpit.investedEur,
                saleCreditsEur = cockpit.saleCreditsEur,
                reservedEur = cockpit.reservedEur,
                availableEur = cockpit.availableEur,
                advisorBudgetEur = cockpit.advisorBudgetEur
            )
        }
    }
}
