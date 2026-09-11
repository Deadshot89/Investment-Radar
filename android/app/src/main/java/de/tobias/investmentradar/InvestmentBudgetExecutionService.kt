package de.tobias.investmentradar

data class BudgetBuyExecution(
    val eventId: String,
    val reservationId: String?,
    val itemId: String,
    val date: String,
    val amountEur: Double,
    val shares: Double,
    val source: BudgetJournalSource
)

data class BudgetSaleExecution(
    val eventId: String,
    val itemId: String,
    val date: String,
    val proceedsEur: Double,
    val shares: Double,
    val source: BudgetJournalSource
)

data class BudgetExecutionResult(
    val position: PortfolioPosition?,
    val entries: List<BudgetJournalEntry>,
    val reservations: List<BudgetReservation>,
    val error: String? = null
)

object InvestmentBudgetExecutionService {
    fun executeBuy(
        position: PortfolioPosition,
        entries: List<BudgetJournalEntry>,
        reservations: List<BudgetReservation>,
        request: BudgetBuyExecution
    ): BudgetExecutionResult {
        if (InvestmentBudgetJournalEngine.containsEvent(entries, request.eventId)) {
            return BudgetExecutionResult(position, entries, reservations)
        }
        if (request.itemId != position.itemId || request.eventId.isBlank() || request.date.isBlank() ||
            !request.amountEur.isFinite() || request.amountEur <= 0.0 ||
            !request.shares.isFinite() || request.shares <= 0.0
        ) {
            return BudgetExecutionResult(position, entries, reservations, "Ungültige Kaufdaten")
        }

        val matchingReservation = request.reservationId
            ?.let { id -> reservations.firstOrNull { it.id == id && it.itemId == request.itemId } }
        val summary = InvestmentBudgetJournalEngine.summarize(entries, reservations)
        val buyingPower = summary.availableEur + (matchingReservation?.amountEur ?: 0.0)
        if (request.amountEur > buyingPower + 0.000001) {
            return BudgetExecutionResult(position, entries, reservations, "Nicht genügend verfügbares Budget")
        }

        val purchase = PortfolioPurchase(
            id = request.eventId,
            date = request.date,
            investedAmount = request.amountEur,
            shares = request.shares
        )
        val updatedPosition = position.upsertPurchaseIfValid(purchase)
            ?: return BudgetExecutionResult(position, entries, reservations, "Kauf konnte nicht verbucht werden")

        val updatedEntries = InvestmentBudgetJournalEngine.upsert(
            entries,
            BudgetJournalEntry(
                id = request.eventId,
                type = BudgetJournalType.BUY_DEBIT,
                amountEur = request.amountEur,
                date = request.date,
                itemId = request.itemId,
                source = request.source,
                note = "Kauf ausgeführt"
            )
        )
        val updatedReservations = request.reservationId
            ?.let { InvestmentBudgetJournalEngine.removeReservation(reservations, it) }
            ?: reservations

        return BudgetExecutionResult(updatedPosition, updatedEntries, updatedReservations)
    }

    fun executeSale(
        position: PortfolioPosition,
        entries: List<BudgetJournalEntry>,
        reservations: List<BudgetReservation>,
        request: BudgetSaleExecution
    ): BudgetExecutionResult {
        if (InvestmentBudgetJournalEngine.containsEvent(entries, request.eventId)) {
            return BudgetExecutionResult(position, entries, reservations)
        }
        if (request.itemId != position.itemId || request.eventId.isBlank() || request.date.isBlank() ||
            !request.proceedsEur.isFinite() || request.proceedsEur < 0.0 ||
            !request.shares.isFinite() || request.shares <= 0.0
        ) {
            return BudgetExecutionResult(position, entries, reservations, "Ungültige Verkaufsdaten")
        }

        val updatedPosition = position.upsertSale(
            PortfolioSale(
                id = request.eventId,
                date = request.date,
                proceeds = request.proceedsEur,
                shares = request.shares
            )
        ) ?: return BudgetExecutionResult(position, entries, reservations, "Verkauf übersteigt den Bestand")

        val updatedEntries = InvestmentBudgetJournalEngine.upsert(
            entries,
            BudgetJournalEntry(
                id = request.eventId,
                type = BudgetJournalType.SELL_CREDIT,
                amountEur = request.proceedsEur,
                date = request.date,
                itemId = request.itemId,
                source = request.source,
                note = "Verkauf ausgeführt"
            )
        )

        return BudgetExecutionResult(updatedPosition, updatedEntries, reservations)
    }
}
