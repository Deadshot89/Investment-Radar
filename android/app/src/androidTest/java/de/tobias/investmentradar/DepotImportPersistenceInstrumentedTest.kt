package de.tobias.investmentradar

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DepotImportPersistenceInstrumentedTest {
    private fun metadata() = PortfolioImportMetadata(
        importId = "import-cross-store",
        capturedAt = "2026-10-15T12:20:00Z",
        screenshotCount = 2,
        updatedCount = 0,
        addedCount = 1,
        soldCount = 0,
        deletedCount = 0
    )

    private fun importResult(context: android.content.Context): PortfolioImportResult {
        val draft = CustomAssetDraft(
            id = "custom-nvda",
            name = "NVIDIA",
            ticker = "NVDA",
            isin = "US67066G1040",
            type = "Aktie"
        )
        return PortfolioImportApplier.preview(
            existingPositions = PortfolioStore.readPositions(context),
            existingCustomAssets = CustomInvestmentStore.read(context),
            changes = listOf(
                PortfolioImportChange.AddPosition(
                    itemId = draft.id,
                    valueEur = 88.0,
                    costBasisEur = 80.0,
                    customAssetDraft = draft
                )
            ),
            capturedAt = metadata().capturedAt
        )
    }

    @Test
    fun customWriteFailureRestoresPortfolioAndCustomPreferencesExactly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val portfolioPrefs = context.getSharedPreferences("investment_radar_portfolio", 0)
        val customPrefs = context.getSharedPreferences("investment_radar_custom_assets", 0)
        val budgetPrefs = context.getSharedPreferences("investment_radar_budget", 0)
        portfolioPrefs.edit().clear().commit()
        customPrefs.edit().clear().commit()
        budgetPrefs.edit().clear().commit()
        try {
            PortfolioStore.save(context, PortfolioPosition("meta", snapshotValueEur = 100.0))
            CustomInvestmentStore.save(
                context,
                CustomInvestment("custom-old", "Old Asset", "OLD", "", "Aktie")
            )
            seedStableImportBudget(context, "custom-rollback")
            val portfolioBefore = portfolioPrefs.all.toMap()
            val customBefore = customPrefs.all.toMap()
            val budgetBefore = budgetPrefs.all.toMap()

            val committed = DepotImportPersistence.commit(
                context = context,
                result = importResult(context),
                metadata = metadata(),
                customCommitEditor = { editor: SharedPreferences.Editor ->
                    editor.commit()
                    false
                }
            )

            assertFalse(committed)
            assertEquals(portfolioBefore, portfolioPrefs.all.toMap())
            assertEquals(customBefore, customPrefs.all.toMap())
            assertEquals(budgetBefore, budgetPrefs.all.toMap())
        } finally {
            portfolioPrefs.edit().clear().commit()
            customPrefs.edit().clear().commit()
            budgetPrefs.edit().clear().commit()
        }
    }

    @Test
    fun portfolioWriteFailureRollsBackAlreadyCommittedCustomAssetsExactly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val portfolioPrefs = context.getSharedPreferences("investment_radar_portfolio", 0)
        val customPrefs = context.getSharedPreferences("investment_radar_custom_assets", 0)
        val budgetPrefs = context.getSharedPreferences("investment_radar_budget", 0)
        portfolioPrefs.edit().clear().commit()
        customPrefs.edit().clear().commit()
        budgetPrefs.edit().clear().commit()
        try {
            PortfolioStore.save(context, PortfolioPosition("meta", snapshotValueEur = 100.0))
            CustomInvestmentStore.save(
                context,
                CustomInvestment("custom-old", "Old Asset", "OLD", "", "Aktie")
            )
            seedStableImportBudget(context, "portfolio-rollback")
            val portfolioBefore = portfolioPrefs.all.toMap()
            val customBefore = customPrefs.all.toMap()
            val budgetBefore = budgetPrefs.all.toMap()

            val committed = DepotImportPersistence.commit(
                context = context,
                result = importResult(context),
                metadata = metadata(),
                portfolioCommitEditor = { editor: SharedPreferences.Editor ->
                    editor.commit()
                    false
                }
            )

            assertFalse(committed)
            assertEquals(portfolioBefore, portfolioPrefs.all.toMap())
            assertEquals(customBefore, customPrefs.all.toMap())
            assertEquals(budgetBefore, budgetPrefs.all.toMap())
        } finally {
            portfolioPrefs.edit().clear().commit()
            customPrefs.edit().clear().commit()
            budgetPrefs.edit().clear().commit()
        }
    }
}
