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

        val forbidden = Regex("nicht\\s+verfügbar", RegexOption.IGNORE_CASE)
        val matches = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    if (forbidden.containsMatchIn(line)) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()

        if (matches.isNotEmpty()) {
            println("GENERIC_UNAVAILABLE_MATCHES")
            matches.forEach(::println)
        }

        assertTrue(
            "Visible Android copy must explain the concrete cause instead of using generic copy: ${matches.size} matches",
            matches.isEmpty()
        )
    }
}
