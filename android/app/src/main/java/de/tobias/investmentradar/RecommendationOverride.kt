package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

data class RecommendationOverride(
    val itemId: String,
    val action: PortfolioAdvisorAction,
    val amountEur: Int? = null
)

object RecommendationOverridePresentation {
    fun isBuyAction(action: PortfolioAdvisorAction): Boolean =
        action == PortfolioAdvisorAction.NACHKAUFEN || action == PortfolioAdvisorAction.NEU_AUFNEHMEN

    fun allowedActions(isHolding: Boolean): List<PortfolioAdvisorAction> =
        if (isHolding) {
            listOf(
                PortfolioAdvisorAction.NACHKAUFEN,
                PortfolioAdvisorAction.HALTEN,
                PortfolioAdvisorAction.REDUZIEREN,
                PortfolioAdvisorAction.VERKAUFEN
            )
        } else {
            listOf(
                PortfolioAdvisorAction.NEU_AUFNEHMEN,
                PortfolioAdvisorAction.NICHT_AUFNEHMEN
            )
        }

    fun label(action: PortfolioAdvisorAction): String = when (action) {
        PortfolioAdvisorAction.NACHKAUFEN -> "NACHKAUFEN"
        PortfolioAdvisorAction.HALTEN -> "HALTEN"
        PortfolioAdvisorAction.REDUZIEREN -> "REDUZIEREN"
        PortfolioAdvisorAction.VERKAUFEN -> "VERKAUFEN"
        PortfolioAdvisorAction.NEU_AUFNEHMEN -> "KAUFEN"
        PortfolioAdvisorAction.NICHT_AUFNEHMEN -> "NICHT KAUFEN"
        PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> "KEINE BELASTBARE BEWERTUNG"
    }
}

object RecommendationOverrideCodec {
    fun encode(overrides: Collection<RecommendationOverride>): String = JSONArray().apply {
        overrides.sortedBy { it.itemId }.forEach { override ->
            put(JSONObject().apply {
                put("itemId", override.itemId)
                put("action", override.action.name)
                override.amountEur?.let { put("amountEur", it.coerceAtLeast(0)) }
            })
        }
    }.toString()

    fun decode(raw: String): Map<String, RecommendationOverride> = runCatching {
        val array = JSONArray(raw)
        buildMap {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val itemId = value.optString("itemId").trim()
                if (itemId.isBlank()) continue
                val action = runCatching {
                    PortfolioAdvisorAction.valueOf(value.optString("action"))
                }.getOrNull() ?: continue
                val amount = if (value.has("amountEur")) value.optInt("amountEur").coerceAtLeast(0) else null
                put(
                    itemId,
                    RecommendationOverride(
                        itemId = itemId,
                        action = action,
                        amountEur = if (RecommendationOverridePresentation.isBuyAction(action)) amount else null
                    )
                )
            }
        }
    }.getOrDefault(emptyMap())
}

object RecommendationOverrideStore {
    private const val PREFS = "investment_radar_recommendation_overrides"
    private const val KEY = "overrides_v1"

    fun read(context: Context): Map<String, RecommendationOverride> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyMap()
        return RecommendationOverrideCodec.decode(raw)
    }

    fun save(context: Context, override: RecommendationOverride) {
        require(override.itemId.isNotBlank())
        val next = read(context).toMutableMap()
        next[override.itemId] = override
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, RecommendationOverrideCodec.encode(next.values))
            .apply()
    }

    fun remove(context: Context, itemId: String) {
        if (itemId.isBlank()) return
        val next = read(context).toMutableMap()
        next.remove(itemId)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, RecommendationOverrideCodec.encode(next.values))
            .apply()
    }
}

object RecommendationOverrideEngine {
    fun apply(
        base: PortfolioAdvisorPlan,
        overrides: Map<String, RecommendationOverride>
    ): PortfolioAdvisorPlan {
        if (overrides.isEmpty()) return base
        val candidateById = base.candidates.associateBy { it.itemId }
        val active = overrides.mapNotNull { (itemId, override) ->
            val candidate = candidateById[itemId] ?: return@mapNotNull null
            if (override.action !in RecommendationOverridePresentation.allowedActions(candidate.isHolding)) {
                return@mapNotNull null
            }
            itemId to override
        }.toMap()
        if (active.isEmpty()) return base

        val candidates = base.candidates.map { candidate ->
            active[candidate.itemId]?.let { candidate.copy(action = it.action) } ?: candidate
        }
        val overriddenIds = active.keys
        val plannedSavingsEur = candidates.sumOf { it.monthlySavingsEur.coerceAtLeast(0) }
            .coerceAtMost(base.budgetEur)
        var remainingPurchaseBudget = (base.budgetEur - plannedSavingsEur).coerceAtLeast(0)
        val allocations = mutableListOf<PortfolioAllocation>()

        candidates.forEach { candidate ->
            val override = active[candidate.itemId] ?: return@forEach
            if (!RecommendationOverridePresentation.isBuyAction(override.action)) return@forEach
            val requested = override.amountEur?.coerceAtLeast(0) ?: 0
            val amount = min(requested, remainingPurchaseBudget)
            if (amount > 0) {
                allocations += PortfolioAllocation(
                    itemId = candidate.itemId,
                    amountEur = amount,
                    action = override.action,
                    reason = "Manuell festgelegte Empfehlung"
                )
                remainingPurchaseBudget -= amount
            }
        }

        base.allocations
            .filter { it.itemId !in overriddenIds }
            .forEach { allocation ->
                if (remainingPurchaseBudget <= 0) return@forEach
                val amount = min(allocation.amountEur.coerceAtLeast(0), remainingPurchaseBudget)
                if (amount > 0) {
                    allocations += allocation.copy(amountEur = amount)
                    remainingPurchaseBudget -= amount
                }
            }

        val conflicts = candidates
            .filter {
                it.monthlySavingsEur > 0 &&
                    (it.action == PortfolioAdvisorAction.REDUZIEREN || it.action == PortfolioAdvisorAction.VERKAUFEN)
            }
            .map {
                SavingsPlanConflict(
                    itemId = it.itemId,
                    monthlySavingsEur = it.monthlySavingsEur,
                    action = it.action
                )
            }

        return base.copy(
            allocations = allocations,
            cashEur = (base.budgetEur - allocations.sumOf { it.amountEur }).coerceAtLeast(0),
            reallocations = base.reallocations.filter {
                it.fromItemId !in overriddenIds && it.toItemId !in overriddenIds
            },
            savingsPlanConflicts = conflicts,
            candidates = candidates
        )
    }

    fun maxBuyAmount(
        plan: PortfolioAdvisorPlan,
        overrides: Map<String, RecommendationOverride>,
        itemId: String
    ): Int {
        val plannedSavingsEur = plan.candidates.sumOf { it.monthlySavingsEur.coerceAtLeast(0) }
            .coerceAtMost(plan.budgetEur)
        val purchaseBudget = (plan.budgetEur - plannedSavingsEur).coerceAtLeast(0)
        val otherManualBuys = overrides.values
            .filter { it.itemId != itemId && RecommendationOverridePresentation.isBuyAction(it.action) }
            .sumOf { it.amountEur?.coerceAtLeast(0) ?: 0 }
        return (purchaseBudget - otherManualBuys).coerceAtLeast(0)
    }
}
