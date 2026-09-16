package de.tobias.investmentradar

object SavingsPlanPendingExecutionPolicy {
    fun pendingForPlan(
        executions: List<SavingsPlanExecution>,
        planId: String
    ): List<SavingsPlanExecution> = executions
        .asSequence()
        .filter { it.planId == planId && it.status == SavingsPlanExecutionStatus.PENDING }
        .sortedBy { it.scheduledDate }
        .toList()

    fun label(scheduledDate: String, today: String): String =
        if (scheduledDate < today) "Verpasste Ausführung" else "Fällige Ausführung"

    fun canConfirm(execution: SavingsPlanExecution, today: String): Boolean =
        execution.status == SavingsPlanExecutionStatus.PENDING && execution.scheduledDate <= today
}
