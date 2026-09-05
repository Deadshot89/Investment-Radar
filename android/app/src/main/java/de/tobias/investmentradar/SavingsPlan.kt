package de.tobias.investmentradar

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class SavingsPlanFrequency {
    MONTHLY,
    TWICE_MONTHLY
}

enum class SavingsPlanExecutionStatus {
    PENDING,
    CONFIRMED,
    SKIPPED
}

data class SavingsPlan(
    val id: String,
    val name: String,
    val itemId: String?,
    val amountEur: Double,
    val frequency: SavingsPlanFrequency,
    val dayOfMonth1: Int,
    val dayOfMonth2: Int?,
    val nextDueDate: String,
    val enabled: Boolean = true
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(amountEur.isFinite() && amountEur > 0.0)
        require(dayOfMonth1 in 1..31)
        if (frequency == SavingsPlanFrequency.TWICE_MONTHLY) {
            require(dayOfMonth2 != null && dayOfMonth2 in 1..31 && dayOfMonth2 != dayOfMonth1)
        }
    }
}

data class SavingsPlanExecution(
    val id: String,
    val planId: String,
    val scheduledDate: String,
    val amountEur: Double,
    val status: SavingsPlanExecutionStatus = SavingsPlanExecutionStatus.PENDING,
    val confirmedAt: String? = null,
    val priceEur: Double? = null,
    val shares: Double? = null
) {
    companion object {
        fun pending(planId: String, scheduledDate: String, amountEur: Double): SavingsPlanExecution =
            SavingsPlanExecution(
                id = "$planId@$scheduledDate",
                planId = planId,
                scheduledDate = scheduledDate,
                amountEur = amountEur,
                status = SavingsPlanExecutionStatus.PENDING
            )
    }
}

object SavingsPlanSchedule {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    fun nextDueDate(plan: SavingsPlan, afterDate: String): String {
        val after = parseDate(afterDate)
        val candidates = when (plan.frequency) {
            SavingsPlanFrequency.MONTHLY -> listOf(plan.dayOfMonth1)
            SavingsPlanFrequency.TWICE_MONTHLY -> listOfNotNull(plan.dayOfMonth1, plan.dayOfMonth2).sorted()
        }

        for (monthOffset in 0..14) {
            val month = (after.clone() as Calendar).apply {
                add(Calendar.MONTH, monthOffset)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            for (day in candidates) {
                val candidate = dateForDay(month, day)
                if (candidate.after(after)) return formatDate(candidate)
            }
        }
        error("Could not calculate next savings-plan date")
    }

    fun dueExecutions(plan: SavingsPlan, today: String): List<SavingsPlanExecution> {
        if (!plan.enabled) return emptyList()
        val due = parseDate(plan.nextDueDate)
        val current = parseDate(today)
        if (due.after(current)) return emptyList()
        return listOf(SavingsPlanExecution.pending(plan.id, plan.nextDueDate, plan.amountEur))
    }

    private fun parseDate(value: String): Calendar {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = utc
        }
        val parsed = requireNotNull(format.parse(value)) { "Invalid date: $value" }
        return Calendar.getInstance(utc, Locale.US).apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun dateForDay(month: Calendar, requestedDay: Int): Calendar =
        (month.clone() as Calendar).apply {
            val max = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, requestedDay.coerceAtMost(max))
        }

    private fun formatDate(value: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc }.format(value.time)
}
