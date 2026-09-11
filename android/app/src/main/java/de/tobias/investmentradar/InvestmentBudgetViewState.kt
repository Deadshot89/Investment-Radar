package de.tobias.investmentradar

data class BudgetHistoryItem(
    val id: String,
    val date: String,
    val title: String,
    val amountEur: Double,
    val isCredit: Boolean,
    val itemId: String,
    val note: String
)

data class InvestmentBudgetViewState(
    val monthlyBudgetEur: Double,
    val extraFundingEur: Double,
    val investedEur: Double,
    val saleCreditsEur: Double,
    val reservedEur: Double,
    val availableEur: Double,
    val advisorBudgetEur: Int,
    val history: List<BudgetHistoryItem> = emptyList()
) {
    companion object {
        fun from(
            summary: InvestmentBudgetSummary,
            entries: List<BudgetJournalEntry>
        ): InvestmentBudgetViewState {
            val cockpit = InvestmentBudgetPresentation.from(summary)
            val history = entries
                .filter { it.id.isNotBlank() && it.amountEur.isFinite() && it.amountEur >= 0.0 }
                .distinctBy { it.id }
                .sortedWith(compareByDescending<BudgetJournalEntry> { it.date }.thenByDescending { it.id })
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
                        BudgetJournalType.ADJUSTMENT_CREDIT -> "Gutschrift"
                        BudgetJournalType.ADJUSTMENT_DEBIT -> "Abbuchung"
                    }
                    BudgetHistoryItem(
                        id = entry.id,
                        date = entry.date,
                        title = title,
                        amountEur = if (credit) entry.amountEur else -entry.amountEur,
                        isCredit = credit,
                        itemId = entry.itemId,
                        note = entry.note
                    )
                }
            return InvestmentBudgetViewState(
                monthlyBudgetEur = cockpit.monthlyBudgetEur,
                extraFundingEur = cockpit.extraFundingEur,
                investedEur = cockpit.investedEur,
                saleCreditsEur = cockpit.saleCreditsEur,
                reservedEur = cockpit.reservedEur,
                availableEur = cockpit.availableEur,
                advisorBudgetEur = cockpit.advisorBudgetEur,
                history = history
            )
        }

        fun from(summary: InvestmentBudgetSummary): InvestmentBudgetViewState =
            from(summary, emptyList())
    }
}
