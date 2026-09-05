package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsPlanStoreTest {
    @Test
    fun initialSeedContainsFiveSeparateTradeRepublicPlans() {
        val plans = SavingsPlanStore.initialPlans()

        assertEquals(5, plans.size)
        assertEquals(1, plans.count { it.itemId == "meta" && it.amountEur == 10.0 && it.frequency == SavingsPlanFrequency.TWICE_MONTHLY })
        assertEquals(1, plans.count { it.itemId == "custom-samsung-gdr" && it.amountEur == 10.0 && it.frequency == SavingsPlanFrequency.TWICE_MONTHLY })
        assertEquals(1, plans.count { it.itemId == "msft" && it.amountEur == 10.0 && it.frequency == SavingsPlanFrequency.MONTHLY })

        val privateEquity = plans.filter { it.name == "Private Equity" }
        assertEquals(2, privateEquity.size)
        assertEquals(2, privateEquity.map { it.id }.distinct().size)
        assertTrue(privateEquity.all { it.amountEur == 5.0 && it.frequency == SavingsPlanFrequency.TWICE_MONTHLY })
        assertTrue(privateEquity.all { it.itemId == null })
    }

    @Test
    fun screenshotSeedDoesNotInventExecutionDates() {
        SavingsPlanStore.initialPlans().forEach { plan ->
            assertNull(plan.dayOfMonth1)
            assertNull(plan.dayOfMonth2)
            assertNull(plan.nextDueDate)
            assertTrue(!plan.scheduleConfigured)
        }
    }

    @Test
    fun mergingSeedNeverDuplicatesExistingPlanIds() {
        val seed = SavingsPlanStore.initialPlans()
        val editedMeta = seed.first { it.itemId == "meta" }.copy(amountEur = 25.0)
        val merged = SavingsPlanStore.mergeSeed(listOf(editedMeta))

        assertEquals(5, merged.size)
        assertEquals(25.0, merged.first { it.id == editedMeta.id }.amountEur, 0.0)
    }
}
