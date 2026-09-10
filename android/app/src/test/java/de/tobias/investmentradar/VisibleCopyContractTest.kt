package de.tobias.investmentradar

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleCopyContractTest {
    @Test fun visibleKotlinSourceDoesNotUseGenericUnavailableCopy() {
        val candidates = listOf(
            File(System.getProperty("user.dir"), "src/main/java"),
            File(System.getProperty("user.dir"), "app/src/main/java"),
            File(System.getProperty("user.dir"), "android/app/src/main/java")
        )
        val root = candidates.firstOrNull(File::isDirectory)
            ?: error("Could not locate Android main source directory from ${System.getProperty("user.dir")}")

        // A metric-specific value such as "Kurs: Nicht verfügbar" is useful because it
        // identifies exactly what is missing. What we reject is a generic standalone
        // unavailable message that gives the user no actionable context.
        val forbiddenStandalone = Regex(
            "Text\\(\\s*\\\"(?:Derzeit |Aktuell |Noch )?Nicht verfügbar[.!]?\\\"\\s*\\)",
            RegexOption.IGNORE_CASE
        )
        val matches = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    if (forbiddenStandalone.containsMatchIn(line)) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()

        assertTrue(
            "Visible Android copy must identify what is unavailable instead of using standalone generic wording:\n${matches.joinToString("\n")}",
            matches.isEmpty()
        )
    }
}
