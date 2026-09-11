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
}
