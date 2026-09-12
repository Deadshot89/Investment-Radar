package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InvestmentBudgetMoneyFlowTest {
    @Test
    fun spareChangePurchaseKeepsMonthlyMoneyUntouched() {
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("extra-change", BudgetJournalType.EXTRA_DEPOSIT, 6.42, "2026-09-09", source = BudgetJournalSource.SPARE_CHANGE),
            BudgetJournalEntry("buy-change", BudgetJournalType.BUY_DEBIT, 6.42, "2026-09-09", "spyi", BudgetJournalSource.SPARE_CHANGE)
        )
        val summary = InvestmentBudgetJournalEngine.summarize(entries, emptyList())
        assertEquals(100.0, summary.cashBalanceEur, 0.000001)
        assertEquals(100.0, summary.availableEur, 0.000001)
    }

    @Test
    fun monthRolloverCarriesRestAndAddsConfiguredBudgetExactlyOnce() {
        val september = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("buy-sep", BudgetJournalType.BUY_DEBIT, 40.0, "2026-09-10", "msft")
        )
        val october = InvestmentBudgetMigration.ensureCurrentMonth(september, 100, "2026-10-01")
        val restarted = InvestmentBudgetMigration.ensureCurrentMonth(october, 100, "2026-10-12")

        assertEquals(3, october.size)
        assertEquals(october, restarted)
        assertEquals(160.0, InvestmentBudgetJournalEngine.summarize(restarted, emptyList()).availableEur, 0.000001)

        val view = InvestmentBudgetViewState.from(
            InvestmentBudgetJournalEngine.summarize(restarted, emptyList()),
            restarted,
            activeInvestedEur = 40.0,
            today = LocalDate.of(2026, 10, 12)
        )
        assertEquals(60.0, view.carryoverEur, 0.000001)
        assertEquals(100.0, view.monthlyBudgetEur, 0.000001)
        assertEquals(160.0, view.availableEur, 0.000001)
        assertEquals("10/2026", view.monthLabel)
    }

    @Test
    fun changingCurrentMonthlyBudgetPreservesPreviousMonths() {
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("monthly-budget-2026-10", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-10-01")
        )
        val changed = InvestmentBudgetCommands.setMonthlyBudget(entries, 150.0, "2026-10-12")
        assertEquals(2, changed.count { it.type == BudgetJournalType.MONTHLY_DEPOSIT })
        assertEquals(100.0, changed.single { InvestmentBudgetDate.monthKey(it.date) == "2026-09" }.amountEur, 0.000001)
        assertEquals(150.0, changed.single { InvestmentBudgetDate.monthKey(it.date) == "2026-10" }.amountEur, 0.000001)
    }

    @Test
    fun partialSaleReturnsOnlyActualNetProceedsAndKeepsFeeExplanatory() {
        val base = PortfolioPosition("msft").upsertPurchaseIfValid(
            PortfolioPurchase("old-buy", "2026-09-01", 100.0, 1.0)
        )!!
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("old-buy", BudgetJournalType.BUY_DEBIT, 100.0, "2026-09-01", "msft")
        )
        val sold = InvestmentBudgetExecutionService.executeSale(
            base,
            entries,
            emptyList(),
            BudgetSaleExecution(
                eventId = "partial-sale",
                itemId = "msft",
                date = "2026-09-12",
                proceedsEur = 59.0,
                shares = 0.5,
                source = BudgetJournalSource.MANUAL,
                feeEur = 1.0
            )
        )
        assertNull(sold.error)
        assertEquals(0.5, sold.position!!.shares, 0.000001)
        assertEquals(59.0, InvestmentBudgetJournalEngine.summarize(sold.entries, emptyList()).availableEur, 0.000001)
        assertEquals(1.0, sold.entries.single { it.id == "partial-sale" }.feeEur, 0.000001)
    }

    @Test
    fun manualCorrectionExplainsAndChangesCashOnce() {
        val entries = listOf(
            BudgetJournalEntry("month", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01")
        )
        val credited = InvestmentBudgetCommands.addAdjustment(
            entries,
            BudgetAdjustmentCommand("fix-plus", 3.5, "2026-09-12", true, "Broker-Rundung")
        )
        val debited = InvestmentBudgetCommands.addAdjustment(
            credited,
            BudgetAdjustmentCommand("fix-minus", 1.0, "2026-09-12", false, "Gebühr nachgetragen")
        )
        assertEquals(102.5, InvestmentBudgetJournalEngine.summarize(debited, emptyList()).availableEur, 0.000001)
        assertTrue(debited.any { it.id == "fix-plus" && it.note == "Broker-Rundung" })
        assertTrue(debited.any { it.id == "fix-minus" && it.note == "Gebühr nachgetragen" })
    }

    @Test
    fun activeInvestedAmountIsIndependentFromHistoricalBudgetDebits() {
        val entries = listOf(
            BudgetJournalEntry("month", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("new-buy", BudgetJournalType.BUY_DEBIT, 20.0, "2026-09-12", "etf")
        )
        val summary = InvestmentBudgetJournalEngine.summarize(entries, emptyList())
        val view = InvestmentBudgetViewState.from(summary, entries, activeInvestedEur = 845.0, today = LocalDate.of(2026, 9, 12))
        assertEquals(845.0, view.investedEur, 0.000001)
        assertEquals(80.0, view.availableEur, 0.000001)
    }

    @Test
    fun oldPersistedJournalWithoutFeeRemainsReadable() {
        val raw = """[{"id":"buy-old","type":"BUY_DEBIT","amountEur":20.0,"date":"2026-09-10","itemId":"msft","source":"MANUAL","note":"alt"}]"""
        val decoded = InvestmentBudgetCodec.decodeEntries(raw)
        assertEquals(1, decoded.size)
        assertEquals(0.0, decoded.single().feeEur, 0.000001)
    }
}
