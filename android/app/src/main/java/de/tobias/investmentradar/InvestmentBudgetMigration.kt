package de.tobias.investmentradar

import java.time.LocalDate
import java.time.YearMonth

data class MonthlyBudgetRepair(
    val entries: List<BudgetJournalEntry>,
    val configuredMonthlyBudgetEur: Int,
    val repaired: Boolean
)

object InvestmentBudgetMigration {
    fun repairKnownIncorrectFiveHundredBudget(
        existing: List<BudgetJournalEntry>,
        configuredMonthlyBudgetEur: Int,
        date: String
    ): MonthlyBudgetRepair {
        val monthKey = InvestmentBudgetDate.monthKey(date)
            ?: return MonthlyBudgetRepair(existing, configuredMonthlyBudgetEur, false)
        val currentMonthlyBudget = existing
            .filter {
                it.type == BudgetJournalType.MONTHLY_DEPOSIT &&
                    InvestmentBudgetDate.monthKey(it.date) == monthKey
            }
            .sumOf { it.amountEur }
        val wrongConfiguredValue = configuredMonthlyBudgetEur == 500
        val wrongCurrentMonthValue = kotlin.math.abs(currentMonthlyBudget - 500.0) < 0.001
        if (!wrongConfiguredValue && !wrongCurrentMonthValue) {
            return MonthlyBudgetRepair(existing, configuredMonthlyBudgetEur, false)
        }

        val corrected = InvestmentBudgetCommands.setMonthlyBudget(
            entries = existing,
            amountEur = 100.0,
            date = date
        ).map { entry ->
            if (
                entry.type == BudgetJournalType.MONTHLY_DEPOSIT &&
                InvestmentBudgetDate.monthKey(entry.date) == monthKey
            ) {
                entry.copy(
                    source = BudgetJournalSource.SYSTEM,
                    note = "Korrigiertes Monatsbudget · 500 € war kein Kaufbudget"
                )
            } else {
                entry
            }
        }
        return MonthlyBudgetRepair(
            entries = corrected,
            configuredMonthlyBudgetEur = 100,
            repaired = true
        )
    }

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

    fun reconcileCurrentMonthTransactions(
        existing: List<BudgetJournalEntry>,
        positions: Map<String, PortfolioPosition>,
        today: LocalDate = LocalDate.now()
    ): List<BudgetJournalEntry> {
        val currentMonth = YearMonth.from(today)
        val knownIds = existing.mapTo(mutableSetOf()) { it.id }
        val imported = mutableListOf<BudgetJournalEntry>()

        positions.values.forEach { position ->
            position.purchases.forEach { purchase ->
                val purchaseDate = InvestmentBudgetDate.parse(purchase.date)
                if (
                    purchase.id.isNotBlank() &&
                    purchase.id !in knownIds &&
                    purchaseDate != null &&
                    YearMonth.from(purchaseDate) == currentMonth &&
                    purchase.investedAmount.isFinite() && purchase.investedAmount > 0.0 &&
                    purchase.shares.isFinite() && purchase.shares > 0.0
                ) {
                    imported += BudgetJournalEntry(
                        id = purchase.id,
                        type = BudgetJournalType.BUY_DEBIT,
                        amountEur = purchase.investedAmount,
                        date = purchase.date,
                        itemId = position.itemId,
                        source = BudgetJournalSource.SYSTEM,
                        note = "Bestätigter Kauf aus Depot übernommen"
                    )
                    knownIds += purchase.id
                }
            }

            position.sales.forEach { sale ->
                val saleDate = InvestmentBudgetDate.parse(sale.date)
                if (
                    sale.id.isNotBlank() &&
                    sale.id !in knownIds &&
                    saleDate != null &&
                    YearMonth.from(saleDate) == currentMonth &&
                    sale.proceeds.isFinite() && sale.proceeds >= 0.0 &&
                    sale.shares.isFinite() && sale.shares > 0.0
                ) {
                    imported += BudgetJournalEntry(
                        id = sale.id,
                        type = BudgetJournalType.SELL_CREDIT,
                        amountEur = sale.proceeds,
                        date = sale.date,
                        itemId = position.itemId,
                        source = BudgetJournalSource.SYSTEM,
                        note = "Bestätigter Verkauf aus Depot übernommen"
                    )
                    knownIds += sale.id
                }
            }
        }

        return if (imported.isEmpty()) existing else existing + imported
    }
}
