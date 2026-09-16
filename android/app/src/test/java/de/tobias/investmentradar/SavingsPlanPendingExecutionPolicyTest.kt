package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsPlanPendingExecutionPolicyTest {
    @Test
    fun `all pending executions stay actionable oldest first`() {
        val executions = listOf(
            SavingsPlanExecution.pending("plan-1", "2026-09-15", 25.0),
            SavingsPlanExecution.pending("plan-1", "2026-08-15", 25.0),
            SavingsPlanExecution(
                id = "plan-1@2026-07-15",
                planId = "plan-1",
                scheduledDate = "2026-07-15",
                amountEur = 25.0,
                status = SavingsPlanExecutionStatus.CONFIRMED
            )
        )

        val pending = SavingsPlanPendingExecutionPolicy.pendingForPlan(executions, "plan-1")

        assertEquals(listOf("2026-08-15", "2026-09-15"), pending.map { it.scheduledDate })
    }

    @Test
    fun `past pending execution is labelled missed`() {
        assertEquals(
            "Verpasste Ausführung",
            SavingsPlanPendingExecutionPolicy.label("2026-09-15", "2026-09-16")
        )
        assertEquals(
            "Fällige Ausführung",
            SavingsPlanPendingExecutionPolicy.label("2026-09-16", "2026-09-16")
        )
    }

    @Test
    fun `pending execution remains confirmable after due date`() {
        val execution = SavingsPlanExecution.pending("plan-1", "2026-08-15", 25.0)
        assertTrue(SavingsPlanPendingExecutionPolicy.canConfirm(execution, "2026-09-16"))
    }
}
