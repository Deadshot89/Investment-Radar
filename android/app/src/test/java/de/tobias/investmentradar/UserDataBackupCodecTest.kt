package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataBackupCodecTest {
    @Test
    fun `round trip preserves every supported preference type`() {
        val source = mapOf(
            "investment_radar_budget" to mapOf(
                "text" to "100",
                "enabled" to true,
                "count" to 7,
                "timestamp" to 1234567890123L,
                "ratio" to 1.25f,
                "ids" to setOf("msft", "meta")
            )
        )

        val encoded = UserDataBackupCodec.encode(
            preferences = source,
            appVersion = "test",
            createdAt = "2026-09-22T12:00:00Z"
        )
        val decoded = UserDataBackupCodec.decode(encoded)

        assertEquals(1, decoded.schemaVersion)
        assertEquals("test", decoded.appVersion)
        assertEquals("2026-09-22T12:00:00Z", decoded.createdAt)
        assertEquals(source["investment_radar_budget"], decoded.preferences["investment_radar_budget"])
    }

    @Test
    fun `wrong format and future schema are rejected before restore`() {
        val wrongFormat = """{"format":"other","schemaVersion":1,"preferences":{}}"""
        assertTrue(runCatching { UserDataBackupCodec.decode(wrongFormat) }.isFailure)

        val futureSchema = """{"format":"investment-radar-user-backup","schemaVersion":99,"preferences":{}}"""
        assertTrue(runCatching { UserDataBackupCodec.decode(futureSchema) }.isFailure)
    }

    @Test
    fun `backup allowlist contains financial user data but excludes device push state`() {
        val files = UserDataBackupManager.preferenceFiles.toSet()
        assertTrue("investment_radar_portfolio" in files)
        assertTrue("investment_radar_budget" in files)
        assertTrue("investment_radar_settings" in files)
        assertTrue("investment_radar_watchlist" in files)
        assertTrue("investment_radar_savings_plans" in files)
        assertTrue("investment_radar_custom_assets" in files)
        assertTrue("investment_radar_exit_strategy" in files)
        assertFalse("investment_radar_push_diagnostics" in files)
    }
}
