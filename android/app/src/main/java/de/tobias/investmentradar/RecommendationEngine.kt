package de.tobias.investmentradar

import kotlin.math.floor

data class PersonalRecommendation(
    val itemId: String,
    val objectiveRecommendation: String,
    val scoreTotal: Int?,
    val allocationEur: Int,
    val currentWeightPct: Double,
    val concentrationLabel: String,
    val explanation: String
)

data class PersonalPlan(
    val items: List<PersonalRecommendation>,
    val cashAmount: Int
)

object RecommendationEngine {
    fun plan(
        candidates: List<InvestmentItem>,
        budget: Int,
        currentValues: Map<String, Double>
    ): PersonalPlan {
        val safeBudget = budget.coerceAtLeast(0)
        val cleanValues = currentValues.mapValues { (_, value) -> value.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0 }
        val portfolioTotal = cleanValues.values.sum()
        val weights = candidates.associate { item ->
            val portfolioWeight = if (portfolioTotal > 0.0) (cleanValues[item.id] ?: 0.0) / portfolioTotal * 100.0 else 0.0
            val score = item.scoreTotal ?: 0
            val objectiveBuy = item.recommendation.equals("BUY", true)
            val raw = if (!item.portfolioOnly && objectiveBuy && score >= 75) {
                (score - 74).toDouble() * concentrationFactor(portfolioWeight) * riskFactor(item.risk)
            } else 0.0
            item.id to WeightRow(item, portfolioWeight, raw)
        }
        val totalWeight = weights.values.sumOf { it.personalWeight }
        if (safeBudget == 0) {
            return PersonalPlan(
                items = candidates.map { item -> weights.getValue(item.id).toRecommendation(0) },
                cashAmount = 0
            )
        }

        if (totalWeight <= 0.0) {
            val fallback = weights.values
                .filter { row -> !row.item.portfolioOnly && row.item.recommendation.equals("BUY", true) && (row.item.scoreTotal ?: 0) >= 75 }
                .minWithOrNull(
                    compareBy<WeightRow> { it.portfolioWeight }
                        .thenBy { it.item.risk }
                        .thenByDescending { it.item.scoreTotal ?: 0 }
                )
            if (fallback == null) {
                return PersonalPlan(
                    items = candidates.map { item -> weights.getValue(item.id).toRecommendation(0) },
                    cashAmount = safeBudget
                )
            }
            return PersonalPlan(
                items = candidates.map { item ->
                    val row = weights.getValue(item.id)
                    row.toRecommendation(if (item.id == fallback.item.id) safeBudget else 0, fallbackOverride = item.id == fallback.item.id)
                },
                cashAmount = 0
            )
        }

        val exact = weights.values.filter { it.personalWeight > 0.0 }.map { row ->
            row to safeBudget * row.personalWeight / totalWeight
        }
        val allocations = mutableMapOf<String, Int>()
        var allocated = 0
        exact.forEach { (row, amount) ->
            val base = floor(amount).toInt()
            allocations[row.item.id] = base
            allocated += base
        }
        var remainder = safeBudget - allocated
        exact.sortedByDescending { (_, amount) -> amount - floor(amount) }.forEach { (row, _) ->
            if (remainder > 0) {
                allocations[row.item.id] = (allocations[row.item.id] ?: 0) + 1
                remainder--
            }
        }

        return PersonalPlan(
            items = candidates.map { item -> weights.getValue(item.id).toRecommendation(allocations[item.id] ?: 0) },
            cashAmount = remainder.coerceAtLeast(0)
        )
    }

    private fun concentrationFactor(weightPct: Double): Double = when {
        weightPct >= 40.0 -> 0.0
        weightPct >= 30.0 -> 0.25
        weightPct >= 20.0 -> 0.65
        else -> 1.0
    }

    private fun riskFactor(risk: Int): Double = when (risk.coerceIn(1, 5)) {
        5 -> 0.55
        4 -> 0.75
        else -> 1.0
    }

    private data class WeightRow(
        val item: InvestmentItem,
        val portfolioWeight: Double,
        val personalWeight: Double
    ) {
        fun toRecommendation(allocation: Int, fallbackOverride: Boolean = false): PersonalRecommendation {
            val concentration = when {
                item.portfolioOnly -> "PORTFOLIO"
                fallbackOverride -> "AUSNAHME"
                portfolioWeight >= 40.0 -> "BLOCKIERT"
                portfolioWeight >= 30.0 -> "STARK REDUZIERT"
                portfolioWeight >= 20.0 -> "REDUZIERT"
                else -> "OK"
            }
            val explanation = when {
                item.portfolioOnly -> "Nur Portfolio-Tracking; keine automatische Kaufempfehlung"
                !item.recommendation.equals("BUY", true) -> "Kein objektives Kaufsignal"
                fallbackOverride -> "Ausnahme: kein weniger konzentrierter BUY-Kandidat verfügbar"
                portfolioWeight >= 40.0 -> "Kein Neukauf: Position ist bereits stark konzentriert"
                portfolioWeight >= 30.0 -> "Neukauf wegen hoher Depotgewichtung stark reduziert"
                portfolioWeight >= 20.0 -> "Neukauf wegen Depotgewichtung reduziert"
                item.risk >= 4 -> "Kaufsignal aktiv, Positionsgröße wegen Risiko begrenzt"
                else -> "Kaufsignal aktiv und Depotgewichtung erlaubt weiteren Kauf"
            }
            return PersonalRecommendation(
                itemId = item.id,
                objectiveRecommendation = item.recommendation,
                scoreTotal = item.scoreTotal,
                allocationEur = allocation,
                currentWeightPct = portfolioWeight,
                concentrationLabel = concentration,
                explanation = explanation
            )
        }
    }
}