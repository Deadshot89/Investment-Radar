package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentBudgetJournalTest {
    @Test
    fun `executed buy is deducted only once`() {
        val entries = listOf(
            BudgetJournalEntry("month-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("buy-msft-1", BudgetJournalType.BUY_DEBIT, 20.0, "2026-09-02", itemId = "msft")
        )
        val summary = InvestmentBudgetJournalEngine.summarize(entries, emptyList())
        assertEquals(80.0, summary.availableEur, 0.0001)
    }

    @Test
    fun `sale proceeds return to available money`() {
        val entries = listOf(
            BudgetJournalEntry("month-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("buy-msft-1", BudgetJournalType.BUY_DEBIT, 40.0, "2026-09-02", itemId = "msft"),
            BudgetJournalEntry("sell-msft-1", BudgetJournalType.SELL_CREDIT, 25.0, "2026-09-08", itemId = "msft")
        )
        val summary = InvestmentBudgetJournalEngine.summarize(entries, emptyList())
        assertEquals(85.0, summary.availableEur, 0.0001)
    }

    @Test
    fun `spare change deposit and purchase cancel each other without consuming monthly budget`() {
        val entries = listOf(
            BudgetJournalEntry("month-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("spare-2026-09-09", BudgetJournalType.EXTRA_DEPOSIT, 6.42, "2026-09-09", source = BudgetJournalSource.SPARE_CHANGE),
            BudgetJournalEntry("buy-spyi-spare-2026-09-09", BudgetJournalType.BUY_DEBIT, 6.42, "2026-09-09", itemId = "spyi", source = BudgetJournalSource.SPARE_CHANGE)
        )
        val summary = InvestmentBudgetJournalEngine.summarize(entries, emptyList())
        assertEquals(100.0, summary.availableEur, 0.0001)
        assertEquals(6.42, summary.extraDepositsEur, 0.0001)
        assertEquals(6.42, summary.executedBuysEur, 0.0001)
    }

    @Test
    fun `reservation reduces spendable amount but remains separate`() {
        val summary = InvestmentBudgetJournalEngine.summarize(
            entries = listOf(BudgetJournalEntry("month-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01")),
            reservations = listOf(BudgetReservation("rec-aapl", "aapl", 30.0))
        )
        assertEquals(70.0, summary.availableEur, 0.0001)
        assertEquals(30.0, summary.reservedEur, 0.0001)
    }

    @Test
    fun `upsert prevents duplicate execution event`() {
        val first = BudgetJournalEntry("buy-1", BudgetJournalType.BUY_DEBIT, 25.0, "2026-09-11", itemId = "aapl")
        val corrected = first.copy(amountEur = 24.75)
        val next = InvestmentBudgetJournalEngine.upsert(listOf(first), corrected)
        assertEquals(1, next.size)
        assertEquals(24.75, next.single().amountEur, 0.0001)
        assertTrue(InvestmentBudgetJournalEngine.containsEvent(next, "buy-1"))
        assertFalse(InvestmentBudgetJournalEngine.containsEvent(next, "missing"))
    }
}
