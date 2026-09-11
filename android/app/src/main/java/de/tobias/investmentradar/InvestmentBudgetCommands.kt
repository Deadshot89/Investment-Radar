package de.tobias.investmentradar

data class ExtraFundingCommand(
    val eventId: String,
    val amountEur: Double,
    val date: String,
    val source: BudgetJournalSource = BudgetJournalSource.MANUAL,
    val note: String = ""
)

object InvestmentBudgetCommands {
    private const val CURRENT_MONTHLY_BUDGET_ID = "monthly-budget-current"

    fun setMonthlyBudget(
        entries: List<BudgetJournalEntry>,
        amountEur: Double,
        date: String
    ): List<BudgetJournalEntry> {
        require(amountEur.isFinite() && amountEur >= 0.0) { "Monthly budget must be finite and non-negative" }
        require(date.isNotBlank()) { "Monthly budget date must not be blank" }

        val withoutPreviousMonthlyFunding = entries.filterNot { it.type == BudgetJournalType.MONTHLY_DEPOSIT }
        return InvestmentBudgetJournalEngine.upsert(
            withoutPreviousMonthlyFunding,
            BudgetJournalEntry(
                id = CURRENT_MONTHLY_BUDGET_ID,
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = amountEur,
                date = date,
                source = BudgetJournalSource.MANUAL,
                note = "Aktuelles Monatsbudget"
            )
        )
    }

    fun addExtraFunding(
        entries: List<BudgetJournalEntry>,
        command: ExtraFundingCommand
    ): List<BudgetJournalEntry> {
        require(command.eventId.isNotBlank()) { "Extra funding event id must not be blank" }
        require(command.amountEur.isFinite() && command.amountEur > 0.0) { "Extra funding must be positive" }
        require(command.date.isNotBlank()) { "Extra funding date must not be blank" }

        return InvestmentBudgetJournalEngine.upsert(
            entries,
            BudgetJournalEntry(
                id = command.eventId,
                type = BudgetJournalType.EXTRA_DEPOSIT,
                amountEur = command.amountEur,
                date = command.date,
                source = command.source,
                note = command.note
            )
        )
    }
}
