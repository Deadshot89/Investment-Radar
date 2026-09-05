package de.tobias.investmentradar

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainBackHandlerContractTest {
    @Test fun rootUiConsumesBackWhileInsideNonRootTab() {
        val candidates = listOf(
            File(System.getProperty("user.dir"), "src/main/java/de/tobias/investmentradar/MainActivity.kt"),
            File(System.getProperty("user.dir"), "app/src/main/java/de/tobias/investmentradar/MainActivity.kt"),
            File(System.getProperty("user.dir"), "android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt")
        )
        val source = candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Could not locate MainActivity.kt")

        assertTrue(source.contains("import androidx.activity.compose.BackHandler"))
        assertTrue(source.contains("BackHandler(enabled = tab != 0"))
        assertTrue(source.contains("tab = 0"))
    }
}
