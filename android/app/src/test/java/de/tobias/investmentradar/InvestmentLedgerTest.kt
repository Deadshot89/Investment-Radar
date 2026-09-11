package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentLedgerTest {
    @Test
    fun `buy decreases available cash and increases invested capital`() {
        val summary = InvestmentLedgerEngine.summarize(
            cashEntries = listOf(CashEntry("m1", CashEntryType.MONTHLY_DEPOSIT, 100.0, "2026-09-01")),
            trades = listOf(InvestmentTrade("t1", "spyi", "IE00B3YLTY66", TradeSide.BUY, 1.0, 40.0, 40.0, "2026-09-03", TradeSource.RECOMMENDATION)),
            reservations = emptyList()
        )
        assertEquals(60.0, summary.availableEur, 0.0001)
        assertEquals(40.0, summary.investedEur, 0.0001)
    }

    @Test
    fun `sell credits available cash`() {
        val summary = InvestmentLedgerEngine.summarize(
            cashEntries = listOf(CashEntry("m1", CashEntryType.MONTHLY_DEPOSIT, 100.0, "2026-09-01")),
            trades = listOf(
                InvestmentTrade("b1", "spyi", "IE00B3YLTY66", TradeSide.BUY, 1.0, 40.0, 40.0, "2026-09-03", TradeSource.MANUAL),
                InvestmentTrade("s1", "spyi", "IE00B3YLTY66", TradeSide.SELL, 0.5, 50.0, 25.0, "2026-09-08", TradeSource.MANUAL)
            ),
            reservations = emptyList()
        )
        assertEquals(85.0, summary.availableEur, 0.0001)
        assertEquals(20.0, summary.investedEur, 0.0001)
    }

    @Test
    fun `spare change is extra deposit independent of monthly budget`() {
        val summary = InvestmentLedgerEngine.summarize(
            cashEntries = listOf(
                CashEntry("m1", CashEntryType.MONTHLY_DEPOSIT, 100.0, "2026-09-01"),
                CashEntry("x1", CashEntryType.EXTRA_DEPOSIT, 6.40, "2026-09-09")
            ),
            trades = emptyList(),
            reservations = emptyList()
        )
        assertEquals(106.40, summary.availableEur, 0.0001)
        assertEquals(6.40, summary.extraDepositsEur, 0.0001)
    }

    @Test
    fun `reservation reduces spendable cash but does not create investment`() {
        val summary = InvestmentLedgerEngine.summarize(
            cashEntries = listOf(CashEntry("m1", CashEntryType.MONTHLY_DEPOSIT, 100.0, "2026-09-01")),
            trades = emptyList(),
            reservations = listOf(BudgetReservation("r1", "msft", 25.0))
        )
        assertEquals(75.0, summary.availableEur, 0.0001)
        assertEquals(25.0, summary.reservedEur, 0.0001)
        assertEquals(0.0, summary.investedEur, 0.0001)
    }

    @Test
    fun `multiple buys of same isin aggregate into one position`() {
        val position = InvestmentLedgerEngine.aggregatePosition(
            listOf(
                InvestmentTrade("b1", "spyi", "IE00B3YLTY66", TradeSide.BUY, 4.339524, 11.75, 50.989407, "2026-08-31", TradeSource.MANUAL),
                InvestmentTrade("b2", "spyi", "IE00B3YLTY66", TradeSide.BUY, 0.560698, 11.45, 6.4199921, "2026-09-09", TradeSource.SPARE_CHANGE)
            )
        )
        assertEquals("IE00B3YLTY66", position.key)
        assertEquals(4.900222, position.shares, 0.0000001)
        assertEquals(57.4093991, position.costBasisEur, 0.000001)
        assertEquals(11.715673, position.averageBuyInEur, 0.000001)
    }
}
