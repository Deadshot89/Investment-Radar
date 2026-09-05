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
    fun confirmedTradeRepublicExecutionDaysAreSeeded() {
        val plans = SavingsPlanStore.initialPlans()

        plans.filter { it.frequency == SavingsPlanFrequency.TWICE_MONTHLY }.forEach { plan ->
            assertEquals(1, plan.dayOfMonth1)
            assertEquals(15, plan.dayOfMonth2)
            assertNull(plan.nextDueDate)
        }

        val microsoft = plans.single { it.itemId == "msft" }
        assertEquals(1, microsoft.dayOfMonth1)
        assertNull(microsoft.dayOfMonth2)
        assertNull(microsoft.nextDueDate)
    }

    @Test
    fun migrationAddsConfirmedDatesToExistingBlankSchedules() {
        val oldPlans = SavingsPlanStore.initialPlans().map {
            it.copy(dayOfMonth1 = null, dayOfMonth2 = null, nextDueDate = null)
        }

        val migrated = SavingsPlanStore.applyConfirmedDefaultSchedules(oldPlans, "2026-09-05")

        migrated.filter { it.frequency == SavingsPlanFrequency.TWICE_MONTHLY }.forEach { plan ->
            assertEquals(1, plan.dayOfMonth1)
            assertEquals(15, plan.dayOfMonth2)
            assertEquals("2026-09-15", plan.nextDueDate)
        }
        assertEquals("2026-10-01", migrated.single { it.itemId == "msft" }.nextDueDate)
    }

    @Test
    fun migrationPreservesUserEditedSchedule() {
        val editedMeta = SavingsPlanStore.initialPlans().first { it.itemId == "meta" }.copy(
            dayOfMonth1 = 3,
            dayOfMonth2 = 17,
            nextDueDate = "2026-09-17"
        )

        val migrated = SavingsPlanStore.applyConfirmedDefaultSchedules(listOf(editedMeta), "2026-09-05")

        assertEquals(3, migrated.single().dayOfMonth1)
        assertEquals(17, migrated.single().dayOfMonth2)
        assertEquals("2026-09-17", migrated.single().nextDueDate)
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
