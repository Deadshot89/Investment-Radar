package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentBudgetCommandsTest {
    @Test
    fun `changing monthly budget replaces previous monthly funding instead of adding it`() {
        val existing = listOf(
            BudgetJournalEntry(
                id = "legacy-monthly-budget-opening",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 100.0,
                date = "2026-09-01",
                source = BudgetJournalSource.SYSTEM
            ),
            BudgetJournalEntry(
                id = "buy-1",
                type = BudgetJournalType.BUY_DEBIT,
                amountEur = 40.0,
                date = "2026-09-05",
                itemId = "msci"
            )
        )

        val updated = InvestmentBudgetCommands.setMonthlyBudget(existing, 150.0, "2026-09-11")
        val summary = InvestmentBudgetJournalEngine.summarize(updated, emptyList())

        assertEquals(1, updated.count { it.type == BudgetJournalType.MONTHLY_DEPOSIT })
        assertEquals(150.0, summary.monthlyDepositsEur, 0.000001)
        assertEquals(110.0, summary.availableEur, 0.000001)
    }

    @Test
    fun `spare change is independent extra funding and duplicate event id is idempotent`() {
        val request = ExtraFundingCommand(
            eventId = "spare-2026-09-09-msci",
            amountEur = 6.42,
            date = "2026-09-09",
            source = BudgetJournalSource.SPARE_CHANGE,
            note = "Wechselgeld"
        )

        val once = InvestmentBudgetCommands.addExtraFunding(emptyList(), request)
        val twice = InvestmentBudgetCommands.addExtraFunding(once, request)
        val summary = InvestmentBudgetJournalEngine.summarize(twice, emptyList())

        assertEquals(1, twice.size)
        assertEquals(6.42, summary.extraDepositsEur, 0.000001)
        assertEquals(BudgetJournalSource.SPARE_CHANGE, twice.single().source)
    }
}
