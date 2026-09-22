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
}
