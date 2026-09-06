package de.tobias.investmentradar

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootNavigationWiringContractTest {
    private fun source(path: String): String {
        val candidates = listOf(
            File(System.getProperty("user.dir"), path.removePrefix("android/app/")),
            File(System.getProperty("user.dir"), "app/" + path.removePrefix("android/app/")),
            File(System.getProperty("user.dir"), path)
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Could not locate $path")
    }

    @Test
    fun rootUsesCentralReducerForBackAndSavingsChild() {
        val main = source("android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt")
        val portfolio = source("android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt")
        val savings = source("android/app/src/main/java/de/tobias/investmentradar/SavingsPlansScreen.kt")
        val detail = source("android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt")

        assertTrue(main.contains("AppNavigationState("))
        assertTrue(main.contains("AppChildScreen.SAVINGS_PLANS"))
        assertTrue(main.contains("navigationState.onBack()"))
        assertTrue(main.contains("showSavingsPlans"))
        assertTrue(portfolio.contains("showSavingsPlans: Boolean"))
        assertTrue(portfolio.contains("onShowSavingsPlansChange: (Boolean) -> Unit"))
        assertFalse(savings.contains("import androidx.activity.compose.BackHandler"))
        assertFalse(savings.contains("BackHandler("))
        assertFalse(detail.contains("import androidx.activity.compose.BackHandler"))
        assertFalse(detail.contains("BackHandler("))
        assertFalse(main.contains("BackHandler(enabled = tab != 0 && selectedDetailId == null)"))
    }

    @Test
    fun savingsPushIsResolvedByRootAndOpensSavingsChild() {
        val main = source("android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt")

        assertTrue(main.contains("pendingOpenSavingsPlans"))
        assertTrue(main.contains("intent.getBooleanExtra(\"openSavingsPlans\", false)"))
        assertTrue(main.contains("PushNavigationTarget.resolve("))
        assertTrue(main.contains("initialOpenSavingsPlans"))
    }
}
