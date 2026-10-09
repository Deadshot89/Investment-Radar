package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanDecimalInputTest {
    @Test
    fun germanGroupedCurrencyKeepsItsMagnitude() {
        assertEquals("1000,50", GermanDecimalInput.sanitize("1.000,50"))
        assertEquals(1000.50, GermanDecimalInput.parse("1.000,50")!!, 0.000001)
    }
}
