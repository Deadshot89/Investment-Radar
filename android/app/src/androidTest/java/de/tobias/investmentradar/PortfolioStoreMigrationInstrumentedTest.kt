package de.tobias.investmentradar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortfolioStoreMigrationInstrumentedTest {
    @Test
    fun legacyPositionDefaultsToActiveAndLifecycleTimestampRoundTrips() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("investment_radar_portfolio", 0)
        prefs.edit().clear().commit()
        try {
            prefs.edit()
                .putStringSet("position_ids", setOf("meta"))
                .putString("position.meta.snapshotValueEur", "120.0")
                .putString("position.meta.snapshotCostBasisEur", "100.0")
                .putString("position.meta.trackedShares", "2.0")
                .putString(
                    "position.meta.purchases",
                    "[{\"id\":\"buy-1\",\"date\":\"2026-10-01\",\"investedAmount\":100.0,\"shares\":2.0}]"
                )
                .putString("position.meta.sales", "[]")
                .commit()

            val legacy = PortfolioStore.readPositions(context).getValue("meta")
            assertEquals(PortfolioLifecycleStatus.ACTIVE, legacy.lifecycleStatus)
            assertEquals(null, legacy.snapshotCapturedAt)

            val sold = legacy.copy(
                lifecycleStatus = PortfolioLifecycleStatus.SOLD_MARKED,
                snapshotCapturedAt = "2026-10-15T10:00:00Z"
            )
            PortfolioStore.save(context, sold)

            val restored = PortfolioStore.readPositions(context).getValue("meta")
            assertEquals(PortfolioLifecycleStatus.SOLD_MARKED, restored.lifecycleStatus)
            assertEquals("2026-10-15T10:00:00Z", restored.snapshotCapturedAt)
            assertEquals(legacy.purchases, restored.purchases)
            assertEquals(legacy.sales, restored.sales)
            assertTrue(restored.trackedShares == 2.0)
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
