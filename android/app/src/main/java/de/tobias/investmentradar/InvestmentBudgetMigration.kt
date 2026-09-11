package de.tobias.investmentradar

object InvestmentBudgetMigration {
    fun seedIfEmpty(
        existing: List<BudgetJournalEntry>,
        legacyMonthlyBudgetEur: Int,
        date: String
    ): List<BudgetJournalEntry> {
        if (existing.isNotEmpty()) return existing
        if (legacyMonthlyBudgetEur <= 0 || date.isBlank()) return existing
        return listOf(
            BudgetJournalEntry(
                id = "legacy-monthly-budget-opening",
                type = BudgetJournalType.MONTHLY_DEPOSIT,
                amountEur = legacyMonthlyBudgetEur.toDouble(),
                date = date,
                source = BudgetJournalSource.SYSTEM,
                note = "Übernommenes Monatsbudget"
            )
        )
    }
}
