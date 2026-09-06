package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SavingsPlanStore {
    private const val PREFS = "investment_radar_savings_plans"
    private const val PLANS_KEY = "plans_v1"
    private const val EXECUTIONS_KEY = "executions_v1"
    private const val SEEDED_KEY = "trade_republic_savings_plans_2026_09_05_v1"
    private const val CONFIRMED_DATES_KEY = "trade_republic_savings_plan_dates_2026_09_05_v2"
    private const val PRIVATE_EQUITY_NAMES_KEY = "trade_republic_private_equity_names_2026_09_05_v3"
    private val CONFIRMED_DEFAULT_PLAN_IDS = setOf(
        "tr-meta-twice-monthly",
        "tr-samsung-gdr-twice-monthly",
        "tr-private-equity-a-twice-monthly",
        "tr-private-equity-b-twice-monthly",
        "tr-msft-monthly"
    )

    fun initialPlans(): List<SavingsPlan> = listOf(
        SavingsPlan(
            id = "tr-meta-twice-monthly",
            name = "Meta Platforms (A)",
            itemId = "meta",
            amountEur = 10.0,
            frequency = SavingsPlanFrequency.TWICE_MONTHLY,
            dayOfMonth1 = 1,
            dayOfMonth2 = 15,
            nextDueDate = null
        ),
        SavingsPlan(
            id = "tr-samsung-gdr-twice-monthly",
            name = "Samsung (GDR)",
            itemId = "custom-samsung-gdr",
            amountEur = 10.0,
            frequency = SavingsPlanFrequency.TWICE_MONTHLY,
            dayOfMonth1 = 1,
            dayOfMonth2 = 15,
            nextDueDate = null
        ),
        SavingsPlan(
            id = "tr-private-equity-a-twice-monthly",
            name = "Private Equity 1",
            itemId = null,
            amountEur = 5.0,
            frequency = SavingsPlanFrequency.TWICE_MONTHLY,
            dayOfMonth1 = 1,
            dayOfMonth2 = 15,
            nextDueDate = null
        ),
        SavingsPlan(
            id = "tr-private-equity-b-twice-monthly",
            name = "Private Equity 2",
            itemId = null,
            amountEur = 5.0,
            frequency = SavingsPlanFrequency.TWICE_MONTHLY,
            dayOfMonth1 = 1,
            dayOfMonth2 = 15,
            nextDueDate = null
        ),
        SavingsPlan(
            id = "tr-msft-monthly",
            name = "Microsoft",
            itemId = "msft",
            amountEur = 10.0,
            frequency = SavingsPlanFrequency.MONTHLY,
            dayOfMonth1 = 1,
            dayOfMonth2 = null,
            nextDueDate = null
        )
    )

    fun mergeSeed(existing: List<SavingsPlan>): List<SavingsPlan> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        initialPlans().forEach { seed -> byId.putIfAbsent(seed.id, seed) }
        return byId.values.sortedBy { it.id }
    }

    fun applyPrivateEquityDisplayNames(plans: List<SavingsPlan>): List<SavingsPlan> = plans.map { plan ->
        if (!plan.name.trim().equals("Private Equity", ignoreCase = true)) return@map plan
        when (plan.id) {
            "tr-private-equity-a-twice-monthly" -> plan.copy(name = "Private Equity 1")
            "tr-private-equity-b-twice-monthly" -> plan.copy(name = "Private Equity 2")
            else -> plan
        }
    }

    fun applyConfirmedDefaultSchedules(plans: List<SavingsPlan>, today: String): List<SavingsPlan> =
        plans.map { plan ->
            if (plan.id !in CONFIRMED_DEFAULT_PLAN_IDS) return@map plan

            val isBlankLegacySchedule = plan.dayOfMonth1 == null && plan.dayOfMonth2 == null && plan.nextDueDate == null
            val isConfirmedDefaultsWithoutNextDate = plan.nextDueDate == null && when (plan.frequency) {
                SavingsPlanFrequency.MONTHLY -> plan.dayOfMonth1 == 1 && plan.dayOfMonth2 == null
                SavingsPlanFrequency.TWICE_MONTHLY -> plan.dayOfMonth1 == 1 && plan.dayOfMonth2 == 15
            }
            if (!isBlankLegacySchedule && !isConfirmedDefaultsWithoutNextDate) return@map plan

            val withConfirmedDays = when (plan.frequency) {
                SavingsPlanFrequency.MONTHLY -> plan.copy(dayOfMonth1 = 1, dayOfMonth2 = null, nextDueDate = today)
                SavingsPlanFrequency.TWICE_MONTHLY -> plan.copy(dayOfMonth1 = 1, dayOfMonth2 = 15, nextDueDate = today)
            }
            withConfirmedDays.copy(nextDueDate = SavingsPlanSchedule.nextDueDate(withConfirmedDays, today))
        }

    fun ensureSeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SEEDED_KEY, false)) {
            savePlans(context, mergeSeed(readPlans(context)))
            prefs.edit().putBoolean(SEEDED_KEY, true).apply()
        }

        if (!prefs.getBoolean(CONFIRMED_DATES_KEY, false)) {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            savePlans(context, applyConfirmedDefaultSchedules(readPlans(context), today))
            prefs.edit().putBoolean(CONFIRMED_DATES_KEY, true).apply()
        }

        if (!prefs.getBoolean(PRIVATE_EQUITY_NAMES_KEY, false)) {
            savePlans(context, applyPrivateEquityDisplayNames(readPlans(context)))
            prefs.edit().putBoolean(PRIVATE_EQUITY_NAMES_KEY, true).apply()
        }
    }

    fun readPlans(context: Context): List<SavingsPlan> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PLANS_KEY, null) ?: return emptyList()
        return decodePlans(raw)
    }

    fun savePlans(context: Context, plans: List<SavingsPlan>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PLANS_KEY, encodePlans(plans))
            .apply()
    }

    fun upsertPlan(context: Context, plan: SavingsPlan) {
        val next = readPlans(context).associateBy { it.id }.toMutableMap().apply { put(plan.id, plan) }.values.toList()
        savePlans(context, next)
    }

    fun readExecutions(context: Context): List<SavingsPlanExecution> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EXECUTIONS_KEY, null) ?: return emptyList()
        return decodeExecutions(raw)
    }

    fun upsertExecution(context: Context, execution: SavingsPlanExecution) {
        val next = readExecutions(context).associateBy { it.id }.toMutableMap().apply { put(execution.id, execution) }.values.toList()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(EXECUTIONS_KEY, encodeExecutions(next))
            .apply()
    }

    fun ensureDueExecutions(context: Context, today: String): List<SavingsPlanExecution> {
        ensureSeeded(context)
        val existing = readExecutions(context).associateBy { it.id }.toMutableMap()
        readPlans(context).forEach { plan ->
            SavingsPlanSchedule.dueExecutions(plan, today).forEach { due -> existing.putIfAbsent(due.id, due) }
        }
        val values = existing.values.sortedBy { it.scheduledDate }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(EXECUTIONS_KEY, encodeExecutions(values))
            .apply()
        return values.filter { it.status == SavingsPlanExecutionStatus.PENDING }
    }

    private fun encodePlans(plans: List<SavingsPlan>): String {
        val array = JSONArray()
        plans.forEach { plan ->
            array.put(JSONObject()
                .put("id", plan.id)
                .put("name", plan.name)
                .put("itemId", plan.itemId)
                .put("amountEur", plan.amountEur)
                .put("frequency", plan.frequency.name)
                .put("dayOfMonth1", plan.dayOfMonth1)
                .put("dayOfMonth2", plan.dayOfMonth2)
                .put("nextDueDate", plan.nextDueDate)
                .put("enabled", plan.enabled))
        }
        return array.toString()
    }

    private fun decodePlans(raw: String): List<SavingsPlan> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                val amount = item.optDouble("amountEur", Double.NaN)
                val frequency = runCatching { SavingsPlanFrequency.valueOf(item.optString("frequency")) }.getOrNull() ?: continue
                if (id.isBlank() || name.isBlank() || !amount.isFinite() || amount <= 0.0) continue
                val day1 = item.optNullableInt("dayOfMonth1")
                val day2 = item.optNullableInt("dayOfMonth2")
                val nextDate = item.optNullableString("nextDueDate")
                add(SavingsPlan(
                    id = id,
                    name = name,
                    itemId = item.optNullableString("itemId"),
                    amountEur = amount,
                    frequency = frequency,
                    dayOfMonth1 = day1,
                    dayOfMonth2 = day2,
                    nextDueDate = nextDate,
                    enabled = item.optBoolean("enabled", true)
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeExecutions(executions: List<SavingsPlanExecution>): String {
        val array = JSONArray()
        executions.forEach { execution ->
            array.put(JSONObject()
                .put("id", execution.id)
                .put("planId", execution.planId)
                .put("scheduledDate", execution.scheduledDate)
                .put("amountEur", execution.amountEur)
                .put("status", execution.status.name)
                .put("confirmedAt", execution.confirmedAt)
                .put("priceEur", execution.priceEur)
                .put("shares", execution.shares))
        }
        return array.toString()
    }

    private fun decodeExecutions(raw: String): List<SavingsPlanExecution> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val planId = item.optString("planId").trim()
                val scheduledDate = item.optString("scheduledDate").trim()
                val amount = item.optDouble("amountEur", Double.NaN)
                val status = runCatching { SavingsPlanExecutionStatus.valueOf(item.optString("status")) }.getOrNull() ?: continue
                if (id.isBlank() || planId.isBlank() || scheduledDate.isBlank() || !amount.isFinite() || amount <= 0.0) continue
                add(SavingsPlanExecution(
                    id = id,
                    planId = planId,
                    scheduledDate = scheduledDate,
                    amountEur = amount,
                    status = status,
                    confirmedAt = item.optNullableString("confirmedAt"),
                    priceEur = item.optNullableDouble("priceEur"),
                    shares = item.optNullableDouble("shares")
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key).takeIf { it in 1..31 }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key, Double.NaN).takeIf { it.isFinite() }
}
