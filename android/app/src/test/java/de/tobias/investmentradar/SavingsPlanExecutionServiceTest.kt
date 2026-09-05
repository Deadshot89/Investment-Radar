package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsPlanExecutionServiceTest {
    private class FakeQuoteProvider(var quote: Double?) : SavingsPlanQuoteProvider {
        var calls = 0
        override fun currentPriceEur(itemId: String): Double? {
            calls += 1
            return quote
        }
    }

    private class FakePortfolioWriter : SavingsPlanPortfolioWriter {
        val purchases = mutableListOf<Pair<String, PortfolioPurchase>>()
        override fun persistPurchase(itemId: String, purchase: PortfolioPurchase): Boolean {
            purchases += itemId to purchase
            return true
        }
    }

    private class FakeExecutionRepository(initial: SavingsPlanExecution) : SavingsPlanExecutionRepository {
        var execution = initial
        override fun getExecution(id: String): SavingsPlanExecution? = execution.takeIf { it.id == id }
        override fun saveExecution(execution: SavingsPlanExecution) { this.execution = execution }
    }

    private fun plan(itemId: String? = "meta") = SavingsPlan(
        id = "plan-meta",
        name = "Meta Platforms (A)",
        itemId = itemId,
        amountEur = 10.0,
        frequency = SavingsPlanFrequency.TWICE_MONTHLY,
        dayOfMonth1 = 2,
        dayOfMonth2 = 16,
        nextDueDate = "2026-09-16",
        enabled = true
    )

    @Test
    fun confirmationUsesAutomaticQuoteAndBooksCalculatedShares() {
        val execution = SavingsPlanExecution.pending("plan-meta", "2026-09-16", 10.0)
        val quote = FakeQuoteProvider(500.0)
        val writer = FakePortfolioWriter()
        val repo = FakeExecutionRepository(execution)
        val service = SavingsPlanExecutionService(quote, writer, repo)

        val result = service.confirm(plan(), execution.id, confirmedAt = "2026-09-16T08:00:00Z")

        assertTrue(result is SavingsPlanConfirmationResult.Confirmed)
        val confirmed = result as SavingsPlanConfirmationResult.Confirmed
        assertEquals(0.02, confirmed.shares, 0.000000001)
        assertEquals(500.0, confirmed.priceEur, 0.0)
        assertEquals(1, writer.purchases.size)
        assertEquals("meta", writer.purchases.single().first)
        assertEquals(10.0, writer.purchases.single().second.investedAmount, 0.0)
        assertEquals(0.02, writer.purchases.single().second.shares, 0.000000001)
        assertEquals(SavingsPlanExecutionStatus.CONFIRMED, repo.execution.status)
    }

    @Test
    fun invalidQuoteLeavesExecutionPendingAndCreatesNoPurchase() {
        val execution = SavingsPlanExecution.pending("plan-meta", "2026-09-16", 10.0)
        val writer = FakePortfolioWriter()
        val repo = FakeExecutionRepository(execution)
        val service = SavingsPlanExecutionService(FakeQuoteProvider(0.0), writer, repo)

        val result = service.confirm(plan(), execution.id, "2026-09-16T08:00:00Z")

        assertTrue(result is SavingsPlanConfirmationResult.PriceUnavailable)
        assertTrue(writer.purchases.isEmpty())
        assertEquals(SavingsPlanExecutionStatus.PENDING, repo.execution.status)
    }

    @Test
    fun missingInstrumentCannotBookPrivateEquityIntoWrongAsset() {
        val execution = SavingsPlanExecution.pending("plan-meta", "2026-09-16", 5.0)
        val writer = FakePortfolioWriter()
        val repo = FakeExecutionRepository(execution)
        val service = SavingsPlanExecutionService(FakeQuoteProvider(100.0), writer, repo)

        val result = service.confirm(plan(itemId = null), execution.id, "2026-09-16T08:00:00Z")

        assertTrue(result is SavingsPlanConfirmationResult.InstrumentMissing)
        assertTrue(writer.purchases.isEmpty())
    }

    @Test
    fun repeatedConfirmationIsIdempotent() {
        val execution = SavingsPlanExecution.pending("plan-meta", "2026-09-16", 10.0)
        val quote = FakeQuoteProvider(500.0)
        val writer = FakePortfolioWriter()
        val repo = FakeExecutionRepository(execution)
        val service = SavingsPlanExecutionService(quote, writer, repo)

        val first = service.confirm(plan(), execution.id, "2026-09-16T08:00:00Z")
        val second = service.confirm(plan(), execution.id, "2026-09-16T08:05:00Z")

        assertTrue(first is SavingsPlanConfirmationResult.Confirmed)
        assertTrue(second is SavingsPlanConfirmationResult.AlreadyConfirmed)
        assertEquals(1, writer.purchases.size)
        assertEquals(1, quote.calls)
    }

    @Test
    fun skipChangesOnlyExecutionState() {
        val execution = SavingsPlanExecution.pending("plan-meta", "2026-09-16", 10.0)
        val writer = FakePortfolioWriter()
        val repo = FakeExecutionRepository(execution)
        val service = SavingsPlanExecutionService(FakeQuoteProvider(500.0), writer, repo)

        val result = service.skip(execution.id)

        assertTrue(result)
        assertEquals(SavingsPlanExecutionStatus.SKIPPED, repo.execution.status)
        assertTrue(writer.purchases.isEmpty())
    }
}
