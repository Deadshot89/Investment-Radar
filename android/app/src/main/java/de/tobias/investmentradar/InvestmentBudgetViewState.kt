package de.tobias.investmentradar

import java.time.LocalDate
import java.time.YearMonth

data class BudgetHistoryItem(
    val id: String,
    val date: String,
    val title: String,
    val amountEur: Double,
    val isCredit: Boolean,
    val itemId: String,
    val note: String,
    val feeEur: Double = 0.0
)

data class InvestmentBudgetViewState(
    val monthlyBudgetEur: Double,
    val extraFundingEur: Double,
    val investedEur: Double,
    val saleCreditsEur: Double,
    val reservedEur: Double,
    val availableEur: Double,
    val advisorBudgetEur: Int,
    val cashBalanceEur: Double = availableEur + reservedEur,
    val carryoverEur: Double = 0.0,
    val feesEur: Double = 0.0,
    val monthLabel: String = "",
    val history: List<BudgetHistoryItem> = emptyList()
) {
    companion object {
        fun from(
            summary: InvestmentBudgetSummary,
            entries: List<BudgetJournalEntry>,
            activeInvestedEur: Double = summary.executedBuysEur,
            today: LocalDate = LocalDate.now()
        ): InvestmentBudgetViewState {
            val cockpit = InvestmentBudgetPresentation.from(summary)
            val currentMonth = YearMonth.from(today)
            val currentEntries = entries.filter { entry ->
                InvestmentBudgetDate.parse(entry.date)?.let { YearMonth.from(it) == currentMonth } == true
            }
            val priorEntries = entries.filter { entry ->
                InvestmentBudgetDate.parse(entry.date)?.let { YearMonth.from(it) < currentMonth } == true
            }
            val monthly = currentEntries
                .filter { it.type == BudgetJournalType.MONTHLY_DEPOSIT }
                .sumOf { it.amountEur }
            val extra = currentEntries
                .filter { it.type == BudgetJournalType.EXTRA_DEPOSIT }
                .sumOf { it.amountEur }
            val sales = currentEntries
                .filter { it.type == BudgetJournalType.SELL_CREDIT }
                .sumOf { it.amountEur }
            val fees = currentEntries
                .filter { it.type == BudgetJournalType.BUY_DEBIT || it.type == BudgetJournalType.SELL_CREDIT }
                .sumOf { it.feeEur }
            val carryover = priorEntries.sumOf(::balanceEffect)
            val history = entries
                .filter {
                    it.id.isNotBlank() &&
                        it.amountEur.isFinite() && it.amountEur >= 0.0 &&
                        it.feeEur.isFinite() && it.feeEur >= 0.0
                }
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<BudgetJournalEntry> {
                        InvestmentBudgetDate.parse(it.date) ?: LocalDate.MIN
                    }.thenByDescending { it.id }
                )
                .map { entry ->
                    val credit = entry.type in setOf(
                        BudgetJournalType.MONTHLY_DEPOSIT,
                        BudgetJournalType.EXTRA_DEPOSIT,
                        BudgetJournalType.SELL_CREDIT,
                        BudgetJournalType.ADJUSTMENT_CREDIT
                    )
                    val title = when (entry.type) {
                        BudgetJournalType.MONTHLY_DEPOSIT -> "Monatsbudget"
                        BudgetJournalType.EXTRA_DEPOSIT -> if (entry.source == BudgetJournalSource.SPARE_CHANGE) "Wechselgeld" else "Zusätzliches Guthaben"
                        BudgetJournalType.BUY_DEBIT -> "Kauf"
                        BudgetJournalType.SELL_CREDIT -> "Verkauf"
                        BudgetJournalType.ADJUSTMENT_CREDIT -> "Korrektur +"
                        BudgetJournalType.ADJUSTMENT_DEBIT -> "Korrektur −"
                    }
                    val feeNote = entry.feeEur
                        .takeIf { it > 0.0 }
                        ?.let { "Gebühr %.2f € bereits im Betrag enthalten".format(it) }
                        .orEmpty()
                    BudgetHistoryItem(
                        id = entry.id,
                        date = entry.date,
                        title = title,
                        amountEur = if (credit) entry.amountEur else -entry.amountEur,
                        isCredit = credit,
                        itemId = entry.itemId,
                        note = listOf(entry.note, feeNote).filter { it.isNotBlank() }.joinToString(" · "),
                        feeEur = entry.feeEur
                    )
                }
            return InvestmentBudgetViewState(
                monthlyBudgetEur = monthly.coerceFiniteNonNegative(),
                extraFundingEur = extra.coerceFiniteNonNegative(),
                investedEur = activeInvestedEur.coerceFiniteNonNegative(),
                saleCreditsEur = sales.coerceFiniteNonNegative(),
                reservedEur = cockpit.reservedEur,
                availableEur = cockpit.availableEur,
                advisorBudgetEur = cockpit.advisorBudgetEur,
                cashBalanceEur = summary.cashBalanceEur.coerceFiniteNonNegative(),
                carryoverEur = carryover.coerceAtLeast(0.0),
                feesEur = fees.coerceFiniteNonNegative(),
                monthLabel = InvestmentBudgetDate.monthLabel(currentMonth.toString()),
                history = history
            )
        }

        fun from(summary: InvestmentBudgetSummary): InvestmentBudgetViewState {
            val cockpit = InvestmentBudgetPresentation.from(summary)
            return InvestmentBudgetViewState(
                monthlyBudgetEur = cockpit.monthlyBudgetEur,
                extraFundingEur = cockpit.extraFundingEur,
                investedEur = cockpit.investedEur,
                saleCreditsEur = cockpit.saleCreditsEur,
                reservedEur = cockpit.reservedEur,
                availableEur = cockpit.availableEur,
                advisorBudgetEur = cockpit.advisorBudgetEur,
                cashBalanceEur = summary.cashBalanceEur.coerceFiniteNonNegative(),
                carryoverEur = 0.0,
                feesEur = summary.feesEur.coerceFiniteNonNegative(),
                monthLabel = InvestmentBudgetDate.monthLabel(YearMonth.from(LocalDate.now()).toString()),
                history = emptyList()
            )
        }

        private fun balanceEffect(entry: BudgetJournalEntry): Double = when (entry.type) {
            BudgetJournalType.MONTHLY_DEPOSIT,
            BudgetJournalType.EXTRA_DEPOSIT,
            BudgetJournalType.SELL_CREDIT,
            BudgetJournalType.ADJUSTMENT_CREDIT -> entry.amountEur
            BudgetJournalType.BUY_DEBIT,
            BudgetJournalType.ADJUSTMENT_DEBIT -> -entry.amountEur
        }

        private fun Double.coerceFiniteNonNegative(): Double =
            if (isFinite()) coerceAtLeast(0.0) else 0.0
    }
}
