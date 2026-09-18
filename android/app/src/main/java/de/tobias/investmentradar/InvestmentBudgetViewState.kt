package de.tobias.investmentradar

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.floor

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
    val spentThisMonthEur: Double = 0.0,
    val saleCreditsEur: Double,
    val reservedEur: Double,
    val availableEur: Double,
    val advisorBudgetEur: Int,
    val monthlyAvailableEur: Double = availableEur,
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
            val validEntries = entries
                .filter {
                    it.id.isNotBlank() &&
                        it.amountEur.isFinite() && it.amountEur >= 0.0 &&
                        it.feeEur.isFinite() && it.feeEur >= 0.0
                }
                .distinctBy { it.id }
            val currentEntries = validEntries.filter { entry ->
                InvestmentBudgetDate.parse(entry.date)?.let { YearMonth.from(it) == currentMonth } == true
            }
            val priorEntries = validEntries.filter { entry ->
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
            val spentThisMonth = currentEntries
                .filter { it.type == BudgetJournalType.BUY_DEBIT || it.type == BudgetJournalType.ADJUSTMENT_DEBIT }
                .sumOf { it.amountEur }
            val fees = currentEntries
                .filter { it.type == BudgetJournalType.BUY_DEBIT || it.type == BudgetJournalType.SELL_CREDIT }
                .sumOf { it.feeEur }
            val carryover = priorEntries.sumOf(::balanceEffect)
            val monthlyBudgetBuys = currentEntries
                .filter {
                    it.type == BudgetJournalType.BUY_DEBIT &&
                        it.source != BudgetJournalSource.SPARE_CHANGE
                }
                .sumOf { it.amountEur }
            val monthlyBudgetRemaining = (
                monthly - monthlyBudgetBuys - cockpit.reservedEur
            ).coerceAtLeast(0.0)
            val monthlyAvailable = minOf(
                monthlyBudgetRemaining,
                cockpit.availableEur.coerceAtLeast(0.0)
            )
            val history = validEntries
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
                spentThisMonthEur = spentThisMonth.coerceFiniteNonNegative(),
                saleCreditsEur = sales.coerceFiniteNonNegative(),
                reservedEur = cockpit.reservedEur,
                availableEur = cockpit.availableEur,
                monthlyAvailableEur = monthlyAvailable,
                advisorBudgetEur = floor(monthlyAvailable).toInt().coerceAtLeast(0),
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
                spentThisMonthEur = cockpit.investedEur,
                saleCreditsEur = cockpit.saleCreditsEur,
                reservedEur = cockpit.reservedEur,
                availableEur = cockpit.availableEur,
                monthlyAvailableEur = minOf(
                    cockpit.monthlyBudgetEur.coerceAtLeast(0.0),
                    cockpit.availableEur.coerceAtLeast(0.0)
                ),
                advisorBudgetEur = floor(
                    minOf(
                        cockpit.monthlyBudgetEur.coerceAtLeast(0.0),
                        cockpit.availableEur.coerceAtLeast(0.0)
                    )
                ).toInt().coerceAtLeast(0),
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
