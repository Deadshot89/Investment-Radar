package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPortfolioSeedTest {
    @Test
    fun latestTradeRepublicSnapshotMatchesCurrentSevenPositionDepot() {
        val snapshot = UserPortfolioSeed.latestSnapshotValues()

        assertEquals(7, snapshot.size)
        assertEquals(1714.83, snapshot.getValue("meta"), 0.001)
        assertEquals(110.89, snapshot.getValue("custom-nel-asa"), 0.001)
        assertEquals(50.15, snapshot.getValue("spyi"), 0.001)
        assertEquals(42.96, snapshot.getValue("custom-samsung-gdr"), 0.001)
        assertEquals(32.08, snapshot.getValue("msft"), 0.001)
        assertEquals(20.19, snapshot.getValue("is3s"), 0.001)
        assertEquals(15.14, snapshot.getValue("googl"), 0.001)
        assertEquals(1986.24, snapshot.values.sum(), 0.001)
    }

    @Test
    fun onlyUnknownCostBasisSnapshotPositionsAreSafeToRefresh() {
        val imported = PortfolioPosition(itemId = "meta", snapshotValueEur = 1675.88)
        val tracked = imported.copy(trackedShares = 2.0)
        val purchased = imported.copy(
            purchases = listOf(PortfolioPurchase("buy", "2026-09-03", 100.0, 0.15))
        )

        assertTrue(UserPortfolioSeed.canRefreshSnapshot(imported))
        assertFalse(UserPortfolioSeed.canRefreshSnapshot(tracked))
        assertFalse(UserPortfolioSeed.canRefreshSnapshot(purchased))
    }
}
