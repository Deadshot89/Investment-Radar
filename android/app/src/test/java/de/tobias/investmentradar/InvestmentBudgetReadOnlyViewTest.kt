package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InvestmentBudgetReadOnlyViewTest {
    @Test
    fun readOnlyViewKeepsSerializedEntriesAndReservationsByteForByte() {
        val entries = listOf(
            BudgetJournalEntry(
                id = "monthly-budget-2026-10",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = 100.0,
                date = "2026-10-01"
            ),
            BudgetJournalEntry(
                id = "buy-meta",
                type = BudgetJournalType.BUY_DEBIT,
                amountEur = 25.0,
                date = "2026-10-03",
                itemId = "meta"
            )
        )
        val reservations = listOf(
            BudgetReservation(
                id = "reserve-1",
                itemId = "apple",
                amountEur = 15.0,
                note = "geplant"
            )
        )
        val entriesBefore = InvestmentBudgetCodec.encodeEntries(entries)
        val reservationsBefore = InvestmentBudgetCodec.encodeReservations(reservations)

        val view = InvestmentBudgetStore.viewStateReadOnly(
            entries = entries,
            reservations = reservations,
            activeInvestedEur = 325.0,
            today = LocalDate.of(2026, 10, 15)
        )

        assertEquals(entriesBefore, InvestmentBudgetCodec.encodeEntries(entries))
        assertEquals(reservationsBefore, InvestmentBudgetCodec.encodeReservations(reservations))
        assertEquals(325.0, view.investedEur, 0.000001)
        assertEquals(60.0, view.availableEur, 0.000001)
        assertEquals(60.0, view.monthlyAvailableEur, 0.000001)
        assertEquals(60, view.advisorBudgetEur)
    }
}
