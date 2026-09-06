package de.tobias.investmentradar

import kotlin.math.roundToInt

object SavingsPlanBudget {
    fun monthlyAmounts(plans: List<SavingsPlan>): Map<String, Int> {
        val totals = mutableMapOf<String, Double>()
        plans.asSequence()
            .filter { it.enabled }
            .forEach { plan ->
                val itemId = plan.itemId?.takeIf { it.isNotBlank() } ?: return@forEach
                val monthlyEquivalent = when (plan.frequency) {
                    SavingsPlanFrequency.MONTHLY -> plan.amountEur
                    SavingsPlanFrequency.TWICE_MONTHLY -> plan.amountEur * 2.0
                }
                if (monthlyEquivalent.isFinite() && monthlyEquivalent > 0.0) {
                    totals[itemId] = (totals[itemId] ?: 0.0) + monthlyEquivalent
                }
            }
        return totals.mapValues { (_, amount) -> amount.roundToInt().coerceAtLeast(0) }
            .filterValues { it > 0 }
    }
}
