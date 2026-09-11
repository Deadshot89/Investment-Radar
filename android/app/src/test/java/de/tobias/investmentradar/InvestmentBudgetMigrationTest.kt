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
}
