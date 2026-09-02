package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertCenterStateTest {
    @Test fun deletedRemoteAlertDoesNotImmediatelyReappear() {
        val remote = SignalAlert("same-id", "msft", "BUY", "", "", "2026-09-02T08:00:00Z")
        val merged = AlertCenterState.merge(
            local = emptyList(),
            remote = listOf(remote),
            tombstones = mapOf("same-id" to 1_788_336_000_000L),
            nowEpochMs = 1_788_336_100_000L
        )
        assertTrue(merged.isEmpty())
    }

    @Test fun localReadStateWinsWhenRemoteAlertRepeats() {
        val alert = SignalAlert("same", "msft", "REVIEW", "Prüfen", "Text", "2026-09-02T08:00:00Z")
        val merged = AlertCenterState.merge(
            local = listOf(StoredAlert(alert, true)), remote = listOf(alert), tombstones = emptyMap(), nowEpochMs = 1_788_336_100_000L
        )
        assertEquals(true, merged.single().isRead)
    }
}
