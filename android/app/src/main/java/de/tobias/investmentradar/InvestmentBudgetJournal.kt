package de.tobias.investmentradar

enum class BudgetJournalType {
    MONTHLY_DEPOSIT,
    EXTRA_DEPOSIT,
    BUY_DEBIT,
    SELL_CREDIT,
    ADJUSTMENT_CREDIT,
    ADJUSTMENT_DEBIT
}

enum class BudgetJournalSource {
    MANUAL,
    SAVINGS_PLAN,
    SPARE_CHANGE,
    RECOMMENDATION,
    SYSTEM
}

data class BudgetJournalEntry(
    val id: String,
    val type: BudgetJournalType,
    val amountEur: Double,
    val date: String,
    val itemId: String = "",
    val source: BudgetJournalSource = BudgetJournalSource.MANUAL,
    val note: String = "",
    val feeEur: Double = 0.0
)

data class BudgetReservation(
    val id: String,
    val itemId: String,
    val amountEur: Double,
    val note: String = ""
)

data class InvestmentBudgetSummary(
    val monthlyDepositsEur: Double,
    val extraDepositsEur: Double,
    val executedBuysEur: Double,
    val saleCreditsEur: Double,
    val availableEur: Double,
    val reservedEur: Double,
    val cashBalanceEur: Double = availableEur + reservedEur,
    val feesEur: Double = 0.0
)

object InvestmentBudgetJournalEngine {
    fun summarize(
        entries: List<BudgetJournalEntry>,
        reservations: List<BudgetReservation>
    ): InvestmentBudgetSummary {
        val unique = entries
            .filter {
                it.id.isNotBlank() &&
                    it.amountEur.isFinite() && it.amountEur >= 0.0 &&
                    it.feeEur.isFinite() && it.feeEur >= 0.0
            }
            .associateBy { it.id }
            .values

        val monthly = unique.filter { it.type == BudgetJournalType.MONTHLY_DEPOSIT }.sumOf { it.amountEur }
        val extra = unique.filter { it.type == BudgetJournalType.EXTRA_DEPOSIT }.sumOf { it.amountEur }
        val buys = unique.filter { it.type == BudgetJournalType.BUY_DEBIT }.sumOf { it.amountEur }
        val sales = unique.filter { it.type == BudgetJournalType.SELL_CREDIT }.sumOf { it.amountEur }
        val credits = unique.filter { it.type == BudgetJournalType.ADJUSTMENT_CREDIT }.sumOf { it.amountEur }
        val debits = unique.filter { it.type == BudgetJournalType.ADJUSTMENT_DEBIT }.sumOf { it.amountEur }
        val fees = unique
            .filter { it.type == BudgetJournalType.BUY_DEBIT || it.type == BudgetJournalType.SELL_CREDIT }
            .sumOf { it.feeEur }
        val reserved = reservations
            .filter { it.id.isNotBlank() && it.amountEur.isFinite() && it.amountEur > 0.0 }
            .associateBy { it.id }
            .values
            .sumOf { it.amountEur }
        val cashBalance = monthly + extra + sales + credits - buys - debits

        return InvestmentBudgetSummary(
            monthlyDepositsEur = monthly,
            extraDepositsEur = extra,
            executedBuysEur = buys,
            saleCreditsEur = sales,
            availableEur = cashBalance - reserved,
            reservedEur = reserved,
            cashBalanceEur = cashBalance,
            feesEur = fees
        )
    }

    fun upsert(
        entries: List<BudgetJournalEntry>,
        entry: BudgetJournalEntry
    ): List<BudgetJournalEntry> {
        require(entry.id.isNotBlank()) { "Budget event id must not be blank" }
        require(entry.amountEur.isFinite() && entry.amountEur >= 0.0) { "Budget amount must be finite and non-negative" }
        require(entry.feeEur.isFinite() && entry.feeEur >= 0.0) { "Budget fee must be finite and non-negative" }
        val index = entries.indexOfFirst { it.id == entry.id }
        return if (index >= 0) {
            entries.toMutableList().apply { set(index, entry) }
        } else {
            entries + entry
        }
    }

    fun upsertReservation(
        reservations: List<BudgetReservation>,
        reservation: BudgetReservation
    ): List<BudgetReservation> {
        require(reservation.id.isNotBlank()) { "Reservation id must not be blank" }
        require(reservation.amountEur.isFinite() && reservation.amountEur >= 0.0) { "Reservation amount must be finite and non-negative" }
        val index = reservations.indexOfFirst { it.id == reservation.id }
        return if (index >= 0) {
            reservations.toMutableList().apply { set(index, reservation) }
        } else {
            reservations + reservation
        }
    }

    fun removeReservation(
        reservations: List<BudgetReservation>,
        reservationId: String
    ): List<BudgetReservation> = reservations.filterNot { it.id == reservationId }

    fun containsEvent(entries: List<BudgetJournalEntry>, eventId: String): Boolean =
        entries.any { it.id == eventId }
}
