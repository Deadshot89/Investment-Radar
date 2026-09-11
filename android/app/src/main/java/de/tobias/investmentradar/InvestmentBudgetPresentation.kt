package de.tobias.investmentradar

import kotlin.math.floor

data class InvestmentBudgetCockpit(
    val monthlyBudgetEur: Double,
    val extraFundingEur: Double,
    val investedEur: Double,
    val saleCreditsEur: Double,
    val reservedEur: Double,
    val availableEur: Double,
    val advisorBudgetEur: Int
)

object InvestmentBudgetPresentation {
    fun from(summary: InvestmentBudgetSummary): InvestmentBudgetCockpit {
        val available = summary.availableEur
            .takeIf { it.isFinite() }
            ?.coerceAtLeast(0.0)
            ?: 0.0

        return InvestmentBudgetCockpit(
            monthlyBudgetEur = summary.monthlyDepositsEur.coerceFiniteNonNegative(),
            extraFundingEur = summary.extraDepositsEur.coerceFiniteNonNegative(),
            investedEur = summary.executedBuysEur.coerceFiniteNonNegative(),
            saleCreditsEur = summary.saleCreditsEur.coerceFiniteNonNegative(),
            reservedEur = summary.reservedEur.coerceFiniteNonNegative(),
            availableEur = available,
            advisorBudgetEur = floor(available).toInt().coerceAtLeast(0)
        )
    }

    private fun Double.coerceFiniteNonNegative(): Double =
        if (isFinite()) coerceAtLeast(0.0) else 0.0
}
