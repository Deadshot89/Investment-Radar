package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveQuoteProvenanceResolverTest {
    @Test
    fun manualFallbackIsNotMisrepresentedAsLiveMarketPrice() {
        val status = LiveQuoteProvenanceResolver.resolve(
            marketPriceEur = null,
            manualPriceEur = 42.5,
            marketDelayed = false,
            fxDelayed = false,
            dataError = null,
            dataSource = ""
        )
        assertEquals("Manueller Kurs", status.label)
        assertTrue(status.notice!!.contains("kein Live-Kurs"))
    }

    @Test
    fun delayedMarketQuoteAndFxAreExplicitlyLabeled() {
        val status = LiveQuoteProvenanceResolver.resolve(
            marketPriceEur = 123.45,
            manualPriceEur = 92.0,
            marketDelayed = true,
            fxDelayed = true,
            dataError = null,
            dataSource = "Verified Provider"
        )
        assertEquals("Kurs (verzögert)", status.label)
        assertTrue(status.notice!!.contains("Marktdaten verzögert"))
        assertTrue(status.notice!!.contains("EUR-Umrechnung verzögert"))
        assertTrue(status.notice!!.contains("Quelle: Verified Provider"))
    }

    @Test
    fun backendErrorFlagRemainsVisibleEvenWhenQuotePresent() {
        val status = LiveQuoteProvenanceResolver.resolve(
            marketPriceEur = 100.0,
            manualPriceEur = null,
            marketDelayed = false,
            fxDelayed = false,
            dataError = "upstream",
            dataSource = null
        )
        assertEquals("Kurs", status.label)
        assertTrue(status.notice!!.contains("Datenwarnung"))
    }

    @Test
    fun absentPricesDoNotCreateFakeValues() {
        val status = LiveQuoteProvenanceResolver.resolve(
            marketPriceEur = Double.NaN,
            manualPriceEur = 0.0,
            marketDelayed = true,
            fxDelayed = false,
            dataError = null,
            dataSource = null
        )
        assertEquals("Kurs nicht verfügbar", status.label)
        assertEquals(null, status.notice)
    }

    @Test
    fun sourceDoesNotImplyRealTimePricing() {
        val status = LiveQuoteProvenanceResolver.resolve(
            marketPriceEur = 7.99,
            manualPriceEur = null,
            marketDelayed = false,
            fxDelayed = false,
            dataError = null,
            dataSource = "Provider"
        )
        assertEquals("Kurs", status.label)
        assertTrue(status.notice!!.contains("Quelle: Provider"))
        assertFalse(status.label.contains("Live"))
    }
}
