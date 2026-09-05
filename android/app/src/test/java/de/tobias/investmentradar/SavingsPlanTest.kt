package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsPlanTest {
    @Test
    fun monthlyPlanMovesToNextMonthAndClampsEndOfMonth() {
        val plan = SavingsPlan(
            id = "monthly",
            name = "Microsoft",
            itemId = "msft",
            amountEur = 10.0,
            frequency = SavingsPlanFrequency.MONTHLY,
            dayOfMonth1 = 31,
            dayOfMonth2 = null,
            nextDueDate = "2026-09-30",
            enabled = true
        )

        assertEquals("2026-10-31", SavingsPlanSchedule.nextDueDate(plan, "2026-09-30"))
        assertEquals("2026-11-30", SavingsPlanSchedule.nextDueDate(plan, "2026-10-31"))
    }

    @Test
    fun twiceMonthlyPlanKeepsTwoDistinctSlots() {
        val plan = SavingsPlan(
            id = "twice",
            name = "Meta Platforms (A)",
            itemId = "meta",
            amountEur = 10.0,
            frequency = SavingsPlanFrequency.TWICE_MONTHLY,
            dayOfMonth1 = 2,
            dayOfMonth2 = 16,
            nextDueDate = "2026-09-02",
            enabled = true
        )

        assertEquals("2026-09-16", SavingsPlanSchedule.nextDueDate(plan, "2026-09-02"))
        assertEquals("2026-10-02", SavingsPlanSchedule.nextDueDate(plan, "2026-09-16"))
    }

    @Test
    fun executionIdIsStableForPlanAndScheduledDate() {
        val first = SavingsPlanExecution.pending("plan-a", "2026-09-16", 10.0)
        val second = SavingsPlanExecution.pending("plan-a", "2026-09-16", 10.0)
        assertEquals(first.id, second.id)
        assertEquals("plan-a@2026-09-16", first.id)
    }

    @Test
    fun disabledPlanProducesNoDueExecution() {
        val plan = SavingsPlan(
            id = "disabled",
            name = "Samsung (GDR)",
            itemId = "custom-samsung-gdr",
            amountEur = 10.0,
            frequency = SavingsPlanFrequency.TWICE_MONTHLY,
            dayOfMonth1 = 2,
            dayOfMonth2 = 16,
            nextDueDate = "2026-09-02",
            enabled = false
        )

        assertTrue(SavingsPlanSchedule.dueExecutions(plan, "2026-09-16").isEmpty())
    }
}
