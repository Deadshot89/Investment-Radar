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
        val knownWrongCurrentMonthValue = existing.any {
            it.type == BudgetJournalType.MONTHLY_DEPOSIT &&
                InvestmentBudgetDate.monthKey(it.date) == monthKey &&
                it.source == BudgetJournalSource.SYSTEM &&
                kotlin.math.abs(it.amountEur - 500.0) < 0.001
        }
        val knownWrongState = configuredMonthlyBudgetEur == 500 && knownWrongCurrentMonthValue
        if (!knownWrongState) {
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
    ): List<BudgetJournalEntry> = seedIfEmpty(existing, legacyMonthlyBudgetEur.toDouble(), date)

    fun seedIfEmpty(
        existing: List<BudgetJournalEntry>,
        legacyMonthlyBudgetEur: Double,
        date: String
    ): List<BudgetJournalEntry> {
        if (existing.isNotEmpty()) return existing
        if (!legacyMonthlyBudgetEur.isFinite() || legacyMonthlyBudgetEur <= 0.0 || date.isBlank()) return existing
        return InvestmentBudgetCommands.setMonthlyBudget(
            entries = emptyList(),
            amountEur = legacyMonthlyBudgetEur,
            date = date
        ).map { it.copy(source = BudgetJournalSource.SYSTEM, note = "Übernommenes Monatsbudget") }
    }

    fun ensureCurrentMonth(
        existing: List<BudgetJournalEntry>,
        configuredMonthlyBudgetEur: Int,
        date: String
    ): List<BudgetJournalEntry> = ensureCurrentMonth(existing, configuredMonthlyBudgetEur.toDouble(), date)

    fun ensureCurrentMonth(
        existing: List<BudgetJournalEntry>,
        configuredMonthlyBudgetEur: Double,
        date: String
    ): List<BudgetJournalEntry> {
        if (!configuredMonthlyBudgetEur.isFinite() || configuredMonthlyBudgetEur < 0.0 || date.isBlank()) return existing
        val monthKey = InvestmentBudgetDate.monthKey(date) ?: return existing
        val currentMonthBudgets = existing.filter {
            it.type == BudgetJournalType.MONTHLY_DEPOSIT &&
                InvestmentBudgetDate.monthKey(it.date) == monthKey
        }

        if (currentMonthBudgets.size == 1) return existing

        if (currentMonthBudgets.size > 1) {
            val manualBudget = currentMonthBudgets.lastOrNull {
                it.source == BudgetJournalSource.MANUAL &&
                    it.amountEur.isFinite() &&
                    it.amountEur >= 0.0
            }
            val authoritativeAmount = manualBudget?.amountEur ?: configuredMonthlyBudgetEur
            val authoritativeDate = manualBudget?.date ?: date
            val authoritativeSource = manualBudget?.source ?: BudgetJournalSource.SYSTEM
            val authoritativeNote = manualBudget?.note
                ?.takeIf { it.isNotBlank() }
                ?: if (authoritativeSource == BudgetJournalSource.SYSTEM) {
                    "Monatsbudget automatisch übernommen"
                } else {
                    "Monatsbudget ${InvestmentBudgetDate.monthLabel(monthKey)}"
                }

            return InvestmentBudgetCommands.setMonthlyBudget(
                entries = existing,
                amountEur = authoritativeAmount,
                date = authoritativeDate
            ).map { entry ->
                if (
                    entry.type == BudgetJournalType.MONTHLY_DEPOSIT &&
                    InvestmentBudgetDate.monthKey(entry.date) == monthKey
                ) {
                    entry.copy(
                        source = authoritativeSource,
                        note = authoritativeNote
                    )
                } else {
                    entry
                }
            }
        }

        return InvestmentBudgetCommands.setMonthlyBudget(
            entries = existing,
            amountEur = configuredMonthlyBudgetEur,
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
