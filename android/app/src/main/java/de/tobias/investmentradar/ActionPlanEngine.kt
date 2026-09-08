package de.tobias.investmentradar

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min

enum class ActionType {
    KEEP_SAVINGS_PLAN,
    REVIEW_SAVINGS_PLAN,
    BUY_MORE,
    OPEN_POSITION,
    REDUCE,
    SELL,
    HOLD_CASH
}

data class ActionPlanAction(
    val actionId: String,
    val type: ActionType,
    val instrumentId: String,
    val amountEur: Double,
    val plannedShares: Double? = null,
    val fromInstrumentId: String? = null,
    val toInstrumentId: String? = null,
    val reason: String = "",
    val eventFingerprints: List<String> = emptyList(),
    val priority: Int = 0
)

data class ActionPlan(
    val planId: String,
    val analysisDay: String,
    val availableBudgetEur: Double,
    val cashEur: Double,
    val actions: List<ActionPlanAction>
)

object ActionPlanEngine {
    fun build(
        analysisDay: String,
        advisorPlan: PortfolioAdvisorPlan,
        currentPricesEur: Map<String, Double?> = emptyMap(),
        eventFingerprintsByInstrument: Map<String, List<String>> = emptyMap(),
        criticalEventInstrumentIds: Set<String> = emptySet()
    ): ActionPlan {
        val budget = advisorPlan.budgetEur.coerceAtLeast(0).toDouble()
        val actions = mutableListOf<ActionPlanAction>()
        var remainingBudget = budget
        val conflicts = advisorPlan.savingsPlanConflicts.map { it.itemId }.toSet()

        advisorPlan.candidates
            .filter { it.monthlySavingsEur > 0 }
            .sortedBy { it.itemId }
            .forEach { candidate ->
                val amount = candidate.monthlySavingsEur.coerceAtLeast(0).toDouble()
                val isConflict = candidate.itemId in conflicts ||
                    candidate.action == PortfolioAdvisorAction.REDUZIEREN ||
                    candidate.action == PortfolioAdvisorAction.VERKAUFEN
                if (isConflict) {
                    actions += action(
                        analysisDay = analysisDay,
                        type = ActionType.REVIEW_SAVINGS_PLAN,
                        instrumentId = candidate.itemId,
                        amountEur = amount,
                        reason = "Sparplan prüfen: Advisor empfiehlt ${candidate.action.name.lowercase(Locale.GERMANY)}",
                        fingerprints = eventFingerprintsByInstrument[candidate.itemId].orEmpty(),
                        critical = candidate.itemId in criticalEventInstrumentIds
                    )
                } else if (remainingBudget > 0.0) {
                    val committed = min(amount, remainingBudget)
                    if (committed > 0.0) {
                        actions += action(
                            analysisDay = analysisDay,
                            type = ActionType.KEEP_SAVINGS_PLAN,
                            instrumentId = candidate.itemId,
                            amountEur = committed,
                            reason = "Bestehenden Sparplan beibehalten",
                            fingerprints = eventFingerprintsByInstrument[candidate.itemId].orEmpty(),
                            critical = candidate.itemId in criticalEventInstrumentIds
                        )
                        remainingBudget -= committed
                    }
                }
            }

        advisorPlan.reallocations
            .sortedWith(compareBy<ReallocationSuggestion> { it.fromItemId }.thenBy { it.toItemId })
            .forEach { suggestion ->
                val amount = suggestion.amountEur.coerceAtLeast(0).toDouble()
                if (amount <= 0.0) return@forEach
                val source = advisorPlan.candidates.firstOrNull { it.itemId == suggestion.fromItemId }
                val sourceType = if (source?.action == PortfolioAdvisorAction.VERKAUFEN) ActionType.SELL else ActionType.REDUCE
                actions += action(
                    analysisDay = analysisDay,
                    type = sourceType,
                    instrumentId = suggestion.fromItemId,
                    amountEur = amount,
                    plannedShares = sharesFor(amount, currentPricesEur[suggestion.fromItemId]),
                    fromInstrumentId = suggestion.fromItemId,
                    toInstrumentId = suggestion.toItemId,
                    reason = suggestion.reason,
                    fingerprints = eventFingerprintsByInstrument[suggestion.fromItemId].orEmpty(),
                    critical = suggestion.fromItemId in criticalEventInstrumentIds
                )
                actions += action(
                    analysisDay = analysisDay,
                    type = ActionType.OPEN_POSITION,
                    instrumentId = suggestion.toItemId,
                    amountEur = amount,
                    plannedShares = sharesFor(amount, currentPricesEur[suggestion.toItemId]),
                    fromInstrumentId = suggestion.fromItemId,
                    toInstrumentId = suggestion.toItemId,
                    reason = suggestion.reason,
                    fingerprints = eventFingerprintsByInstrument[suggestion.toItemId].orEmpty(),
                    critical = suggestion.toItemId in criticalEventInstrumentIds
                )
            }

        advisorPlan.allocations
            .sortedWith(compareByDescending<PortfolioAllocation> { it.amountEur }.thenBy { it.itemId })
            .forEach { allocation ->
                if (remainingBudget <= 0.0) return@forEach
                val requested = allocation.amountEur.coerceAtLeast(0).toDouble()
                val amount = min(requested, remainingBudget)
                if (amount <= 0.0) return@forEach
                val type = when (allocation.action) {
                    PortfolioAdvisorAction.NEU_AUFNEHMEN -> ActionType.OPEN_POSITION
                    PortfolioAdvisorAction.NACHKAUFEN, PortfolioAdvisorAction.HALTEN -> ActionType.BUY_MORE
                    else -> return@forEach
                }
                actions += action(
                    analysisDay = analysisDay,
                    type = type,
                    instrumentId = allocation.itemId,
                    amountEur = amount,
                    plannedShares = sharesFor(amount, currentPricesEur[allocation.itemId]),
                    reason = allocation.reason,
                    fingerprints = eventFingerprintsByInstrument[allocation.itemId].orEmpty(),
                    critical = allocation.itemId in criticalEventInstrumentIds
                )
                remainingBudget -= amount
            }

        val cash = remainingBudget.coerceAtLeast(0.0)
        if (cash > 0.0) {
            actions += action(
                analysisDay = analysisDay,
                type = ActionType.HOLD_CASH,
                instrumentId = "cash",
                amountEur = cash,
                reason = "Nicht eingesetztes Monatsbudget als Cash halten"
            )
        }

        val ordered = actions.sortedWith(
            compareByDescending<ActionPlanAction> { it.priority }
                .thenBy { it.type.name }
                .thenBy { it.instrumentId }
                .thenBy { it.actionId }
        )
        val planSeed = buildString {
            append(analysisDay).append('|').append(formatAmount(budget))
            ordered.forEach { append('|').append(it.actionId) }
        }
        return ActionPlan(
            planId = "plan-${stableHash(planSeed)}",
            analysisDay = analysisDay,
            availableBudgetEur = budget,
            cashEur = cash,
            actions = ordered
        )
    }

    private fun action(
        analysisDay: String,
        type: ActionType,
        instrumentId: String,
        amountEur: Double,
        plannedShares: Double? = null,
        fromInstrumentId: String? = null,
        toInstrumentId: String? = null,
        reason: String = "",
        fingerprints: List<String> = emptyList(),
        critical: Boolean = false
    ): ActionPlanAction {
        val normalizedFingerprints = fingerprints.filter { it.isNotBlank() }.distinct().sorted()
        val priority = basePriority(type) + if (critical) 100 else 0
        val seed = listOf(
            analysisDay,
            type.name,
            instrumentId,
            formatAmount(amountEur),
            plannedShares?.let(::formatAmount).orEmpty(),
            fromInstrumentId.orEmpty(),
            toInstrumentId.orEmpty(),
            normalizedFingerprints.joinToString(",")
        ).joinToString("|")
        return ActionPlanAction(
            actionId = "action-${stableHash(seed)}",
            type = type,
            instrumentId = instrumentId,
            amountEur = amountEur,
            plannedShares = plannedShares,
            fromInstrumentId = fromInstrumentId,
            toInstrumentId = toInstrumentId,
            reason = reason,
            eventFingerprints = normalizedFingerprints,
            priority = priority
        )
    }

    private fun sharesFor(amountEur: Double, priceEur: Double?): Double? {
        val price = priceEur?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return amountEur / price
    }

    private fun basePriority(type: ActionType): Int = when (type) {
        ActionType.SELL -> 80
        ActionType.REDUCE -> 70
        ActionType.REVIEW_SAVINGS_PLAN -> 60
        ActionType.BUY_MORE -> 50
        ActionType.OPEN_POSITION -> 45
        ActionType.KEEP_SAVINGS_PLAN -> 30
        ActionType.HOLD_CASH -> 10
    }

    private fun formatAmount(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun stableHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { "%02x".format(it) }
}
