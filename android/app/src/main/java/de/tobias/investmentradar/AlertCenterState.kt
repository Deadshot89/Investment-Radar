package de.tobias.investmentradar

data class StoredAlert(
    val alert: SignalAlert,
    val isRead: Boolean = false
)

data class AlertCenterSnapshot(
    val items: List<StoredAlert>,
    val tombstones: Map<String, Long>
)

enum class AlertFilter(val label: String) {
    ALL("Alle"), BUY("Kauf"), REVIEW("Prüfen"), SELL("Verkauf");

    fun matches(level: String): Boolean = when (this) {
        ALL -> true
        REVIEW -> level.equals("REVIEW", true) || level.equals("THRESHOLD", true)
        BUY -> level.equals("BUY", true)
        SELL -> level.equals("SELL", true)
    }
}

object AlertCenterState {
    private const val TOMBSTONE_TTL_MS = 7L * 24L * 60L * 60L * 1000L

    fun merge(
        local: List<StoredAlert>,
        remote: List<SignalAlert>,
        tombstones: Map<String, Long>,
        nowEpochMs: Long
    ): List<StoredAlert> {
        val activeTombstones = activeTombstones(tombstones, nowEpochMs)
        val localById = local.associateBy { it.alert.id }
        val merged = LinkedHashMap<String, StoredAlert>()

        local.forEach { stored ->
            if (stored.alert.id.isNotBlank() && stored.alert.id !in activeTombstones) {
                merged[stored.alert.id] = stored
            }
        }
        remote.forEach { alert ->
            if (alert.id.isBlank() || alert.id in activeTombstones) return@forEach
            val existing = localById[alert.id]
            merged[alert.id] = StoredAlert(alert = alert, isRead = existing?.isRead ?: false)
        }

        return merged.values.sortedByDescending { it.alert.createdAt }
    }

    fun markAllRead(items: List<StoredAlert>): List<StoredAlert> =
        items.map { stored -> if (stored.isRead) stored else stored.copy(isRead = true) }

    fun delete(
        items: List<StoredAlert>,
        tombstones: Map<String, Long>,
        alertId: String,
        nowEpochMs: Long
    ): AlertCenterSnapshot {
        if (alertId.isBlank()) return AlertCenterSnapshot(items, activeTombstones(tombstones, nowEpochMs))
        return AlertCenterSnapshot(
            items = items.filterNot { it.alert.id == alertId },
            tombstones = activeTombstones(tombstones, nowEpochMs) + (alertId to nowEpochMs)
        )
    }

    fun clear(
        items: List<StoredAlert>,
        tombstones: Map<String, Long>,
        nowEpochMs: Long
    ): AlertCenterSnapshot {
        val nextTombstones = activeTombstones(tombstones, nowEpochMs).toMutableMap()
        items.map { it.alert.id }.filter { it.isNotBlank() }.forEach { id -> nextTombstones[id] = nowEpochMs }
        return AlertCenterSnapshot(emptyList(), nextTombstones)
    }

    fun activeTombstones(tombstones: Map<String, Long>, nowEpochMs: Long): Map<String, Long> =
        tombstones.filterValues { deletedAt ->
            deletedAt > 0L && nowEpochMs - deletedAt in 0 until TOMBSTONE_TTL_MS
        }
}
