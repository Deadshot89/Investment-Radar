package de.tobias.investmentradar

import java.time.Instant

enum class FreshnessStatus {
    CURRENT,
    CACHED,
    PARTIAL,
    STALE
}

data class DataFreshnessSummary(
    val status: FreshnessStatus,
    val label: String,
    val analysisAsOf: String?,
    val quoteSource: String?,
    val historySource: String?,
    val fundamentalSource: String?,
    val coverage: Int?
)

object DataFreshness {
    private const val HISTORY_FRESH_MS = 6L * 60 * 60 * 1000
    private const val FUNDAMENTAL_FRESH_MS = 24L * 60 * 60 * 1000
    private const val MAX_CACHE_MS = 7L * 24 * 60 * 60 * 1000

    fun summarize(
        item: InvestmentItem,
        nowEpochMs: Long = System.currentTimeMillis()
    ): DataFreshnessSummary {
        val momentum = item.momentum
        val fundamentals = item.fundamentals
        val historyExpected = momentum != null
        val fundamentalsExpected = !item.type.equals("ETF", ignoreCase = true) || fundamentals.hasUsableContent()

        val historyTimestamp = momentum?.asOf.toEpochMsOrNull()
        val fundamentalTimestamp = fundamentals?.asOf.toEpochMsOrNull()
        val historyAge = historyTimestamp?.let { ageMs(nowEpochMs, it) }
        val fundamentalAge = fundamentalTimestamp?.let { ageMs(nowEpochMs, it) }

        val stale =
            (historyExpected && historyAge != null && historyAge > MAX_CACHE_MS) ||
                (fundamentalsExpected && fundamentalAge != null && fundamentalAge > MAX_CACHE_MS)

        val partial =
            item.coverage == null || item.coverage < 70 ||
                (historyExpected && (momentum?.source.isNullOrBlank() || historyTimestamp == null)) ||
                (fundamentalsExpected && (fundamentals?.source.isNullOrBlank() || fundamentalTimestamp == null))

        val cached =
            (historyExpected && (momentum?.stale == true || (historyAge != null && historyAge > HISTORY_FRESH_MS))) ||
                (fundamentalsExpected && (fundamentals?.stale == true || (fundamentalAge != null && fundamentalAge > FUNDAMENTAL_FRESH_MS)))

        val status = when {
            stale -> FreshnessStatus.STALE
            partial -> FreshnessStatus.PARTIAL
            cached -> FreshnessStatus.CACHED
            else -> FreshnessStatus.CURRENT
        }

        return DataFreshnessSummary(
            status = status,
            label = status.displayLabel(),
            analysisAsOf = item.analysisAsOf?.takeIf { it.isNotBlank() },
            quoteSource = item.dataSource.takeIf { it.isNotBlank() },
            historySource = momentum?.source?.takeIf { it.isNotBlank() },
            fundamentalSource = fundamentals?.source?.takeIf { it.isNotBlank() },
            coverage = item.coverage
        )
    }

    private fun FundamentalSnapshot?.hasUsableContent(): Boolean {
        val snapshot = this ?: return false
        return snapshot.pe != null ||
            snapshot.priceToSales != null ||
            snapshot.evToEbitda != null ||
            snapshot.freeCashFlowYield != null ||
            snapshot.revenueGrowth != null ||
            snapshot.epsGrowth != null ||
            snapshot.operatingMargin != null ||
            snapshot.netMargin != null ||
            snapshot.roe != null ||
            snapshot.roic != null ||
            snapshot.debtToEquity != null
    }

    private fun String?.toEpochMsOrNull(): Long? =
        this?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private fun ageMs(nowEpochMs: Long, timestampEpochMs: Long): Long =
        (nowEpochMs - timestampEpochMs).coerceAtLeast(0L)

    private fun FreshnessStatus.displayLabel(): String = when (this) {
        FreshnessStatus.CURRENT -> "Aktuell"
        FreshnessStatus.CACHED -> "Aus Cache"
        FreshnessStatus.PARTIAL -> "Teilweise verfügbar"
        FreshnessStatus.STALE -> "Veraltet"
    }
}
