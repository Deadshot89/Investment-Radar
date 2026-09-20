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
        assertEquals(100.0, view.monthlyAvailableEur, 0.000001)
        assertEquals(100, view.advisorBudgetEur)
        assertEquals("10/2026", view.monthLabel)
    }


    @Test
    fun priorCarryoverNeverExpandsCurrentMonthBuyRecommendations() {
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-08", BudgetJournalType.MONTHLY_DEPOSIT, 500.0, "2026-08-01"),
            BudgetJournalEntry("buy-aug", BudgetJournalType.BUY_DEBIT, 20.0, "2026-08-05", "msft"),
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("buy-sep", BudgetJournalType.BUY_DEBIT, 40.0, "2026-09-10", "meta")
        )
        val summary = InvestmentBudgetJournalEngine.summarize(entries, emptyList())
        val view = InvestmentBudgetViewState.from(
            summary = summary,
            entries = entries,
            activeInvestedEur = 60.0,
            today = LocalDate.of(2026, 9, 18)
        )

        assertEquals(540.0, view.availableEur, 0.000001)
        assertEquals(480.0, view.carryoverEur, 0.000001)
        assertEquals(60.0, view.monthlyAvailableEur, 0.000001)
        assertEquals(60, view.advisorBudgetEur)
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
    fun partialSaleRecordsActualNetProceedsWithoutReturningThemToAppCash() {
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
        val saleSummary = InvestmentBudgetJournalEngine.summarize(sold.entries, emptyList())
        assertEquals(0.0, saleSummary.availableEur, 0.000001)
        assertEquals(59.0, saleSummary.saleCreditsEur, 0.000001)
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

    @Test
    fun currentMonthPortfolioPurchaseMissingFromJournalIsReconciledExactlyOnce() {
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01")
        )
        val positions = mapOf(
            "msft" to PortfolioPosition("msft").upsertPurchaseIfValid(
                PortfolioPurchase("confirmed-buy", "2026-09-12", 35.0, 0.1)
            )!!,
            "old" to PortfolioPosition("old").upsertPurchaseIfValid(
                PortfolioPurchase("historic-buy", "2026-08-15", 500.0, 1.0)
            )!!
        )

        val reconciled = InvestmentBudgetMigration.reconcileCurrentMonthTransactions(
            existing = entries,
            positions = positions,
            today = LocalDate.of(2026, 9, 13)
        )
        val restarted = InvestmentBudgetMigration.reconcileCurrentMonthTransactions(
            existing = reconciled,
            positions = positions,
            today = LocalDate.of(2026, 9, 13)
        )

        assertEquals(65.0, InvestmentBudgetJournalEngine.summarize(reconciled, emptyList()).availableEur, 0.000001)
        assertTrue(reconciled.any { it.id == "confirmed-buy" && it.type == BudgetJournalType.BUY_DEBIT })
        assertTrue(reconciled.none { it.id == "historic-buy" })
        assertEquals(reconciled, restarted)
    }
    @Test
    fun liquidityNeedKeepsSaleProceedsPrivateAndWithdrawsOnlyExistingCash() {
        val position = PortfolioPosition("weak").upsertPurchaseIfValid(
            PortfolioPurchase("buy-weak", "2026-09-01", 100.0, 10.0)
        )!!
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 150.0, "2026-09-01"),
            BudgetJournalEntry("buy-weak", BudgetJournalType.BUY_DEBIT, 100.0, "2026-09-01", "weak")
        )

        val plan = LiquidityNeedEngine.plan(
            requestedEur = 120.0,
            availableCashEur = InvestmentBudgetJournalEngine.summarize(entries, emptyList()).availableEur,
            holdings = listOf(
                LiquidityHolding(
                    itemId = "weak",
                    instrumentName = "Weak Holding",
                    currentValueEur = 100.0,
                    shares = 10.0,
                    advisorAction = PortfolioAdvisorAction.VERKAUFEN,
                    advisorScore = 30,
                    dataReliable = true,
                    forecastDirection = "DOWN",
                    profitLossPct = 0.0
                )
            )
        )

        assertEquals(50.0, plan.cashUsedEur, 0.000001)
        assertEquals(70.0, plan.saleNeededEur, 0.000001)
        val suggestion = plan.suggestions.single()
        assertEquals(70.0, suggestion.amountEur, 0.000001)
        assertEquals(7.0, suggestion.shares ?: 0.0, 0.000001)

        val sold = InvestmentBudgetExecutionService.executeSale(
            position = position,
            entries = entries,
            reservations = emptyList(),
            request = BudgetSaleExecution(
                eventId = "liquidity-sale",
                itemId = suggestion.itemId,
                date = "2026-09-18",
                proceedsEur = suggestion.amountEur,
                shares = suggestion.shares!!,
                source = BudgetJournalSource.RECOMMENDATION
            )
        )

        assertNull(sold.error)
        assertEquals(3.0, sold.position!!.shares, 0.000001)
        val soldSummary = InvestmentBudgetJournalEngine.summarize(sold.entries, sold.reservations)
        assertEquals(50.0, soldSummary.availableEur, 0.000001)
        assertEquals(70.0, soldSummary.saleCreditsEur, 0.000001)

        val withdrawn = InvestmentBudgetCommands.addAdjustment(
            sold.entries,
            BudgetAdjustmentCommand(
                eventId = "liquidity-withdrawal",
                amountEur = plan.cashUsedEur,
                date = "2026-09-18",
                credit = false,
                note = "Auszahlung / Geldbedarf"
            )
        )

        assertEquals(0.0, InvestmentBudgetJournalEngine.summarize(withdrawn, sold.reservations).availableEur, 0.000001)
        assertTrue(withdrawn.any {
            it.id == "liquidity-withdrawal" &&
                it.type == BudgetJournalType.ADJUSTMENT_DEBIT &&
                it.note == "Auszahlung / Geldbedarf"
        })
    }

    @Test
    fun currentMonthExtraCashNeverTurnsIntoBuyBudget() {
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("extra-500", BudgetJournalType.EXTRA_DEPOSIT, 500.0, "2026-09-05")
        )
        val view = InvestmentBudgetViewState.from(
            InvestmentBudgetJournalEngine.summarize(entries, emptyList()),
            entries,
            today = LocalDate.of(2026, 9, 18)
        )

        assertEquals(600.0, view.availableEur, 0.000001)
        assertEquals(100.0, view.monthlyAvailableEur, 0.000001)
        assertEquals(100, view.advisorBudgetEur)
    }

    @Test
    fun saleCreditNeverTurnsIntoAppCashOrNewBuyBudget() {
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("buy-month", BudgetJournalType.BUY_DEBIT, 40.0, "2026-09-03", "msft"),
            BudgetJournalEntry("sale-500", BudgetJournalType.SELL_CREDIT, 500.0, "2026-09-10", "old")
        )
        val view = InvestmentBudgetViewState.from(
            InvestmentBudgetJournalEngine.summarize(entries, emptyList()),
            entries,
            today = LocalDate.of(2026, 9, 18)
        )

        assertEquals(60.0, view.availableEur, 0.000001)
        assertEquals(60.0, view.monthlyAvailableEur, 0.000001)
        assertEquals(60, view.advisorBudgetEur)
    }

    @Test
    fun correctionCreditNeverTurnsIntoNewBuyBudget() {
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("correction-500", BudgetJournalType.ADJUSTMENT_CREDIT, 500.0, "2026-09-12")
        )
        val view = InvestmentBudgetViewState.from(
            InvestmentBudgetJournalEngine.summarize(entries, emptyList()),
            entries,
            today = LocalDate.of(2026, 9, 18)
        )

        assertEquals(600.0, view.availableEur, 0.000001)
        assertEquals(100.0, view.monthlyAvailableEur, 0.000001)
        assertEquals(100, view.advisorBudgetEur)
    }

    @Test
    fun spareChangeInvestmentDoesNotConsumeMonthlyBuyBudget() {
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("extra-change", BudgetJournalType.EXTRA_DEPOSIT, 6.42, "2026-09-09", source = BudgetJournalSource.SPARE_CHANGE),
            BudgetJournalEntry("buy-change", BudgetJournalType.BUY_DEBIT, 6.42, "2026-09-09", "spyi", BudgetJournalSource.SPARE_CHANGE)
        )
        val view = InvestmentBudgetViewState.from(
            InvestmentBudgetJournalEngine.summarize(entries, emptyList()),
            entries,
            today = LocalDate.of(2026, 9, 18)
        )

        assertEquals(100.0, view.monthlyAvailableEur, 0.000001)
        assertEquals(100, view.advisorBudgetEur)
    }

    @Test
    fun withdrawalCanLowerBuyBudgetButNeverRaiseIt() {
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("withdrawal", BudgetJournalType.ADJUSTMENT_DEBIT, 80.0, "2026-09-15")
        )
        val view = InvestmentBudgetViewState.from(
            InvestmentBudgetJournalEngine.summarize(entries, emptyList()),
            entries,
            today = LocalDate.of(2026, 9, 18)
        )

        assertEquals(20.0, view.availableEur, 0.000001)
        assertEquals(20.0, view.monthlyAvailableEur, 0.000001)
        assertEquals(20, view.advisorBudgetEur)
    }

}
