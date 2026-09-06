package de.tobias.investmentradar

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainBackHandlerContractTest {
    @Test fun rootUiUsesCentralBackReducerInsteadOfDirectTabReset() {
        val candidates = listOf(
            File(System.getProperty("user.dir"), "src/main/java/de/tobias/investmentradar/MainActivity.kt"),
            File(System.getProperty("user.dir"), "app/src/main/java/de/tobias/investmentradar/MainActivity.kt"),
            File(System.getProperty("user.dir"), "android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt")
        )
        val source = candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Could not locate MainActivity.kt")

        assertTrue(source.contains("import androidx.activity.compose.BackHandler"))
        assertTrue(source.contains("val navigationState = AppNavigationState("))
        assertTrue(source.contains("navigationState.onBack()"))
        assertTrue(source.contains("AppChildScreen.SAVINGS_PLANS"))
        assertFalse(source.contains("BackHandler(enabled = tab != 0 && selectedDetailId == null)"))
    }
}
