package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioBudgetIntegrationTest {
    @Test
    fun `spare change buy extends existing spyi position and keeps monthly cash untouched`() {
        val openingShares = 4.339524
        val openingBuyIn = 11.75
        val extraShares = 0.560698
        val extraBuyIn = 11.45
        val extraCost = extraShares * extraBuyIn

        val opening = PortfolioPosition(
            itemId = "spyi",
            snapshotValueEur = 50.25,
            snapshotCostBasisEur = openingShares * openingBuyIn,
            trackedShares = openingShares
        )

        val updated = opening.upsertPurchase(
            PortfolioPurchase(
                id = "spyi-spare-2026-09-09",
                date = "09.09.2026",
                investedAmount = extraCost,
                shares = extraShares
            )
        )

        assertEquals("spyi", updated.itemId)
        assertEquals(4.900222, updated.shares, 0.0000001)
        assertEquals((openingShares * openingBuyIn) + extraCost, updated.activeCostBasis, 0.000001)
        assertEquals(11.715673, updated.averageBuyPrice() ?: 0.0, 0.000001)
        assertTrue(updated.purchases.any { it.id == "imported-opening-spyi" })
        assertTrue(updated.purchases.any { it.id == "spyi-spare-2026-09-09" })

        val journal = listOf(
            BudgetJournalEntry(
                id = "month-2026-09",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 100.0,
                date = "01.09.2026"
            ),
            BudgetJournalEntry(
                id = "spare-credit-2026-09-09",
                type = BudgetJournalType.EXTRA_DEPOSIT,
                amountEur = extraCost,
                date = "09.09.2026",
                source = BudgetJournalSource.SPARE_CHANGE
            ),
            BudgetJournalEntry(
                id = "spyi-spare-2026-09-09",
                type = BudgetJournalType.BUY_DEBIT,
                amountEur = extraCost,
                date = "09.09.2026",
                itemId = "spyi",
                source = BudgetJournalSource.SPARE_CHANGE
            )
        )

        val budget = InvestmentBudgetJournalEngine.summarize(journal, emptyList())
        assertEquals(100.0, budget.availableEur, 0.000001)
        assertEquals(extraCost, budget.extraDepositsEur, 0.000001)
    }
}
