package de.tobias.investmentradar

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortfolioImportStoreInstrumentedTest {
    private fun metadata() = PortfolioImportMetadata(
        importId = "import-2026-10-15",
        capturedAt = "2026-10-15T12:10:00Z",
        screenshotCount = 2,
        updatedCount = 0,
        addedCount = 0,
        soldCount = 1,
        deletedCount = 0
    )

    @Test
    fun commitImportDoesNotTouchBudgetPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val portfolioPrefs = context.getSharedPreferences("investment_radar_portfolio", 0)
        val budgetPrefs = context.getSharedPreferences("investment_radar_budget", 0)
        portfolioPrefs.edit().clear().commit()
        budgetPrefs.edit().clear().commit()
        try {
            val initial = PortfolioPosition(
                itemId = "meta",
                snapshotValueEur = 100.0,
                purchases = listOf(PortfolioPurchase("buy", "2026-09-01", 90.0, 1.0))
            )
            PortfolioStore.save(context, initial)
            budgetPrefs.edit()
                .putString("entries_v1", "sentinel-entries")
                .putString("reservations_v1", "sentinel-reservations")
                .commit()
            val budgetBefore = budgetPrefs.all.toMap()

            val result = PortfolioImportApplier.preview(
                existing = mapOf("meta" to initial),
                changes = listOf(
                    PortfolioImportChange.MissingPosition("meta", MissingPositionDecision.MARK_SOLD)
                ),
                capturedAt = "2026-10-15T12:10:00Z"
            )

            assertTrue(PortfolioStore.commitImport(context, result, metadata()))
            assertEquals(PortfolioLifecycleStatus.SOLD_MARKED, PortfolioStore.readPositions(context).getValue("meta").lifecycleStatus)
            assertEquals(budgetBefore, budgetPrefs.all.toMap())
        } finally {
            portfolioPrefs.edit().clear().commit()
            budgetPrefs.edit().clear().commit()
        }
    }

    @Test
    fun failedCommitRestoresPreviousSerializedPortfolio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("investment_radar_portfolio", 0)
        prefs.edit().clear().commit()
        try {
            val initial = PortfolioPosition(
                itemId = "meta",
                snapshotValueEur = 100.0,
                snapshotCostBasisEur = 90.0,
                trackedShares = 1.0,
                purchases = listOf(PortfolioPurchase("buy", "2026-09-01", 90.0, 1.0))
            )
            PortfolioStore.save(context, initial)
            val before = prefs.all.toMap()

            val result = PortfolioImportApplier.preview(
                existing = mapOf("meta" to initial),
                changes = listOf(
                    PortfolioImportChange.UpdateSnapshot("meta", valueEur = 130.0),
                    PortfolioImportChange.MissingPosition("meta", MissingPositionDecision.MARK_SOLD)
                ),
                capturedAt = "2026-10-15T12:11:00Z"
            )

            val committed = PortfolioStore.commitImport(
                context = context,
                result = result,
                metadata = metadata(),
                commitEditor = { editor: SharedPreferences.Editor ->
                    editor.commit()
                    false
                }
            )

            assertFalse(committed)
            assertEquals(before, prefs.all.toMap())
            assertEquals(PortfolioLifecycleStatus.ACTIVE, PortfolioStore.readPositions(context).getValue("meta").lifecycleStatus)
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
