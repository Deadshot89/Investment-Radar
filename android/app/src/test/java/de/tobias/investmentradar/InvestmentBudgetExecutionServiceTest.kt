package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentBudgetExecutionServiceTest {
    @Test
    fun `confirmed recommendation buy updates portfolio cash and reservation atomically`() {
        val position = PortfolioPosition(itemId = "msft", investedAmount = 0.0, shares = 0.0)
        val entries = listOf(BudgetJournalEntry("month-2026-09", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "01.09.2026"))
        val reservations = listOf(BudgetReservation("rec-msft", "msft", 20.0))

        val result = InvestmentBudgetExecutionService.executeBuy(
            position = position,
            entries = entries,
            reservations = reservations,
            request = BudgetBuyExecution(
                eventId = "buy-msft-2026-09-11",
                reservationId = "rec-msft",
                itemId = "msft",
                date = "11.09.2026",
                amountEur = 20.0,
                shares = 0.05,
                source = BudgetJournalSource.RECOMMENDATION
            )
        )

        assertNull(result.error)
        assertEquals(0.05, result.position?.shares ?: 0.0, 0.000001)
        assertEquals(80.0, InvestmentBudgetJournalEngine.summarize(result.entries, result.reservations).availableEur, 0.000001)
        assertTrue(result.reservations.isEmpty())
        assertEquals(1, result.entries.count { it.id == "buy-msft-2026-09-11" })
    }

    @Test
    fun `buy above available cash is rejected`() {
        val result = InvestmentBudgetExecutionService.executeBuy(
            position = PortfolioPosition(itemId = "aapl", investedAmount = 0.0, shares = 0.0),
            entries = listOf(BudgetJournalEntry("month", BudgetJournalType.MONTHLY_DEPOSIT, 10.0, "01.09.2026")),
            reservations = emptyList(),
            request = BudgetBuyExecution("too-large", null, "aapl", "11.09.2026", 25.0, 0.1, BudgetJournalSource.MANUAL)
        )
        assertEquals("Nicht genügend verfügbares Budget", result.error)
        assertEquals(10.0, InvestmentBudgetJournalEngine.summarize(result.entries, result.reservations).availableEur, 0.000001)
        assertEquals(0.0, result.position?.shares ?: 0.0, 0.000001)
    }

    @Test
    fun `confirmed sale credits proceeds and removes shares`() {
        val position = PortfolioPosition(itemId = "msft", investedAmount = 40.0, shares = 1.0)
        val result = InvestmentBudgetExecutionService.executeSale(
            position = position,
            entries = listOf(BudgetJournalEntry("month", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "01.09.2026")),
            reservations = emptyList(),
            request = BudgetSaleExecution("sell-msft", "msft", "11.09.2026", 25.0, 0.5, BudgetJournalSource.RECOMMENDATION)
        )
        assertNull(result.error)
        assertEquals(0.5, result.position?.shares ?: 0.0, 0.000001)
        assertEquals(125.0, InvestmentBudgetJournalEngine.summarize(result.entries, result.reservations).availableEur, 0.000001)
    }

    @Test
    fun `same execution event is idempotent`() {
        val initialPosition = PortfolioPosition(itemId = "aapl", investedAmount = 0.0, shares = 0.0)
        val request = BudgetBuyExecution("buy-aapl", null, "aapl", "11.09.2026", 10.0, 0.04, BudgetJournalSource.MANUAL)
        val first = InvestmentBudgetExecutionService.executeBuy(
            initialPosition,
            listOf(BudgetJournalEntry("month", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "01.09.2026")),
            emptyList(),
            request
        )
        val second = InvestmentBudgetExecutionService.executeBuy(first.position!!, first.entries, first.reservations, request)
        assertNull(second.error)
        assertEquals(first.position, second.position)
        assertEquals(first.entries, second.entries)
    }

    @Test
    fun `editing executed buy updates both position and debit`() {
        val original = InvestmentBudgetExecutionService.executeBuy(
            PortfolioPosition("msci"),
            listOf(BudgetJournalEntry("month", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01")),
            emptyList(),
            BudgetBuyExecution("buy-msci", null, "msci", "2026-09-09", 20.0, 1.0, BudgetJournalSource.SPARE_CHANGE)
        )

        val revised = InvestmentBudgetExecutionService.reviseBuy(
            position = original.position!!,
            entries = original.entries,
            purchase = PortfolioPurchase("buy-msci", "2026-09-09", 25.0, 1.25)
        )

        assertNull(revised.error)
        assertEquals(25.0, revised.position?.purchases?.single()?.investedAmount ?: 0.0, 0.000001)
        assertEquals(75.0, InvestmentBudgetJournalEngine.summarize(revised.entries, revised.reservations).availableEur, 0.000001)
        assertEquals(25.0, revised.entries.single { it.id == "buy-msci" }.amountEur, 0.000001)
        assertEquals(BudgetJournalSource.SPARE_CHANGE, revised.entries.single { it.id == "buy-msci" }.source)
    }

    @Test
    fun `editing executed buy above restored buying power is rejected`() {
        val entries = listOf(
            BudgetJournalEntry("month", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("buy-msci", BudgetJournalType.BUY_DEBIT, 80.0, "2026-09-09", "msci")
        )
        val position = PortfolioPosition("msci").upsertPurchaseIfValid(
            PortfolioPurchase("buy-msci", "2026-09-09", 80.0, 4.0)
        )!!

        val revised = InvestmentBudgetExecutionService.reviseBuy(
            position,
            entries,
            PortfolioPurchase("buy-msci", "2026-09-09", 110.0, 5.5)
        )

        assertEquals("Nicht genügend verfügbares Budget", revised.error)
        assertEquals(80.0, revised.entries.single { it.id == "buy-msci" }.amountEur, 0.000001)
    }

    @Test
    fun `deleting executed buy removes matching debit`() {
        val position = PortfolioPosition("msci").upsertPurchaseIfValid(
            PortfolioPurchase("buy-msci", "2026-09-09", 20.0, 1.0)
        )!!
        val entries = listOf(
            BudgetJournalEntry("month", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
            BudgetJournalEntry("buy-msci", BudgetJournalType.BUY_DEBIT, 20.0, "2026-09-09", "msci")
        )

        val deleted = InvestmentBudgetExecutionService.deleteBuy(position, entries, "buy-msci")

        assertNull(deleted.error)
        assertTrue(deleted.position?.purchases?.isEmpty() == true)
        assertTrue(deleted.entries.none { it.id == "buy-msci" })
        assertEquals(100.0, InvestmentBudgetJournalEngine.summarize(deleted.entries, deleted.reservations).availableEur, 0.000001)
    }

    @Test
    fun `editing and deleting executed sale keep credit synchronized`() {
        val base = PortfolioPosition("msci").upsertPurchaseIfValid(
            PortfolioPurchase("buy-old", "2026-08-31", 50.0, 5.0)
        )!!
        val sold = InvestmentBudgetExecutionService.executeSale(
            base,
            listOf(BudgetJournalEntry("month", BudgetJournalType.MONTHLY_DEPOSIT, 100.0, "2026-09-01")),
            emptyList(),
            BudgetSaleExecution("sell-msci", "msci", "2026-09-10", 15.0, 1.0, BudgetJournalSource.MANUAL)
        )

        val revised = InvestmentBudgetExecutionService.reviseSale(
            sold.position!!,
            sold.entries,
            PortfolioSale("sell-msci", "2026-09-10", 18.0, 1.0)
        )
        assertNull(revised.error)
        assertEquals(118.0, InvestmentBudgetJournalEngine.summarize(revised.entries, revised.reservations).availableEur, 0.000001)
        assertEquals(18.0, revised.entries.single { it.id == "sell-msci" }.amountEur, 0.000001)

        val deleted = InvestmentBudgetExecutionService.deleteSale(revised.position!!, revised.entries, "sell-msci")
        assertNull(deleted.error)
        assertTrue(deleted.position?.sales?.isEmpty() == true)
        assertTrue(deleted.entries.none { it.id == "sell-msci" })
        assertEquals(100.0, InvestmentBudgetJournalEngine.summarize(deleted.entries, deleted.reservations).availableEur, 0.000001)
    }
}
