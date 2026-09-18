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
    fun `new candidate allocation is labeled buy candidate with concrete amount`() {
        val event = AdvisorNotificationEvent(
            id = "allocation|meta|60|NEU_AUFNEHMEN|2026-09-18",
            instrumentId = "meta",
            previousSignal = null,
            newSignal = AdvisorSignal.NACHKAUFEN,
            analysisDay = "2026-09-18",
            reasons = listOf("Beste aktuelle Chance"),
            kind = AdvisorNotificationEventKind.PURCHASE_ALLOCATION,
            amountEur = 60,
            portfolioAction = PortfolioAdvisorAction.NEU_AUFNEHMEN
        )

        val copy = AdvisorNotificationCopy.format(event, "Beispiel Aktie")

        assertEquals("🟢 KAUFEN · 60 € – Beispiel Aktie", copy.title)
        assertTrue(copy.body.startsWith("Entscheidung: KAUFEN · 60 €"))
    }

    @Test
    fun `existing holding allocation is labeled buy more with concrete amount`() {
        val event = AdvisorNotificationEvent(
            id = "allocation|meta|40|NACHKAUFEN|2026-09-18",
            instrumentId = "meta",
            previousSignal = AdvisorSignal.HALTEN,
            newSignal = AdvisorSignal.NACHKAUFEN,
            analysisDay = "2026-09-18",
            reasons = listOf("Bestehende Position"),
            kind = AdvisorNotificationEventKind.PURCHASE_ALLOCATION,
            amountEur = 40,
            portfolioAction = PortfolioAdvisorAction.NACHKAUFEN
        )

        val copy = AdvisorNotificationCopy.format(event, "Beispiel Aktie")

        assertEquals("🟢 NACHKAUFEN · 40 € – Beispiel Aktie", copy.title)
        assertTrue(copy.body.startsWith("Entscheidung: NACHKAUFEN · 40 €"))
    }

    @Test
    fun `reduce signal is presented as a clear partial sell`() {
        val copy = AdvisorNotificationCopy.format(
            event = signalEvent(
                previous = AdvisorSignal.HALTEN,
                current = AdvisorSignal.REDUZIEREN,
                reason = "Positionsrisiko ist gestiegen"
            ),
            displayName = "Beispiel Aktie"
        )

        assertEquals("🔴 VERKAUFEN – Beispiel Aktie", copy.title)
        assertTrue(copy.body.startsWith("Entscheidung: VERKAUFEN"))
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
    fun `unreliable analysis keeps the primary action at hold and adds a data warning`() {
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

        assertEquals("🟡 HALTEN – Beispiel Aktie", copy.title)
        assertTrue(copy.body.startsWith("Entscheidung: HALTEN"))
        assertTrue(copy.body.contains("Datenwarnung:"))
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
