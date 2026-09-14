package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorNotificationCopyTest {
    @Test
    fun `hold signal is the explicit primary action in title and body`() {
        val event = AdvisorNotificationEvent(
            id = "samsung|NACHKAUFEN|HALTEN|2026-09-14",
            instrumentId = "samsung",
            previousSignal = AdvisorSignal.NACHKAUFEN,
            newSignal = AdvisorSignal.HALTEN,
            analysisDay = "2026-09-14",
            reasons = listOf("Tagesverlust allein bricht die Investmentthese nicht"),
            kind = AdvisorNotificationEventKind.SIGNAL_CHANGE
        )

        val copy = AdvisorNotificationCopy.format(
            event = event,
            displayName = "Samsung Electronics GDR"
        )

        assertEquals("🟡 HALTEN – Samsung Electronics GDR", copy.title)
        assertTrue(copy.body.startsWith("Entscheidung: HALTEN"))
        assertTrue(copy.body.contains("Tagesverlust allein bricht die Investmentthese nicht"))
    }
}
