package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceLineQuoteStatusTest {
    @Test
    fun undatedQuoteMustNeverBeDescribedAsVerifiedLive() {
        val label = PriceLineQuoteStatus.render(
            hasPrice = true,
            delayed = false,
            fxDelayed = false,
            source = "Provider",
            dataError = null
        )
        assertTrue(label.contains("Kurszeit nicht bestätigt"))
        assertTrue(label.contains("Quelle: Provider"))
        assertFalse(label.contains("Live"))
    }

    @Test
    fun delayedMarketQuoteAndCurrencyConversionAreVisible() {
        val label = PriceLineQuoteStatus.render(
            hasPrice = true,
            delayed = true,
            fxDelayed = true,
            source = "Provider",
            dataError = null
        )
        assertTrue(label.contains("Kurs verzögert"))
        assertTrue(label.contains("EUR-Umrechnung verzögert"))
    }

    @Test
    fun backendWarningDoesNotDisappearForAvailablePrice() {
        val label = PriceLineQuoteStatus.render(
            hasPrice = true,
            delayed = false,
            fxDelayed = false,
            source = "",
            dataError = "provider-warning"
        )
        assertTrue(label.contains("Datenwarnung"))
    }

    @Test
    fun noPriceNeverInventsAQuoteOrProvenance() {
        assertEquals(
            "",
            PriceLineQuoteStatus.render(false, false, false, "Provider", "failed")
        )
    }
}
