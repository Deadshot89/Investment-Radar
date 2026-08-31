package de.tobias.investmentradar

import kotlin.math.floor
import kotlin.math.roundToInt

data class PlannerItem(
    val id: String,
    val type: String,
    val status: String,
    val baseAllocation: Int,
    val risk: Int,
    val percentChange: Double? = null
)

data class PlannedAllocation(val id: String, val amount: Int)

data class InvestmentRecommendation(
    val label: String,
    val score: Int,
    val reason: String
)

object InvestmentPlanner {
    fun scaledAllocation(baseAllocation: Int, budget: Int): Int =
        (baseAllocation.coerceAtLeast(0) * budget.coerceAtLeast(0) / 100.0).roundToInt()

    fun plan(items: List<PlannerItem>, budget: Int): List<PlannedAllocation> {
        val safeBudget = budget.coerceAtLeast(0)
        if (safeBudget == 0 || items.isEmpty()) return items.map { PlannedAllocation(it.id, 0) }

        val buyItems = items.filter { normalizeStatus(it.status) == "KAUFEN" && it.baseAllocation > 0 }
        if (buyItems.isEmpty()) return items.map { PlannedAllocation(it.id, 0) }

        val totalWeight = buyItems.sumOf { it.baseAllocation }.toDouble()
        val exact = buyItems.associate { it.id to safeBudget * (it.baseAllocation / totalWeight) }
        val amounts = exact.mapValues { floor(it.value).toInt() }.toMutableMap()
        var remainder = safeBudget - amounts.values.sum()

        exact.entries
            .sortedByDescending { it.value - floor(it.value) }
            .forEach { entry ->
                if (remainder > 0) {
                    amounts[entry.key] = (amounts[entry.key] ?: 0) + 1
                    remainder--
                }
            }

        return items.map { PlannedAllocation(it.id, amounts[it.id] ?: 0) }
    }

    fun recommendation(item: PlannerItem): InvestmentRecommendation {
        val status = normalizeStatus(item.status)
        val riskPenalty = (item.risk.coerceIn(1, 5) - 1) * 4
        val momentum = when {
            item.percentChange == null -> 0
            item.percentChange >= 2.0 -> 4
            item.percentChange >= 0.0 -> 2
            item.percentChange <= -5.0 -> -8
            item.percentChange <= -2.0 -> -4
            else -> -1
        }
        val typeBonus = if (item.type.equals("ETF", ignoreCase = true)) 3 else 0
        val base = when (status) {
            "KAUFEN" -> 92
            "BEOBACHTEN" -> 68
            "VERKAUF PRÜFEN" -> 38
            "NICHT KAUFEN" -> 32
            else -> 55
        }
        val score = (base - riskPenalty + momentum + typeBonus).coerceIn(0, 100)
        val reason = when (status) {
            "KAUFEN" -> if (item.risk <= 2) {
                "Kaufsignal aktiv · vergleichsweise niedriges Risiko · für den Kaufplan priorisiert"
            } else {
                "Kaufsignal aktiv · Chancen überwiegen aktuell · Positionsgröße wird am Risiko begrenzt"
            }
            "BEOBACHTEN" -> "Noch kein klares Kaufsignal · Entwicklung beobachten · aktuell kein Budget zuweisen"
            "VERKAUF PRÜFEN" -> "Warnsignal aktiv · bestehende Position prüfen · keinen Neukauf einplanen"
            "NICHT KAUFEN" -> "Aktuell kein attraktives Chance-Risiko-Verhältnis · keinen Neukauf einplanen"
            else -> "Signal nicht eindeutig · vor einer Entscheidung weitere Daten abwarten"
        }
        return InvestmentRecommendation(status, score, reason)
    }

    private fun normalizeStatus(status: String): String = when (status.trim().uppercase()) {
        "KAUFEN", "BUY" -> "KAUFEN"
        "BEOBACHTEN", "WATCH" -> "BEOBACHTEN"
        "VERKAUFEN", "SELL", "DRINGEND PRÜFEN", "DRINGEND PRUEFEN", "REVIEW" -> "VERKAUF PRÜFEN"
        "NICHT KAUFEN", "NO BUY" -> "NICHT KAUFEN"
        else -> status.trim().uppercase().ifBlank { "BEOBACHTEN" }
    }
}
