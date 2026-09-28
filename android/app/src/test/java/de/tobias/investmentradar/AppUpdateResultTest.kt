package de.tobias.investmentradar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateResultTest {
    @Test fun semanticVersionComparisonTreats120AsNewerThan1129() {
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0", "1.1.29"))
        assertFalse(AppUpdateManager.isNewerVersion("1.1.29", "1.1.29"))
        assertTrue(AppUpdateManager.isNewerVersion("2.5.24", "2.5.23"))
        assertFalse(AppUpdateManager.isNewerVersion("2.5.23", "2.5.24"))
    }

    @Test fun currentResultCarriesInstalledVersion() {
        val result: UpdateCheckResult = UpdateCheckResult.Current("1.2.0")
        assertTrue(result is UpdateCheckResult.Current && result.versionName == "1.2.0")
    }
}
