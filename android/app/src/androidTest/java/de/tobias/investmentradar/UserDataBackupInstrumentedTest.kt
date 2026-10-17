package de.tobias.investmentradar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDataBackupInstrumentedTest {
    @Test
    fun incompleteBackupIsRejectedWithoutChangingExistingData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val budgetPrefs = context.getSharedPreferences("investment_radar_budget", 0)
        budgetPrefs.edit().putString("entries_v1", "sentinel").commit()

        try {
            val incomplete = UserDataBackupCodec.encode(
                preferences = mapOf(
                    "investment_radar_budget" to mapOf("entries_v1" to "other")
                ),
                appVersion = "instrumented-test",
                createdAt = "2026-09-22T12:00:00Z"
            )

            assertTrue(runCatching { UserDataBackupManager.restoreJson(context, incomplete) }.isFailure)
            assertEquals("sentinel", budgetPrefs.getString("entries_v1", null))
        } finally {
            budgetPrefs.edit().clear().commit()
        }
    }

    @Test
    fun exportThenRestoreRecoversFinancialPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        UserDataBackupManager.preferenceFiles.forEach { fileName ->
            context.getSharedPreferences(fileName, 0).edit().clear().commit()
        }

        try {
            context.getSharedPreferences("investment_radar_portfolio", 0)
                .edit()
                .putStringSet("holding_ids", setOf("msft", "meta"))
                .putString("position.msft.invested", "42.0")
                .commit()
            context.getSharedPreferences("investment_radar_budget", 0)
                .edit()
                .putString("entries_v1", "[{\"id\":\"month\",\"type\":\"MONTHLY_DEPOSIT\",\"amountEur\":100.0,\"date\":\"2026-09-01\"}]")
                .commit()

            val backup = UserDataBackupManager.exportJson(
                context = context,
                appVersion = "instrumented-test",
                createdAt = "2026-09-22T12:00:00Z"
            )

            context.getSharedPreferences("investment_radar_portfolio", 0).edit().clear().commit()
            context.getSharedPreferences("investment_radar_budget", 0)
                .edit()
                .putString("entries_v1", "[]")
                .commit()

            val restoredValues = UserDataBackupManager.restoreJson(context, backup)

            val restoredHoldings = context.getSharedPreferences("investment_radar_portfolio", 0)
                .getStringSet("holding_ids", emptySet())
                .orEmpty()
            val restoredBudget = context.getSharedPreferences("investment_radar_budget", 0)
                .getString("entries_v1", null)

            assertEquals(setOf("msft", "meta"), restoredHoldings)
            assertTrue(restoredBudget?.contains("MONTHLY_DEPOSIT") == true)
            assertTrue(restoredValues >= 3)
        } finally {
            UserDataBackupManager.preferenceFiles.forEach { fileName ->
                context.getSharedPreferences(fileName, 0).edit().clear().commit()
            }
        }
    }

    @Test
    fun screenshotImportLifecycleMetadataAndCustomAssetRoundTripWithoutPrivateImagePayload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        UserDataBackupManager.preferenceFiles.forEach { fileName ->
            context.getSharedPreferences(fileName, 0).edit().clear().commit()
        }

        try {
            val custom = CustomInvestment(
                id = "custom-test-ag",
                name = "Test AG",
                ticker = "TEST",
                isin = "DE000TEST001",
                type = "Aktie"
            )
            assertTrue(CustomInvestmentStore.replaceAllForImport(context, listOf(custom)))

            val sold = PortfolioPosition(
                itemId = "meta",
                snapshotValueEur = 240.0,
                snapshotCostBasisEur = 200.0,
                trackedShares = 2.0,
                purchases = listOf(
                    PortfolioPurchase(
                        id = "buy-meta",
                        date = "2026-01-05",
                        investedAmount = 200.0,
                        shares = 2.0
                    )
                ),
                lifecycleStatus = PortfolioLifecycleStatus.SOLD_MARKED,
                snapshotCapturedAt = "2026-10-15T10:00:00Z"
            )
            val importedCustom = PortfolioPosition(
                itemId = custom.id,
                snapshotValueEur = 75.5,
                snapshotCostBasisEur = 70.0,
                lifecycleStatus = PortfolioLifecycleStatus.ACTIVE,
                snapshotCapturedAt = "2026-10-15T10:00:00Z"
            )
            val result = PortfolioImportResult(
                positions = mapOf(
                    sold.itemId to sold,
                    importedCustom.itemId to importedCustom
                ),
                customAssets = listOf(custom),
                summary = PortfolioImportSummary(
                    updatedCount = 1,
                    addedCount = 1,
                    soldCount = 1
                )
            )
            assertTrue(
                PortfolioStore.commitImport(
                    context = context,
                    result = result,
                    metadata = PortfolioImportMetadata(
                        importId = "backup-round-trip",
                        capturedAt = "2026-10-15T10:00:00Z",
                        screenshotCount = 2,
                        updatedCount = 1,
                        addedCount = 1,
                        soldCount = 1,
                        deletedCount = 0
                    )
                )
            )
            context.getSharedPreferences("investment_radar_budget", 0)
                .edit()
                .putString("entries_v1", "[{\"id\":\"monthly\",\"type\":\"MONTHLY_DEPOSIT\",\"amountEur\":100.0,\"date\":\"2026-10-01\"}]")
                .commit()

            val expected = UserDataBackupManager.preferenceFiles.associateWith { fileName ->
                context.getSharedPreferences(fileName, 0).all.mapValues { (_, value) ->
                    if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
                }
            }

            val backup = UserDataBackupManager.exportJson(
                context = context,
                appVersion = "instrumented-test",
                createdAt = "2026-10-16T07:00:00Z"
            )

            assertTrue(backup.contains("SOLD_MARKED"))
            assertTrue(backup.contains("2026-10-15T10:00:00Z"))
            assertTrue(backup.contains("backup-round-trip"))
            assertTrue(backup.contains("custom-test-ag"))
            assertTrue(!backup.contains("content://", ignoreCase = true))
            assertTrue(!backup.contains("uriString", ignoreCase = true))
            assertTrue(!backup.contains("ocrRaw", ignoreCase = true))
            assertTrue(!backup.contains("bitmapPath", ignoreCase = true))

            UserDataBackupManager.preferenceFiles.forEach { fileName ->
                context.getSharedPreferences(fileName, 0).edit().clear().commit()
            }

            UserDataBackupManager.restoreJson(context, backup)

            val actual = UserDataBackupManager.preferenceFiles.associateWith { fileName ->
                context.getSharedPreferences(fileName, 0).all.mapValues { (_, value) ->
                    if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
                }
            }
            assertEquals(expected, actual)

            val restoredPositions = PortfolioStore.readPositions(context)
            assertEquals(PortfolioLifecycleStatus.SOLD_MARKED, restoredPositions.getValue("meta").lifecycleStatus)
            assertEquals("2026-10-15T10:00:00Z", restoredPositions.getValue("meta").snapshotCapturedAt)
            assertEquals(listOf(custom), CustomInvestmentStore.read(context))
        } finally {
            UserDataBackupManager.preferenceFiles.forEach { fileName ->
                context.getSharedPreferences(fileName, 0).edit().clear().commit()
            }
        }
    }
}