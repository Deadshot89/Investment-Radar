package de.tobias.investmentradar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanDecimalInputTest {
    @Test
    fun germanGroupedCurrencyKeepsItsMagnitude() {
        assertEquals("1000,50", GermanDecimalInput.sanitize("1.000,50"))
        assertEquals(1000.50, GermanDecimalInput.parse("1.000,50")!!, 0.000001)
    }

    @Test
    fun internationalGroupedCurrencyUsesLastSeparatorAsDecimal() {
        assertEquals("1000,50", GermanDecimalInput.sanitize("1,000.50"))
        assertEquals(1000.50, GermanDecimalInput.parse("1,000.50")!!, 0.000001)
    }

    @Test
    fun ungroupedDecimalInputStaysCompatible() {
        assertEquals("1000,50", GermanDecimalInput.sanitize("1000,50"))
        assertEquals(1.5, GermanDecimalInput.parse("1.5")!!, 0.000001)
    }

    @Test
    fun trackedSharesDialogUsesSharedGroupedDecimalParser() {
        val dashboard = listOf(
            File(System.getProperty("user.dir"), "src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"),
            File(System.getProperty("user.dir"), "app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt")
        ).firstOrNull { it.exists() } ?: error("PortfolioDashboard.kt not found")
        val source = dashboard.readText()

        assertTrue(source.contains("val parsed = GermanDecimalInput.parse(input)"))
        assertTrue(source.contains("onValueChange = { input = GermanDecimalInput.sanitize(it) }"))
        assertFalse(source.contains("input.trim().replace(',', '.').toDoubleOrNull()"))
    }
}
