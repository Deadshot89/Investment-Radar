package de.tobias.investmentradar

import kotlin.math.floor

/** One concrete extra monthly investment recommendation. */
data class PortfolioAllocation(
    val itemId: String,
    val amountEur: Int,
    val action: PortfolioAdvisorAction,
    val reason: String
)

data class SavingsPlanConflict(
    val itemId: String,
    val monthlySavingsEur: Int,
    val action: PortfolioAdvisorAction
)

data class ReallocationSuggestion(
    val fromItemId: String,
    val toItemId: String,
    val amountEur: Int,
    val reason: String
)

data class PortfolioAdvisorPlan(
    val budgetEur: Int,
    val allocations: List<PortfolioAllocation>,
    val cashEur: Int,
    val reallocations: List<ReallocationSuggestion>,
    val savingsPlanConflicts: List<SavingsPlanConflict>,
    val candidates: List<PortfolioAdvisorCandidate>
)

data class PortfolioConcentrationContext(
    val holdingIds: Set<String>,
    val valuesEur: Map<String, Double>
) {
    companion object {
        fun fromCandidates(candidates: List<PortfolioAdvisorCandidate>): PortfolioConcentrationContext {
            val holdings = candidates.filter { it.isHolding }
            return PortfolioConcentrationContext(
                holdingIds = holdings.mapTo(mutableSetOf()) { it.itemId },
                valuesEur = holdings.mapNotNull { candidate ->
                    candidate.currentValueEur
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                        ?.let { candidate.itemId to it }
                }.toMap()
            )
        }
    }
}

object PortfolioAdvisorEngine {
    fun allocate(
        candidates: List<PortfolioAdvisorCandidate>,
        budgetEur: Int,
        concentrationContext: PortfolioConcentrationContext = PortfolioConcentrationContext.fromCandidates(candidates)
    ): PortfolioAdvisorPlan {
        val budget = budgetEur.coerceAtLeast(0)
        val conflicts = candidates
            .filter {
                it.monthlySavingsEur > 0 &&
                    (it.action == PortfolioAdvisorAction.REDUZIEREN ||
                        it.action == PortfolioAdvisorAction.VERKAUFEN)
            }
            .map {
                SavingsPlanConflict(
                    itemId = it.itemId,
                    monthlySavingsEur = it.monthlySavingsEur,
                    action = it.action
                )
            }
        // Only savings plans that the action plan would actually keep reserve monthly budget.
        // Conflicted plans stay visible for review but must not silently consume buy capacity.
        val committedSavingsEur = candidates
            .filter {
                it.monthlySavingsEur > 0 &&
                    it.action != PortfolioAdvisorAction.REDUZIEREN &&
                    it.action != PortfolioAdvisorAction.VERKAUFEN
            }
            .sumOf { it.monthlySavingsEur.coerceAtLeast(0) }
            .coerceAtMost(budget)
        val purchaseBudget = (budget - committedSavingsEur).coerceAtLeast(0)
        val reallocations = ReallocationPolicy.suggest(candidates)

        if (budget == 0 || purchaseBudget == 0) {
            return PortfolioAdvisorPlan(
                budgetEur = budget,
                allocations = emptyList(),
                cashEur = purchaseBudget,
                reallocations = reallocations,
                savingsPlanConflicts = conflicts,
                candidates = candidates
            )
        }

        val concentrationBlockedItemIds = concentrationBlockedItemIds(candidates, concentrationContext)
        val strongEligible = candidates.filter { candidate ->
            candidate.advisor.reliable &&
                candidate.advisor.score != null &&
                candidate.itemId !in concentrationBlockedItemIds &&
                (candidate.action == PortfolioAdvisorAction.NACHKAUFEN ||
                    candidate.action == PortfolioAdvisorAction.NEU_AUFNEHMEN)
        }

        val eligible = if (strongEligible.isNotEmpty()) {
            strongEligible
        } else {
            candidates.filter { candidate ->
                candidate.advisor.reliable &&
                    candidate.itemId !in concentrationBlockedItemIds &&
                    candidate.action == PortfolioAdvisorAction.HALTEN &&
                    candidate.advisor.score in 68..71
            }
        }

        if (eligible.isEmpty()) {
            return PortfolioAdvisorPlan(
                budgetEur = budget,
                allocations = emptyList(),
                cashEur = purchaseBudget,
                reallocations = reallocations,
                savingsPlanConflicts = conflicts,
                candidates = candidates
            )
        }

        val strongestScore = eligible.maxOf { it.advisor.score ?: 0 }
        val deploymentPct = when {
            strongestScore >= 82 -> 100
            strongestScore >= 76 -> 80
            strongestScore >= 72 -> 60
            eligible.all { it.action == PortfolioAdvisorAction.HALTEN } -> 40
            else -> 0
        }
        val deployable = purchaseBudget * deploymentPct / 100

        if (deployable <= 0) {
            return PortfolioAdvisorPlan(
                budgetEur = budget,
                allocations = emptyList(),
                cashEur = purchaseBudget,
                reallocations = reallocations,
                savingsPlanConflicts = conflicts,
                candidates = candidates
            )
        }

        val weighted = eligible.map { candidate ->
            val score = candidate.advisor.score ?: 0
            val timing = candidate.advisor.timingFactor.coerceAtLeast(0.0)
            candidate to ((score - 60).coerceAtLeast(1) * timing)
        }
        val totalWeight = weighted.sumOf { it.second }

        val desired = if (totalWeight <= 0.0) {
            mapOf(eligible.first().itemId to deployable)
        } else {
            distributeWholeEuros(weighted, deployable, totalWeight)
        }

        val allocations = eligible.mapNotNull { candidate ->
            val extraAmount = (desired[candidate.itemId] ?: 0).coerceAtLeast(0)
            if (extraAmount <= 0) {
                null
            } else {
                PortfolioAllocation(
                    itemId = candidate.itemId,
                    amountEur = extraAmount,
                    action = candidate.action,
                    reason = allocationReason(candidate)
                )
            }
        }

        val invested = allocations.sumOf { it.amountEur }.coerceAtMost(purchaseBudget)
        return PortfolioAdvisorPlan(
            budgetEur = budget,
            allocations = allocations,
            cashEur = purchaseBudget - invested,
            reallocations = reallocations,
            savingsPlanConflicts = conflicts,
            candidates = candidates
        )
    }

    private fun concentrationBlockedItemIds(
        candidates: List<PortfolioAdvisorCandidate>,
        context: PortfolioConcentrationContext
    ): Set<String> {
        val candidateHoldingIds = candidates
            .filter { it.isHolding }
            .mapTo(mutableSetOf()) { it.itemId }
        if (candidateHoldingIds.isEmpty()) return emptySet()

        val holdingIds = context.holdingIds
        if (holdingIds.isEmpty() || candidateHoldingIds.any { it !in holdingIds }) return emptySet()

        // The denominator must represent the complete known portfolio. If even one holding
        // cannot be valued, do not invent a portfolio weight or block a purchase from partial data.
        val valuesById = linkedMapOf<String, Double>()
        holdingIds.forEach { itemId ->
            val value = context.valuesEur[itemId]
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?: return emptySet()
            valuesById[itemId] = value
        }
        val totalValue = valuesById.values.sum()
        if (!totalValue.isFinite() || totalValue <= 0.0) return emptySet()

        return candidateHoldingIds.filterTo(mutableSetOf()) { itemId ->
            val value = valuesById[itemId] ?: return@filterTo false
            value / totalValue * 100.0 >= PortfolioAnalysis.CONCENTRATION_WARNING_PCT
        }
    }

    private fun distributeWholeEuros(
        weighted: List<Pair<PortfolioAdvisorCandidate, Double>>,
        total: Int,
        totalWeight: Double
    ): Map<String, Int> {
        data class Share(
            val itemId: String,
            val base: Int,
            val remainder: Double,
            val order: Int
        )

        val shares = weighted.mapIndexed { index, (candidate, weight) ->
            val exact = total * weight / totalWeight
            val base = floor(exact).toInt()
            Share(candidate.itemId, base, exact - base, index)
        }
        val missing = total - shares.sumOf { it.base }
        val bonusIds = shares
            .sortedWith(compareByDescending<Share> { it.remainder }.thenBy { it.order })
            .take(missing.coerceAtLeast(0))
            .map { it.itemId }
            .toSet()

        return shares.associate { share ->
            share.itemId to (share.base + if (share.itemId in bonusIds) 1 else 0)
        }
    }

    private fun allocationReason(candidate: PortfolioAdvisorCandidate): String {
        val score = candidate.advisor.score
        val firstReason = candidate.advisor.reasons.firstOrNull()
        return buildString {
            append(
                when (candidate.action) {
                    PortfolioAdvisorAction.NEU_AUFNEHMEN -> "Neue Radar-Chance"
                    PortfolioAdvisorAction.NACHKAUFEN -> "Bestehende Position mit Nachkauf-Signal"
                    PortfolioAdvisorAction.HALTEN -> "Grenzfall nahe Nachkauf-Signal"
                    else -> "Advisor-Empfehlung"
                }
            )
            if (score != null) append(" · Score $score")
            if (!firstReason.isNullOrBlank()) append(" · $firstReason")
        }
    }
}
