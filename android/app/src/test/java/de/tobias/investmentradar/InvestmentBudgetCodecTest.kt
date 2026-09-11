package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentBudgetCodecTest {
    @Test
    fun `journal entries round trip all relevant fields`() {
        val original = listOf(
            BudgetJournalEntry(
                id = "buy-msft-1",
                type = BudgetJournalType.BUY_DEBIT,
                amountEur = 19.95,
                date = "11.09.2026",
                itemId = "msft",
                source = BudgetJournalSource.RECOMMENDATION,
                note = "Kauf ausgeführt"
            )
        )
        assertEquals(original, InvestmentBudgetCodec.decodeEntries(InvestmentBudgetCodec.encodeEntries(original)))
    }

    @Test
    fun `reservations round trip`() {
        val original = listOf(BudgetReservation("rec-aapl", "aapl", 25.0, "Offene Kaufempfehlung"))
        assertEquals(original, InvestmentBudgetCodec.decodeReservations(InvestmentBudgetCodec.encodeReservations(original)))
    }

    @Test
    fun `invalid persisted records are ignored`() {
        val raw = """[{"id":"","type":"BUY_DEBIT","amountEur":20,"date":"x"},{"id":"ok","type":"EXTRA_DEPOSIT","amountEur":6.4,"date":"11.09.2026"}]"""
        val decoded = InvestmentBudgetCodec.decodeEntries(raw)
        assertEquals(1, decoded.size)
        assertEquals("ok", decoded.single().id)
    }
}
