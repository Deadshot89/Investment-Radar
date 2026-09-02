package de.tobias.investmentradar

data class StoredAlert(
    val alert: SignalAlert,
    val isRead: Boolean = false
)

object AlertCenterState {
    private const val TOMBSTONE_TTL_MS = 7L * 24L * 60L * 60L * 1000L

    fun merge(
        local: List<StoredAlert>,
        remote: List<SignalAlert>,
        tombstones: Map<String, Long>,
        nowEpochMs: Long
    ): List<StoredAlert> {
        val activeTombstones = tombstones.filterValues { deletedAt ->
            deletedAt > 0L && nowEpochMs - deletedAt in 0 until TOMBSTONE_TTL_MS
        }
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
}
