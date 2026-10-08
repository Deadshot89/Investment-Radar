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
    fun `current month initialization removes duplicate legacy budget deposits`() {
        val existing = listOf(
            BudgetJournalEntry(
                id = "monthly-budget-2026-10",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 100.0,
                date = "2026-10-01",
                source = BudgetJournalSource.SYSTEM
            ),
            BudgetJournalEntry(
                id = "legacy-duplicate-budget",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 100.0,
                date = "01.10.2026",
                source = BudgetJournalSource.SYSTEM
            ),
            BudgetJournalEntry(
                id = "buy-meta",
                type = BudgetJournalType.BUY_DEBIT,
                amountEur = 25.0,
                date = "2026-10-02",
                itemId = "meta"
            )
        )

        val migrated = InvestmentBudgetMigration.ensureCurrentMonth(
            existing = existing,
            configuredMonthlyBudgetEur = 100.0,
            date = "2026-10-08"
        )

        val currentMonthBudgets = migrated.filter {
            it.type == BudgetJournalType.MONTHLY_DEPOSIT &&
                InvestmentBudgetDate.monthKey(it.date) == "2026-10"
        }
        assertEquals(1, currentMonthBudgets.size)
        assertEquals(100.0, currentMonthBudgets.single().amountEur, 0.0001)
        assertEquals(1, migrated.count { it.id == "buy-meta" })

        val summary = InvestmentBudgetJournalEngine.summarize(migrated, emptyList())
        assertEquals(75.0, summary.availableEur, 0.0001)
    }

    @Test
    fun `current month duplicate cleanup preserves explicit manual budget`() {
        val existing = listOf(
            BudgetJournalEntry(
                id = "legacy-system-budget",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 100.0,
                date = "2026-10-01",
                source = BudgetJournalSource.SYSTEM
            ),
            BudgetJournalEntry(
                id = "manual-budget",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 125.50,
                date = "2026-10-03",
                source = BudgetJournalSource.MANUAL,
                note = "Bewusst angepasst"
            )
        )

        val migrated = InvestmentBudgetMigration.ensureCurrentMonth(
            existing = existing,
            configuredMonthlyBudgetEur = 100.0,
            date = "2026-10-08"
        )

        val currentMonthBudgets = migrated.filter {
            it.type == BudgetJournalType.MONTHLY_DEPOSIT &&
                InvestmentBudgetDate.monthKey(it.date) == "2026-10"
        }
        assertEquals(1, currentMonthBudgets.size)
        assertEquals(125.50, currentMonthBudgets.single().amountEur, 0.0001)
        assertEquals(BudgetJournalSource.MANUAL, currentMonthBudgets.single().source)
        assertEquals("Bewusst angepasst", currentMonthBudgets.single().note)
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
    fun `manual 500 euro current monthly budget is preserved`() {
        // Regression: the one-time repair must never overwrite a deliberate user-entered budget.
        val existing = listOf(
            BudgetJournalEntry(
                id = "monthly-budget-2026-10",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 500.0,
                date = "2026-10-01",
                source = BudgetJournalSource.MANUAL,
                note = "Monatsbudget Oktober 2026"
            )
        )

        val repair = InvestmentBudgetMigration.repairKnownIncorrectFiveHundredBudget(
            existing = existing,
            configuredMonthlyBudgetEur = 500,
            date = "2026-10-06"
        )

        assertTrue(!repair.repaired)
        assertEquals(500, repair.configuredMonthlyBudgetEur)
        assertEquals(existing, repair.entries)
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
