package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPortfolioSeedTest {
    @Test
    fun latestTradeRepublicSnapshotMatchesCurrentEightPositionDepot() {
        val snapshot = UserPortfolioSeed.latestSnapshotValues()

        assertEquals(8, snapshot.size)
        assertEquals(1213.11, snapshot.getValue("meta"), 0.001)
        assertEquals(113.45, snapshot.getValue("custom-nel-asa"), 0.001)
        assertEquals(50.25, snapshot.getValue("spyi"), 0.001)
        assertEquals(45.45, snapshot.getValue("custom-samsung-gdr"), 0.001)
        assertEquals(31.31, snapshot.getValue("msft"), 0.001)
        assertEquals(20.30, snapshot.getValue("is3s"), 0.001)
        assertEquals(15.00, snapshot.getValue("googl"), 0.001)
        assertEquals(3.02, snapshot.getValue("custom-ibonds-dec-2026-usd"), 0.001)
        assertEquals(1491.89, snapshot.values.sum(), 0.001)
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
