package de.tobias.investmentradar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPortfolioSeedTest {
    @Test
    fun onlyUnknownLegacySnapshotPositionsRemainEligibleForExplicitRefresh() {
        val imported = PortfolioPosition(itemId = "meta", snapshotValueEur = 1675.88)
        val tracked = imported.copy(trackedShares = 2.0)
        val purchased = imported.copy(
            purchases = listOf(PortfolioPurchase("buy", "2026-09-03", 100.0, 0.15))
        )

        assertTrue(UserPortfolioSeed.canRefreshSnapshot(imported))
        assertFalse(UserPortfolioSeed.canRefreshSnapshot(tracked))
        assertFalse(UserPortfolioSeed.canRefreshSnapshot(purchased))
    }

    @Test
    fun transactionHistoryPreventsImportedCostBasisOverwrite() {
        val imported = PortfolioPosition(itemId = "meta", snapshotValueEur = 100.0)
        val purchased = imported.copy(
            purchases = listOf(PortfolioPurchase("buy", "2026-09-03", 100.0, 0.15))
        )

        assertTrue(UserPortfolioSeed.canApplyImportedCostBasis(imported))
        assertFalse(UserPortfolioSeed.canApplyImportedCostBasis(purchased))
    }
}
