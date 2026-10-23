package de.tobias.investmentradar

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

/**
 * Valid, fully initialized budget state for import-persistence tests.
 * An arbitrary non-JSON sentinel is not stable: normal app initialization may
 * legitimately replace it with the monthly 100 EUR budget in the background.
 *
 * Keep a separate unique extra deposit and reservation as sentinels so any
 * accidental budget write by a portfolio import is still detected byte-for-byte.
 */
internal fun seedStableImportBudget(context: Context, label: String) {
    val today = LocalDate.now()
    val month = YearMonth.from(today).toString()
    val entries = listOf(
        BudgetJournalEntry(
            id = "monthly-budget-$month",
            type = BudgetJournalType.MONTHLY_DEPOSIT,
            amountEur = 100.0,
            date = today.toString(),
            source = BudgetJournalSource.SYSTEM,
            note = "Monatsbudget automatisch übernommen"
        ),
        BudgetJournalEntry(
            id = "import-test-sentinel-$label",
            type = BudgetJournalType.EXTRA_DEPOSIT,
            amountEur = 4.17,
            date = today.toString(),
            source = BudgetJournalSource.MANUAL,
            note = "Import darf Budget nicht verändern"
        )
    )
    val reservations = listOf(
        BudgetReservation(
            id = "import-test-reservation-$label",
            itemId = "import-test-only",
            amountEur = 0.77
        )
    )
    check(
        context.getSharedPreferences("investment_radar_budget", Context.MODE_PRIVATE)
            .edit()
            .putString("entries_v1", InvestmentBudgetCodec.encodeEntries(entries))
            .putString("reservations_v1", InvestmentBudgetCodec.encodeReservations(reservations))
            .commit()
    ) { "Testbudget konnte nicht gespeichert werden" }
}
