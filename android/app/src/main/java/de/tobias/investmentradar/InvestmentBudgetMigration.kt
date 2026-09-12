package de.tobias.investmentradar

object InvestmentBudgetMigration {
    fun seedIfEmpty(
        existing: List<BudgetJournalEntry>,
        legacyMonthlyBudgetEur: Int,
        date: String
    ): List<BudgetJournalEntry> {
        if (existing.isNotEmpty()) return existing
        if (legacyMonthlyBudgetEur <= 0 || date.isBlank()) return existing
        return InvestmentBudgetCommands.setMonthlyBudget(
            entries = emptyList(),
            amountEur = legacyMonthlyBudgetEur.toDouble(),
            date = date
        ).map { it.copy(source = BudgetJournalSource.SYSTEM, note = "Übernommenes Monatsbudget") }
    }

    fun ensureCurrentMonth(
        existing: List<BudgetJournalEntry>,
        configuredMonthlyBudgetEur: Int,
        date: String
    ): List<BudgetJournalEntry> {
        if (configuredMonthlyBudgetEur < 0 || date.isBlank()) return existing
        val monthKey = InvestmentBudgetDate.monthKey(date) ?: return existing
        val alreadyPresent = existing.any {
            it.type == BudgetJournalType.MONTHLY_DEPOSIT &&
                InvestmentBudgetDate.monthKey(it.date) == monthKey
        }
        if (alreadyPresent) return existing
        return InvestmentBudgetCommands.setMonthlyBudget(
            entries = existing,
            amountEur = configuredMonthlyBudgetEur.toDouble(),
            date = date
        ).map { entry ->
            if (entry.id == "monthly-budget-$monthKey") {
                entry.copy(source = BudgetJournalSource.SYSTEM, note = "Monatsbudget automatisch übernommen")
            } else entry
        }
    }
}
