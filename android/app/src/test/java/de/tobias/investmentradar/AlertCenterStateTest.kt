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

    @Test fun markAllReadPreservesAlertsAndMarksEveryItem() {
        val a = StoredAlert(SignalAlert("a", "msft", "BUY", "A", "", "2026-09-02T08:00:00Z"), false)
        val b = StoredAlert(SignalAlert("b", "googl", "REVIEW", "B", "", "2026-09-02T09:00:00Z"), true)
        val next = AlertCenterState.markAllRead(listOf(a, b))
        assertEquals(2, next.size)
        assertTrue(next.all { it.isRead })
    }

    @Test fun deleteRemovesAlertAndCreatesTombstone() {
        val alert = StoredAlert(SignalAlert("a", "msft", "BUY", "A", "", "2026-09-02T08:00:00Z"), false)
        val next = AlertCenterState.delete(listOf(alert), emptyMap(), "a", 1234L)
        assertTrue(next.items.isEmpty())
        assertEquals(1234L, next.tombstones["a"])
    }

    @Test fun clearRemovesAllAlertsAndTombstonesEveryId() {
        val a = StoredAlert(SignalAlert("a", "msft", "BUY", "A", "", "2026-09-02T08:00:00Z"), false)
        val b = StoredAlert(SignalAlert("b", "googl", "SELL", "B", "", "2026-09-02T09:00:00Z"), false)
        val next = AlertCenterState.clear(listOf(a, b), mapOf("old" to 10L), 2222L)
        assertTrue(next.items.isEmpty())
        assertEquals(2222L, next.tombstones["a"])
        assertEquals(2222L, next.tombstones["b"])
        assertEquals(10L, next.tombstones["old"])
    }
}
