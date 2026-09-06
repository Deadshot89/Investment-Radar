package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SavingsPlanBudgetTest {
    @Test
    fun monthlyPlanCountsOnceAndTwiceMonthlyCountsTwice() {
        val result = SavingsPlanBudget.monthlyAmounts(
            listOf(
                plan("monthly", "msft", 10.0, SavingsPlanFrequency.MONTHLY),
                plan("twice", "meta", 10.0, SavingsPlanFrequency.TWICE_MONTHLY)
            )
        )

        assertEquals(10, result["msft"])
        assertEquals(20, result["meta"])
    }

    @Test
    fun multiplePlansForSameInstrumentAreCombinedBeforeWholeEuroRounding() {
        val result = SavingsPlanBudget.monthlyAmounts(
            listOf(
                plan("a", "meta", 2.4, SavingsPlanFrequency.MONTHLY),
                plan("b", "meta", 2.4, SavingsPlanFrequency.MONTHLY)
            )
        )

        assertEquals(5, result["meta"])
    }

    @Test
    fun disabledPlansAndUnmappedPlansDoNotConsumeAdvisorBudget() {
        val result = SavingsPlanBudget.monthlyAmounts(
            listOf(
                plan("disabled", "meta", 20.0, SavingsPlanFrequency.MONTHLY, enabled = false),
                plan("unmapped", null, 5.0, SavingsPlanFrequency.TWICE_MONTHLY),
                plan("active", "msft", 10.0, SavingsPlanFrequency.MONTHLY)
            )
        )

        assertFalse("meta" in result)
        assertEquals(10, result["msft"])
        assertEquals(1, result.size)
    }

    @Test
    fun negativeOrInvalidAmountsCannotEnterBudgetMapBecauseSavingsPlanGuardsThem() {
        val result = SavingsPlanBudget.monthlyAmounts(
            listOf(plan("active", "meta", 5.0, SavingsPlanFrequency.TWICE_MONTHLY))
        )

        assertEquals(10, result.values.sum())
    }

    private fun plan(
        id: String,
        itemId: String?,
        amount: Double,
        frequency: SavingsPlanFrequency,
        enabled: Boolean = true
    ) = SavingsPlan(
        id = id,
        name = id,
        itemId = itemId,
        amountEur = amount,
        frequency = frequency,
        dayOfMonth1 = 1,
        dayOfMonth2 = if (frequency == SavingsPlanFrequency.TWICE_MONTHLY) 15 else null,
        nextDueDate = "2026-10-01",
        enabled = enabled
    )
}
