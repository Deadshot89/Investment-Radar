package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioAdvisorStoreTest {
    @Test
    fun latestPlanRoundTripsAllocationsCashReallocationsAndConflicts() {
        val plan = PortfolioAdvisorPlan(
            budgetEur = 100,
            allocations = listOf(
                PortfolioAllocation(
                    itemId = "msft",
                    amountEur = 40,
                    action = PortfolioAdvisorAction.NACHKAUFEN,
                    reason = "Starkes Signal"
                )
            ),
            cashEur = 60,
            reallocations = listOf(
                ReallocationSuggestion(
                    fromItemId = "meta",
                    toItemId = "msft",
                    amountEur = 20,
                    reason = "Klar bessere Chance"
                )
            ),
            savingsPlanConflicts = listOf(
                SavingsPlanConflict(
                    itemId = "meta",
                    monthlySavingsEur = 20,
                    action = PortfolioAdvisorAction.REDUZIEREN
                )
            ),
            candidates = emptyList()
        )

        val encoded = PortfolioAdvisorStore.encode(
            DatedPortfolioAdvisorPlan("2026-09-06", plan)
        )
        val decoded = PortfolioAdvisorStore.decode(encoded)

        assertEquals("2026-09-06", decoded?.analysisDay)
        assertEquals(100, decoded?.plan?.budgetEur)
        assertEquals(40, decoded?.plan?.allocations?.single()?.amountEur)
        assertEquals("msft", decoded?.plan?.allocations?.single()?.itemId)
        assertEquals(60, decoded?.plan?.cashEur)
        assertEquals(20, decoded?.plan?.reallocations?.single()?.amountEur)
        assertEquals("meta", decoded?.plan?.reallocations?.single()?.fromItemId)
        assertEquals(20, decoded?.plan?.savingsPlanConflicts?.single()?.monthlySavingsEur)
        assertEquals(PortfolioAdvisorAction.REDUZIEREN, decoded?.plan?.savingsPlanConflicts?.single()?.action)
    }
}
