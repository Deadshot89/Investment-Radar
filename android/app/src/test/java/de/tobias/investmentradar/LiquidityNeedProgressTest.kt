package de.tobias.investmentradar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidityNeedProgressTest {

    @Test
    fun `private sale plus cash withdrawal covers one liquidity need exactly once`() {
        val flowTag = "Geldbedarf:test-flow"
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 150.0, "2026-09-01"),
            BudgetJournalEntry("buy-weak", BudgetJournalType.BUY_DEBIT, 100.0, "2026-09-01", "weak"),
            BudgetJournalEntry(
                "liquidity-sale",
                BudgetJournalType.SELL_CREDIT,
                70.0,
                "2026-09-20",
                "weak",
                note = "$flowTag · privater Verkaufserlös"
            ),
            BudgetJournalEntry(
                "liquidity-withdrawal",
                BudgetJournalType.ADJUSTMENT_DEBIT,
                50.0,
                "2026-09-20",
                note = "Auszahlung / Geldbedarf · $flowTag"
            )
        )

        val summary = InvestmentBudgetJournalEngine.summarize(entries, emptyList())
        val view = InvestmentBudgetViewState.from(
            summary = summary,
            entries = entries,
            today = LocalDate.of(2026, 9, 20)
        )
        val progress = LiquidityNeedEngine.progress(120.0, view.history, flowTag)

        assertEquals(0.0, summary.availableEur, 0.000001)
        assertEquals(70.0, summary.saleCreditsEur, 0.000001)
        assertEquals(70.0, progress.privateSaleCoveredEur, 0.000001)
        assertEquals(50.0, progress.withdrawnCashEur, 0.000001)
        assertEquals(0.0, progress.remainingEur, 0.000001)
        assertTrue(progress.complete)
    }

    @Test
    fun `confirmed private sale leaves only existing app cash to withdraw`() {
        val flowTag = "Geldbedarf:sale-first"
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 150.0, "2026-09-01"),
            BudgetJournalEntry("buy-weak", BudgetJournalType.BUY_DEBIT, 100.0, "2026-09-01", "weak"),
            BudgetJournalEntry(
                "liquidity-sale",
                BudgetJournalType.SELL_CREDIT,
                70.0,
                "2026-09-20",
                "weak",
                note = "$flowTag · privater Verkaufserlös"
            )
        )
        val summary = InvestmentBudgetJournalEngine.summarize(entries, emptyList())
        val view = InvestmentBudgetViewState.from(summary, entries, today = LocalDate.of(2026, 9, 20))
        val progress = LiquidityNeedEngine.progress(120.0, view.history, flowTag)
        val next = LiquidityNeedEngine.plan(
            requestedEur = progress.remainingEur,
            availableCashEur = summary.availableEur,
            holdings = listOf(
                LiquidityHolding(
                    itemId = "weak",
                    instrumentName = "Weak Holding",
                    currentValueEur = 30.0,
                    shares = 3.0,
                    advisorAction = PortfolioAdvisorAction.VERKAUFEN,
                    advisorScore = 30,
                    dataReliable = true,
                    forecastDirection = "DOWN",
                    profitLossPct = 0.0
                )
            )
        )

        assertEquals(50.0, summary.availableEur, 0.000001)
        assertEquals(50.0, progress.remainingEur, 0.000001)
        assertEquals(50.0, next.cashUsedEur, 0.000001)
        assertEquals(0.0, next.saleNeededEur, 0.000001)
        assertTrue(next.suggestions.isEmpty())
        assertFalse(progress.complete)
    }

    @Test
    fun `cash withdrawal first leaves only private sale gap`() {
        val flowTag = "Geldbedarf:cash-first"
        val entries = listOf(
            BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 150.0, "2026-09-01"),
            BudgetJournalEntry("buy-weak", BudgetJournalType.BUY_DEBIT, 100.0, "2026-09-01", "weak"),
            BudgetJournalEntry(
                "liquidity-withdrawal",
                BudgetJournalType.ADJUSTMENT_DEBIT,
                50.0,
                "2026-09-20",
                note = "Auszahlung / Geldbedarf · $flowTag"
            )
        )
        val summary = InvestmentBudgetJournalEngine.summarize(entries, emptyList())
        val view = InvestmentBudgetViewState.from(summary, entries, today = LocalDate.of(2026, 9, 20))
        val progress = LiquidityNeedEngine.progress(120.0, view.history, flowTag)
        val next = LiquidityNeedEngine.plan(
            requestedEur = progress.remainingEur,
            availableCashEur = summary.availableEur,
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

        assertEquals(0.0, summary.availableEur, 0.000001)
        assertEquals(70.0, progress.remainingEur, 0.000001)
        assertEquals(0.0, next.cashUsedEur, 0.000001)
        assertEquals(70.0, next.saleNeededEur, 0.000001)
        assertEquals(70.0, next.suggestions.single().amountEur, 0.000001)
    }

    @Test
    fun `liquidity sale note survives later sale edit`() {
        val flowTag = "Geldbedarf:edit-sale"
        val base = PortfolioPosition("weak").upsertPurchaseIfValid(
            PortfolioPurchase("buy-old", "2026-09-01", 100.0, 10.0)
        )!!
        val sold = InvestmentBudgetExecutionService.executeSale(
            position = base,
            entries = listOf(
                BudgetJournalEntry("monthly-budget-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01")
            ),
            reservations = emptyList(),
            request = BudgetSaleExecution(
                eventId = "sale-flow",
                itemId = "weak",
                date = "2026-09-20",
                proceedsEur = 40.0,
                shares = 4.0,
                source = BudgetJournalSource.MANUAL,
                note = "$flowTag · privater Verkaufserlös"
            )
        )

        val revised = InvestmentBudgetExecutionService.reviseSale(
            position = sold.position!!,
            entries = sold.entries,
            sale = PortfolioSale("sale-flow", "2026-09-20", 42.0, 4.0)
        )

        assertEquals("$flowTag · privater Verkaufserlös", revised.entries.single { it.id == "sale-flow" }.note)
        assertEquals(42.0, revised.entries.single { it.id == "sale-flow" }.amountEur, 0.000001)
    }

    @Test
    fun `unrelated sale does not cover active liquidity need`() {
        val activeTag = "Geldbedarf:active"
        val history = listOf(
            BudgetHistoryItem(
                id = "other-sale",
                date = "2026-09-20",
                title = "Verkauf",
                amountEur = 100.0,
                isCredit = true,
                itemId = "other",
                note = "Geldbedarf:other · privater Verkaufserlös"
            )
        )

        val progress = LiquidityNeedEngine.progress(120.0, history, activeTag)

        assertEquals(0.0, progress.coveredEur, 0.000001)
        assertEquals(120.0, progress.remainingEur, 0.000001)
    }
}
