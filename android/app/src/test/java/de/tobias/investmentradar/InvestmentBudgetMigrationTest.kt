package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentBudgetMigrationTest {
    @Test
    fun `legacy monthly budget becomes opening monthly deposit only when journal is empty`() {
        val migrated = InvestmentBudgetMigration.seedIfEmpty(
            existing = emptyList(),
            legacyMonthlyBudgetEur = 100,
            date = "2026-09-11"
        )

        assertEquals(1, migrated.size)
        assertEquals(BudgetJournalType.MONTHLY_DEPOSIT, migrated.single().type)
        assertEquals(100.0, migrated.single().amountEur, 0.0001)
        assertEquals(BudgetJournalSource.SYSTEM, migrated.single().source)

        val existing = listOf(
            BudgetJournalEntry(
                id = "existing",
                type = BudgetJournalType.EXTRA_DEPOSIT,
                amountEur = 5.0,
                date = "2026-09-10"
            )
        )
        assertEquals(existing, InvestmentBudgetMigration.seedIfEmpty(existing, 100, "2026-09-11"))
    }

    @Test
    fun `invalid legacy budget does not create money`() {
        assertTrue(InvestmentBudgetMigration.seedIfEmpty(emptyList(), 0, "2026-09-11").isEmpty())
        assertTrue(InvestmentBudgetMigration.seedIfEmpty(emptyList(), -5, "2026-09-11").isEmpty())
    }
    @Test
    fun `known wrong 500 current monthly budget is repaired to 100`() {
        val existing = listOf(
            BudgetJournalEntry(
                id = "monthly-budget-2026-09",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 500.0,
                date = "2026-09-01",
                source = BudgetJournalSource.SYSTEM
            )
        )

        val repair = InvestmentBudgetMigration.repairKnownIncorrectFiveHundredBudget(
            existing = existing,
            configuredMonthlyBudgetEur = 500,
            date = "2026-09-18"
        )

        assertTrue(repair.repaired)
        assertEquals(100, repair.configuredMonthlyBudgetEur)
        assertEquals(100.0, repair.entries.single().amountEur, 0.0001)
        assertTrue(repair.entries.single().note.contains("500 € war kein Kaufbudget"))
    }

    @Test
    fun `old 500 euro history is not rewritten when current budget is 100`() {
        val existing = listOf(
            BudgetJournalEntry(
                id = "monthly-budget-2026-08",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 500.0,
                date = "2026-08-01"
            ),
            BudgetJournalEntry(
                id = "monthly-budget-2026-09",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 100.0,
                date = "2026-09-01"
            )
        )

        val repair = InvestmentBudgetMigration.repairKnownIncorrectFiveHundredBudget(
            existing = existing,
            configuredMonthlyBudgetEur = 100,
            date = "2026-09-18"
        )

        assertTrue(!repair.repaired)
        assertEquals(existing, repair.entries)
        assertEquals(100, repair.configuredMonthlyBudgetEur)
    }

}
