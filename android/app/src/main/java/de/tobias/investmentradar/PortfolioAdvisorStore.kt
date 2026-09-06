package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DatedPortfolioAdvisorPlan(
    val analysisDay: String,
    val plan: PortfolioAdvisorPlan
)

object PortfolioAdvisorStore {
    private const val PREFS = "investment_radar_advisor"
    private const val KEY = "latest_plan_v1"

    fun save(context: Context, analysisDay: String, plan: PortfolioAdvisorPlan) {
        if (analysisDay.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode(DatedPortfolioAdvisorPlan(analysisDay, plan)))
            .apply()
    }

    fun latest(context: Context): DatedPortfolioAdvisorPlan? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return decode(raw)
    }

    internal fun encode(value: DatedPortfolioAdvisorPlan): String = JSONObject().apply {
        put("analysisDay", value.analysisDay)
        put("budgetEur", value.plan.budgetEur)
        put("cashEur", value.plan.cashEur)
        put("allocations", JSONArray().apply {
            value.plan.allocations.forEach { allocation ->
                put(JSONObject().apply {
                    put("itemId", allocation.itemId)
                    put("amountEur", allocation.amountEur)
                    put("action", allocation.action.name)
                    put("reason", allocation.reason)
                })
            }
        })
        put("reallocations", JSONArray().apply {
            value.plan.reallocations.forEach { suggestion ->
                put(JSONObject().apply {
                    put("fromItemId", suggestion.fromItemId)
                    put("toItemId", suggestion.toItemId)
                    put("amountEur", suggestion.amountEur)
                    put("reason", suggestion.reason)
                })
            }
        })
        put("savingsPlanConflicts", JSONArray().apply {
            value.plan.savingsPlanConflicts.forEach { conflict ->
                put(JSONObject().apply {
                    put("itemId", conflict.itemId)
                    put("monthlySavingsEur", conflict.monthlySavingsEur)
                    put("action", conflict.action.name)
                })
            }
        })
        put("candidates", JSONArray().apply {
            value.plan.candidates.forEach { candidate -> put(candidate.toJson()) }
        })
    }.toString()

    internal fun decode(raw: String): DatedPortfolioAdvisorPlan? = runCatching {
        val root = JSONObject(raw)
        val day = root.optString("analysisDay").takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val allocations = root.optJSONArray("allocations").objects().mapNotNull { value ->
            val id = value.optString("itemId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val action = value.action() ?: return@mapNotNull null
            PortfolioAllocation(
                itemId = id,
                amountEur = value.optInt("amountEur").coerceAtLeast(0),
                action = action,
                reason = value.optString("reason")
            )
        }
        val reallocations = root.optJSONArray("reallocations").objects().mapNotNull { value ->
            val from = value.optString("fromItemId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val to = value.optString("toItemId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ReallocationSuggestion(
                fromItemId = from,
                toItemId = to,
                amountEur = value.optInt("amountEur").coerceAtLeast(0),
                reason = value.optString("reason")
            )
        }
        val conflicts = root.optJSONArray("savingsPlanConflicts").objects().mapNotNull { value ->
            val id = value.optString("itemId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val action = value.action() ?: return@mapNotNull null
            SavingsPlanConflict(
                itemId = id,
                monthlySavingsEur = value.optInt("monthlySavingsEur").coerceAtLeast(0),
                action = action
            )
        }
        val candidates = root.optJSONArray("candidates").objects().mapNotNull { it.toCandidate() }
        DatedPortfolioAdvisorPlan(
            analysisDay = day,
            plan = PortfolioAdvisorPlan(
                budgetEur = root.optInt("budgetEur").coerceAtLeast(0),
                allocations = allocations,
                cashEur = root.optInt("cashEur").coerceAtLeast(0),
                reallocations = reallocations,
                savingsPlanConflicts = conflicts,
                candidates = candidates
            )
        )
    }.getOrNull()

    private fun PortfolioAdvisorCandidate.toJson(): JSONObject = JSONObject().apply {
        put("itemId", itemId)
        put("isHolding", isHolding)
        put("action", action.name)
        currentValueEur?.let { put("currentValueEur", it) }
        put("monthlySavingsEur", monthlySavingsEur)
        put("advisor", JSONObject().apply {
            put("instrumentId", advisor.instrumentId)
            put("signal", advisor.signal.name)
            advisor.score?.let { put("score", it) }
            put("reliable", advisor.reliable)
            put("reasons", JSONArray(advisor.reasons))
            put("risks", JSONArray(advisor.risks))
            put("confidencePct", advisor.confidencePct)
            put("timingFactor", advisor.timingFactor)
        })
    }

    private fun JSONObject.toCandidate(): PortfolioAdvisorCandidate? {
        val id = optString("itemId").takeIf { it.isNotBlank() } ?: return null
        val action = action() ?: return null
        val advisorObject = optJSONObject("advisor") ?: return null
        val signal = runCatching {
            AdvisorSignal.valueOf(advisorObject.optString("signal"))
        }.getOrNull() ?: return null
        val advisorId = advisorObject.optString("instrumentId").takeIf { it.isNotBlank() } ?: id
        return PortfolioAdvisorCandidate(
            itemId = id,
            isHolding = optBoolean("isHolding", false),
            action = action,
            advisor = AdvisorResult(
                instrumentId = advisorId,
                signal = signal,
                score = if (advisorObject.has("score")) advisorObject.optInt("score") else null,
                reliable = advisorObject.optBoolean("reliable", false),
                reasons = advisorObject.optJSONArray("reasons").strings(),
                risks = advisorObject.optJSONArray("risks").strings(),
                confidencePct = advisorObject.optInt("confidencePct", 0),
                timingFactor = advisorObject.optDouble("timingFactor", 1.0)
            ),
            currentValueEur = if (has("currentValueEur")) optDouble("currentValueEur") else null,
            monthlySavingsEur = optInt("monthlySavingsEur").coerceAtLeast(0)
        )
    }

    private fun JSONObject.action(): PortfolioAdvisorAction? = runCatching {
        PortfolioAdvisorAction.valueOf(optString("action"))
    }.getOrNull()

    private fun JSONArray?.objects(): List<JSONObject> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONArray?.strings(): List<String> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }
}
