package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorNotificationCopyTest {
    @Test
    fun `hold signal is the explicit primary action in title and body`() {
        val copy = AdvisorNotificationCopy.format(
            event = signalEvent(
                previous = AdvisorSignal.NACHKAUFEN,
                current = AdvisorSignal.HALTEN,
                reason = "Tagesverlust allein bricht die Investmentthese nicht"
            ),
            displayName = "Samsung Electronics GDR"
        )

        assertEquals("🟡 HALTEN – Samsung Electronics GDR", copy.title)
        assertTrue(copy.body.startsWith("Entscheidung: HALTEN"))
        assertTrue(copy.body.contains("Tagesverlust allein bricht die Investmentthese nicht"))
    }

    @Test
    fun `buy signal is unmistakably labeled as buy more`() {
        val copy = AdvisorNotificationCopy.format(
            event = signalEvent(
                previous = AdvisorSignal.HALTEN,
                current = AdvisorSignal.NACHKAUFEN,
                reason = "Bewertung und Momentum sind attraktiv"
            ),
            displayName = "Beispiel Aktie"
        )

        assertEquals("🟢 NACHKAUFEN – Beispiel Aktie", copy.title)
        assertTrue(copy.body.startsWith("Entscheidung: NACHKAUFEN"))
    }

    @Test
    fun `reduce signal is unmistakably labeled as reduce`() {
        val copy = AdvisorNotificationCopy.format(
            event = signalEvent(
                previous = AdvisorSignal.HALTEN,
                current = AdvisorSignal.REDUZIEREN,
                reason = "Positionsrisiko ist gestiegen"
            ),
            displayName = "Beispiel Aktie"
        )

        assertEquals("🟠 REDUZIEREN – Beispiel Aktie", copy.title)
        assertTrue(copy.body.startsWith("Entscheidung: REDUZIEREN"))
    }

    @Test
    fun `sell signal is unmistakably labeled as sell`() {
        val copy = AdvisorNotificationCopy.format(
            event = signalEvent(
                previous = AdvisorSignal.HALTEN,
                current = AdvisorSignal.VERKAUFEN,
                reason = "Investmentthese ist gebrochen"
            ),
            displayName = "Beispiel Aktie"
        )

        assertEquals("🔴 VERKAUFEN – Beispiel Aktie", copy.title)
        assertTrue(copy.body.startsWith("Entscheidung: VERKAUFEN"))
    }

    @Test
    fun `unreliable analysis does not invent a trade action`() {
        val event = AdvisorNotificationEvent(
            id = "risk|reliability|2026-09-14",
            instrumentId = "risk",
            previousSignal = AdvisorSignal.HALTEN,
            newSignal = AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG,
            analysisDay = "2026-09-14",
            reasons = listOf("Marktdaten sind unvollständig"),
            kind = AdvisorNotificationEventKind.RELIABILITY_LOST
        )

        val copy = AdvisorNotificationCopy.format(event, "Beispiel Aktie")

        assertEquals("⚠️ NICHT HANDELN – Beispiel Aktie", copy.title)
        assertTrue(copy.body.startsWith("Entscheidung: NICHT HANDELN"))
        assertTrue(copy.body.contains("Marktdaten sind unvollständig"))
    }

    private fun signalEvent(
        previous: AdvisorSignal,
        current: AdvisorSignal,
        reason: String
    ) = AdvisorNotificationEvent(
        id = "asset|${previous.name}|${current.name}|2026-09-14",
        instrumentId = "asset",
        previousSignal = previous,
        newSignal = current,
        analysisDay = "2026-09-14",
        reasons = listOf(reason),
        kind = AdvisorNotificationEventKind.SIGNAL_CHANGE
    )
}
