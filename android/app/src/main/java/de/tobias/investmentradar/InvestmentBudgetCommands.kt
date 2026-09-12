package de.tobias.investmentradar

data class ExtraFundingCommand(
    val eventId: String,
    val amountEur: Double,
    val date: String,
    val source: BudgetJournalSource = BudgetJournalSource.MANUAL,
    val note: String = ""
)

data class BudgetAdjustmentCommand(
    val eventId: String,
    val amountEur: Double,
    val date: String,
    val credit: Boolean,
    val note: String
)

object InvestmentBudgetCommands {
    fun setMonthlyBudget(
        entries: List<BudgetJournalEntry>,
        amountEur: Double,
        date: String
    ): List<BudgetJournalEntry> {
        require(amountEur.isFinite() && amountEur >= 0.0) { "Monthly budget must be finite and non-negative" }
        require(date.isNotBlank()) { "Monthly budget date must not be blank" }
        val monthKey = InvestmentBudgetDate.monthKey(date)
            ?: throw IllegalArgumentException("Monthly budget date is invalid")
        val id = "monthly-budget-$monthKey"
        val existing = entries.firstOrNull { it.id == id }
            ?: entries.firstOrNull {
                it.type == BudgetJournalType.MONTHLY_DEPOSIT &&
                    InvestmentBudgetDate.monthKey(it.date) == monthKey
            }
        val normalized = BudgetJournalEntry(
            id = existing?.id ?: id,
            type = BudgetJournalType.MONTHLY_DEPOSIT,
            amountEur = amountEur,
            date = date,
            source = BudgetJournalSource.MANUAL,
            note = "Monatsbudget ${InvestmentBudgetDate.monthLabel(monthKey)}"
        )
        return if (existing != null) {
            entries.map { if (it.id == existing.id) normalized else it }
        } else {
            InvestmentBudgetJournalEngine.upsert(entries, normalized)
        }
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

    fun addAdjustment(
        entries: List<BudgetJournalEntry>,
        command: BudgetAdjustmentCommand
    ): List<BudgetJournalEntry> {
        require(command.eventId.isNotBlank()) { "Adjustment event id must not be blank" }
        require(command.amountEur.isFinite() && command.amountEur > 0.0) { "Adjustment amount must be positive" }
        require(command.date.isNotBlank()) { "Adjustment date must not be blank" }
        require(command.note.isNotBlank()) { "Adjustment note must not be blank" }
        return InvestmentBudgetJournalEngine.upsert(
            entries,
            BudgetJournalEntry(
                id = command.eventId,
                type = if (command.credit) BudgetJournalType.ADJUSTMENT_CREDIT else BudgetJournalType.ADJUSTMENT_DEBIT,
                amountEur = command.amountEur,
                date = command.date,
                source = BudgetJournalSource.MANUAL,
                note = command.note.trim()
            )
        )
    }
}
