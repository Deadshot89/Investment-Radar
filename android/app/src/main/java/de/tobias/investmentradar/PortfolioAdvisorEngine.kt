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

object PortfolioAdvisorEngine {
    fun allocate(
        candidates: List<PortfolioAdvisorCandidate>,
        budget: InvestmentBudgetSummary
    ): PortfolioAdvisorPlan = allocate(
        candidates = candidates,
        budgetEur = floor(budget.availableEur.coerceAtLeast(0.0)).toInt()
    )

    fun allocate(
        candidates: List<PortfolioAdvisorCandidate>,
        budgetEur: Int
    ): PortfolioAdvisorPlan {
        val budget = InvestmentBudgetRuntime.resolveAdvisorBudget(budgetEur).coerceAtLeast(0)
        val reallocations = ReallocationPolicy.suggest(candidates)
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

        if (budget == 0) {
            return PortfolioAdvisorPlan(
                budgetEur = 0,
                allocations = emptyList(),
                cashEur = 0,
                reallocations = reallocations,
                savingsPlanConflicts = conflicts,
                candidates = candidates
            )
        }

        val strongEligible = candidates.filter { candidate ->
            candidate.advisor.reliable &&
                candidate.advisor.score != null &&
                (candidate.action == PortfolioAdvisorAction.NACHKAUFEN ||
                    candidate.action == PortfolioAdvisorAction.NEU_AUFNEHMEN)
        }

        val eligible = if (strongEligible.isNotEmpty()) {
            strongEligible
        } else {
            candidates.filter { candidate ->
                candidate.advisor.reliable &&
                    candidate.action == PortfolioAdvisorAction.HALTEN &&
                    candidate.advisor.score in 68..71
            }
        }

        if (eligible.isEmpty()) {
            return PortfolioAdvisorPlan(
                budgetEur = budget,
                allocations = emptyList(),
                cashEur = budget,
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
        val deployable = budget * deploymentPct / 100

        if (deployable <= 0) {
            return PortfolioAdvisorPlan(
                budgetEur = budget,
                allocations = emptyList(),
                cashEur = budget,
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
            val desiredAmount = desired[candidate.itemId] ?: 0
            val extraAmount = (desiredAmount - candidate.monthlySavingsEur.coerceAtLeast(0))
                .coerceAtLeast(0)
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

        val invested = allocations.sumOf { it.amountEur }.coerceAtMost(budget)
        return PortfolioAdvisorPlan(
            budgetEur = budget,
            allocations = allocations,
            cashEur = budget - invested,
            reallocations = reallocations,
            savingsPlanConflicts = conflicts,
            candidates = candidates
        )
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
