package de.tobias.investmentradar

data class BudgetBuyExecution(
    val eventId: String,
    val reservationId: String?,
    val itemId: String,
    val date: String,
    val amountEur: Double,
    val shares: Double,
    val source: BudgetJournalSource,
    val feeEur: Double = 0.0
)

data class BudgetSaleExecution(
    val eventId: String,
    val itemId: String,
    val date: String,
    val proceedsEur: Double,
    val shares: Double,
    val source: BudgetJournalSource,
    val feeEur: Double = 0.0
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
            !request.feeEur.isFinite() || request.feeEur < 0.0 || request.feeEur > request.amountEur ||
            !request.shares.isFinite() || request.shares <= 0.0
        ) {
            return BudgetExecutionResult(position, entries, reservations, "Ungültige Kaufdaten")
        }

        val matchingReservation = request.reservationId
            ?.let { id -> reservations.firstOrNull { it.id == id && it.itemId == request.itemId } }
        val summary = InvestmentBudgetJournalEngine.summarize(entries, reservations)
        val buyingPower = summary.availableEur + (matchingReservation?.amountEur ?: 0.0)
        if (request.amountEur > buyingPower + EPSILON) {
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
                note = "Kauf ausgeführt",
                feeEur = request.feeEur
            )
        )
        val updatedReservations = request.reservationId
            ?.let { InvestmentBudgetJournalEngine.removeReservation(reservations, it) }
            ?: reservations

        return BudgetExecutionResult(updatedPosition, updatedEntries, updatedReservations)
    }

    fun reviseBuy(
        position: PortfolioPosition,
        entries: List<BudgetJournalEntry>,
        purchase: PortfolioPurchase,
        reservations: List<BudgetReservation> = emptyList()
    ): BudgetExecutionResult {
        val existingEntry = entries.firstOrNull {
            it.id == purchase.id &&
                it.type == BudgetJournalType.BUY_DEBIT &&
                it.itemId == position.itemId
        } ?: return BudgetExecutionResult(position, entries, reservations, "Budgetbuchung für Kauf fehlt")

        if (purchase.id.isBlank() || purchase.date.isBlank() ||
            !purchase.investedAmount.isFinite() || purchase.investedAmount <= 0.0 ||
            !purchase.shares.isFinite() || purchase.shares <= 0.0
        ) {
            return BudgetExecutionResult(position, entries, reservations, "Ungültige Kaufdaten")
        }

        val summary = InvestmentBudgetJournalEngine.summarize(entries, reservations)
        val restoredBuyingPower = summary.availableEur + existingEntry.amountEur
        if (purchase.investedAmount > restoredBuyingPower + EPSILON) {
            return BudgetExecutionResult(position, entries, reservations, "Nicht genügend verfügbares Budget")
        }

        val updatedPosition = position.upsertPurchaseIfValid(purchase)
            ?: return BudgetExecutionResult(position, entries, reservations, "Kauf konnte nicht aktualisiert werden")
        val updatedEntries = InvestmentBudgetJournalEngine.upsert(
            entries,
            existingEntry.copy(
                amountEur = purchase.investedAmount,
                date = purchase.date,
                note = "Kauf aktualisiert"
            )
        )
        return BudgetExecutionResult(updatedPosition, updatedEntries, reservations)
    }

    fun deleteBuy(
        position: PortfolioPosition,
        entries: List<BudgetJournalEntry>,
        purchaseId: String,
        reservations: List<BudgetReservation> = emptyList()
    ): BudgetExecutionResult {
        val matchingEntry = entries.firstOrNull {
            it.id == purchaseId &&
                it.type == BudgetJournalType.BUY_DEBIT &&
                it.itemId == position.itemId
        } ?: return BudgetExecutionResult(position, entries, reservations, "Budgetbuchung für Kauf fehlt")

        val updatedPosition = position.removePurchaseIfValid(purchaseId)
            ?: return BudgetExecutionResult(position, entries, reservations, "Kauf kann wegen späterer Verkäufe nicht gelöscht werden")
        val updatedEntries = entries.filterNot { it.id == matchingEntry.id }
        return BudgetExecutionResult(updatedPosition, updatedEntries, reservations)
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
            !request.feeEur.isFinite() || request.feeEur < 0.0 ||
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
                note = "Verkauf ausgeführt",
                feeEur = request.feeEur
            )
        )

        return BudgetExecutionResult(updatedPosition, updatedEntries, reservations)
    }

    fun reviseSale(
        position: PortfolioPosition,
        entries: List<BudgetJournalEntry>,
        sale: PortfolioSale,
        reservations: List<BudgetReservation> = emptyList()
    ): BudgetExecutionResult {
        val existingEntry = entries.firstOrNull {
            it.id == sale.id &&
                it.type == BudgetJournalType.SELL_CREDIT &&
                it.itemId == position.itemId
        } ?: return BudgetExecutionResult(position, entries, reservations, "Budgetbuchung für Verkauf fehlt")

        if (sale.id.isBlank() || sale.date.isBlank() ||
            !sale.proceeds.isFinite() || sale.proceeds < 0.0 ||
            !sale.shares.isFinite() || sale.shares <= 0.0
        ) {
            return BudgetExecutionResult(position, entries, reservations, "Ungültige Verkaufsdaten")
        }

        val updatedPosition = position.upsertSale(sale)
            ?: return BudgetExecutionResult(position, entries, reservations, "Verkauf übersteigt den Bestand")
        val updatedEntries = InvestmentBudgetJournalEngine.upsert(
            entries,
            existingEntry.copy(
                amountEur = sale.proceeds,
                date = sale.date,
                note = "Verkauf aktualisiert"
            )
        )
        return BudgetExecutionResult(updatedPosition, updatedEntries, reservations)
    }

    fun deleteSale(
        position: PortfolioPosition,
        entries: List<BudgetJournalEntry>,
        saleId: String,
        reservations: List<BudgetReservation> = emptyList()
    ): BudgetExecutionResult {
        val matchingEntry = entries.firstOrNull {
            it.id == saleId &&
                it.type == BudgetJournalType.SELL_CREDIT &&
                it.itemId == position.itemId
        } ?: return BudgetExecutionResult(position, entries, reservations, "Budgetbuchung für Verkauf fehlt")

        val updatedPosition = position.removeSale(saleId)
        val updatedEntries = entries.filterNot { it.id == matchingEntry.id }
        return BudgetExecutionResult(updatedPosition, updatedEntries, reservations)
    }

    private const val EPSILON = 0.000001
}
