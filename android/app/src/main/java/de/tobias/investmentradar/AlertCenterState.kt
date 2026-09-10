package de.tobias.investmentradar

data class StoredAlert(
    val alert: SignalAlert,
    val isRead: Boolean = false,
    val isConfirmed: Boolean = false
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
            merged[alert.id] = StoredAlert(
                alert = alert,
                isRead = existing?.isRead ?: false,
                isConfirmed = existing?.isConfirmed ?: false
            )
        }

        return prioritize(merged.values.toList(), emptySet())
    }

    fun reconcileCurrentAnalysis(
        items: List<StoredAlert>,
        analysisById: Map<String, InvestmentItem>
    ): List<StoredAlert> {
        if (analysisById.isEmpty()) return items
        return items.map { stored ->
            if (stored.isConfirmed || !stored.alert.level.equals("BUY", ignoreCase = true)) return@map stored
            val current = analysisById[stored.alert.itemId] ?: return@map stored
            val forecastBlocked = current.forecast?.quality.equals("NICHT_BELASTBAR", ignoreCase = true)
            val qualityBlocked = currentBuyQualityBlocked(current)
            val buyStillValid = current.recommendation.equals("BUY", ignoreCase = true) &&
                !forecastBlocked &&
                !qualityBlocked &&
                !current.portfolioOnly
            if (buyStillValid) stored else stored.copy(
                alert = stored.alert.copy(
                    level = "REVIEW",
                    message = listOf(
                        stored.alert.message.takeIf { it.isNotBlank() },
                        "Die aktuelle Datenbasis bestätigt diesen früheren Kaufalarm nicht mehr. Daten und Analyse erneut prüfen."
                    ).filterNotNull().joinToString(" ")
                )
            )
        }
    }

    private fun currentBuyQualityBlocked(current: InvestmentItem): Boolean {
        val quality = current.dataQuality ?: return false
        val isEtf = current.type.equals("ETF", ignoreCase = true)
        val missing = quality.missingBlocks.map { it.lowercase() }.toSet()
        val criticalMissing = "quote" in missing ||
            "history" in missing ||
            "forecast" in missing ||
            (!isEtf && "fundamentals" in missing)

        return (quality.quoteCoverage ?: 0) < 100 ||
            (quality.historyCoverage ?: 0) < 70 ||
            (!isEtf && (quality.fundamentalCoverage ?: 0) < 60) ||
            (quality.forecastInputCoverage ?: 0) < 70 ||
            (quality.overallCoverage ?: 0) < 70 ||
            quality.criticalConflicts.isNotEmpty() ||
            criticalMissing
    }

    fun visible(
        items: List<StoredAlert>,
        filter: AlertFilter,
        holdingIds: Set<String>,
        portfolioOnly: Boolean,
        includeConfirmed: Boolean = false
    ): List<StoredAlert> = prioritize(items, holdingIds)
        .filter { includeConfirmed || !it.isConfirmed }
        .filter { filter.matches(it.alert.level) }
        .filter { !portfolioOnly || it.alert.itemId in holdingIds }

    fun prioritize(items: List<StoredAlert>, holdingIds: Set<String>): List<StoredAlert> =
        items.sortedWith(
            compareBy<StoredAlert> { it.isConfirmed }
                .thenByDescending { priorityScore(it.alert, holdingIds) }
                .thenByDescending { it.alert.createdAt }
                .thenBy { it.alert.id }
        )

    private fun priorityScore(alert: SignalAlert, holdingIds: Set<String>): Int {
        val severity = when (alert.level.trim().uppercase()) {
            "SELL" -> 400
            "THRESHOLD" -> 300
            "REVIEW" -> 200
            "BUY" -> 100
            else -> 0
        }
        val portfolioBoost = if (alert.itemId.isNotBlank() && alert.itemId in holdingIds) 1_000 else 0
        return portfolioBoost + severity
    }

    fun markAllRead(items: List<StoredAlert>): List<StoredAlert> =
        items.map { stored -> if (stored.isRead) stored else stored.copy(isRead = true) }

    fun confirm(items: List<StoredAlert>, alertId: String): List<StoredAlert> {
        if (alertId.isBlank()) return items
        return items.map { stored ->
            if (stored.alert.id == alertId) stored.copy(isRead = true, isConfirmed = true) else stored
        }
    }

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
