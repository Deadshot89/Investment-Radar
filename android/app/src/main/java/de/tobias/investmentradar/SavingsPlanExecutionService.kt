package de.tobias.investmentradar

interface SavingsPlanQuoteProvider {
    fun currentPriceEur(itemId: String): Double?
}

interface SavingsPlanPortfolioWriter {
    fun persistPurchase(itemId: String, purchase: PortfolioPurchase): Boolean
}

interface SavingsPlanExecutionRepository {
    fun getExecution(id: String): SavingsPlanExecution?
    fun saveExecution(execution: SavingsPlanExecution)
}

sealed interface SavingsPlanConfirmationResult {
    data class Confirmed(
        val executionId: String,
        val priceEur: Double,
        val shares: Double
    ) : SavingsPlanConfirmationResult

    data class AlreadyConfirmed(val executionId: String) : SavingsPlanConfirmationResult
    data object ExecutionMissing : SavingsPlanConfirmationResult
    data object InstrumentMissing : SavingsPlanConfirmationResult
    data object PriceUnavailable : SavingsPlanConfirmationResult
    data object PurchasePersistenceFailed : SavingsPlanConfirmationResult
}

class SavingsPlanExecutionService(
    private val quoteProvider: SavingsPlanQuoteProvider,
    private val portfolioWriter: SavingsPlanPortfolioWriter,
    private val executionRepository: SavingsPlanExecutionRepository
) {
    fun confirm(
        plan: SavingsPlan,
        executionId: String,
        confirmedAt: String
    ): SavingsPlanConfirmationResult {
        val execution = executionRepository.getExecution(executionId)
            ?: return SavingsPlanConfirmationResult.ExecutionMissing

        if (execution.status == SavingsPlanExecutionStatus.CONFIRMED) {
            return SavingsPlanConfirmationResult.AlreadyConfirmed(executionId)
        }
        if (execution.status != SavingsPlanExecutionStatus.PENDING) {
            return SavingsPlanConfirmationResult.ExecutionMissing
        }

        val itemId = plan.itemId?.takeIf { it.isNotBlank() }
            ?: return SavingsPlanConfirmationResult.InstrumentMissing
        val price = quoteProvider.currentPriceEur(itemId)
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return SavingsPlanConfirmationResult.PriceUnavailable
        val shares = execution.amountEur / price
        if (!shares.isFinite() || shares <= 0.0) {
            return SavingsPlanConfirmationResult.PriceUnavailable
        }

        val purchase = PortfolioPurchase(
            id = "savings-plan-${execution.id}",
            date = execution.scheduledDate,
            investedAmount = execution.amountEur,
            shares = shares
        )
        if (!portfolioWriter.persistPurchase(itemId, purchase)) {
            return SavingsPlanConfirmationResult.PurchasePersistenceFailed
        }

        executionRepository.saveExecution(
            execution.copy(
                status = SavingsPlanExecutionStatus.CONFIRMED,
                confirmedAt = confirmedAt,
                priceEur = price,
                shares = shares
            )
        )
        return SavingsPlanConfirmationResult.Confirmed(execution.id, price, shares)
    }

    fun skip(executionId: String): Boolean {
        val execution = executionRepository.getExecution(executionId) ?: return false
        if (execution.status != SavingsPlanExecutionStatus.PENDING) return false
        executionRepository.saveExecution(execution.copy(status = SavingsPlanExecutionStatus.SKIPPED))
        return true
    }
}
