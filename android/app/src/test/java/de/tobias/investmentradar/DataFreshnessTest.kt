package de.tobias.investmentradar

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataFreshnessTest {
    private fun freshnessItem(
        coverage: Int? = 80,
        type: String = "Aktie",
        analysisAsOf: String? = "2026-09-02T09:30:00Z",
        momentumAsOf: String? = "2026-09-02T08:00:00Z",
        momentumStale: Boolean = false,
        fundamentalsAsOf: String? = "2026-09-01T18:00:00Z",
        fundamentalsStale: Boolean = false
    ): InvestmentItem = testInvestmentItem(id = "x", type = type, coverage = coverage).copy(
        analysisAsOf = analysisAsOf,
        dataSource = "Twelve Data",
        momentum = MomentumSnapshot(
            m6 = 5.0,
            source = "Twelve Data",
            asOf = momentumAsOf,
            stale = momentumStale
        ),
        fundamentals = FundamentalSnapshot(
            pe = 20.0,
            source = "Twelve Data",
            asOf = fundamentalsAsOf,
            stale = fundamentalsStale
        )
    )

    @Test
    fun freshStockWithGoodCoverageIsCurrent() {
        val now = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()
        assertEquals(FreshnessStatus.CURRENT, DataFreshness.summarize(freshnessItem(), now).status)
    }

    @Test
    fun lowCoverageBeatsCached() {
        val now = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()
        val item = freshnessItem(coverage = 60, momentumAsOf = "2026-09-02T00:00:00Z", momentumStale = true)
        assertEquals(FreshnessStatus.PARTIAL, DataFreshness.summarize(item, now).status)
    }

    @Test
    fun olderThanSevenDaysIsStaleWithHighestPrecedence() {
        val now = Instant.parse("2026-09-10T10:00:00Z").toEpochMilli()
        val item = freshnessItem(coverage = 40, momentumAsOf = "2026-09-01T10:00:00Z", momentumStale = true)
        assertEquals(FreshnessStatus.STALE, DataFreshness.summarize(item, now).status)
    }

    @Test
    fun staleButUsableHistoryIsCachedWhenCoverageIsGood() {
        val now = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()
        val item = freshnessItem(momentumAsOf = "2026-09-02T00:00:00Z", momentumStale = true)
        assertEquals(FreshnessStatus.CACHED, DataFreshness.summarize(item, now).status)
    }

    @Test
    fun etfWithoutFundamentalsCanStillBeCurrent() {
        val now = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()
        val item = freshnessItem(type = "ETF").copy(fundamentals = null)
        assertEquals(FreshnessStatus.CURRENT, DataFreshness.summarize(item, now).status)
    }

    @Test
    fun etfWithEmptyFundamentalPlaceholderCanStillBeCurrent() {
        val now = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()
        val item = freshnessItem(type = "ETF").copy(
            fundamentals = FundamentalSnapshot(source = "ETF-Konfiguration", asOf = null)
        )
        assertEquals(FreshnessStatus.CURRENT, DataFreshness.summarize(item, now).status)
    }

    @Test
    fun partialSummaryExplainsExactlyWhichDataIsMissing() {
        val now = Instant.parse("2026-09-02T10:00:00Z").toEpochMilli()
        val item = freshnessItem(coverage = 45).copy(
            price = null,
            priceEur = null,
            dataSource = "",
            dataError = "quote unavailable",
            momentum = MomentumSnapshot(source = "", asOf = null, error = "history unavailable"),
            fundamentals = FundamentalSnapshot(source = "", asOf = null, error = "fundamentals unavailable"),
            analysisAsOf = null
        )
        val summary = DataFreshness.summarize(item, now)
        assertEquals(FreshnessStatus.PARTIAL, summary.status)
        assertTrue("Kurs fehlt" in summary.issues)
        assertTrue("Historie fehlt" in summary.issues)
        assertTrue("Fundamentaldaten fehlen" in summary.issues)
        assertTrue("Analyse fehlt" in summary.issues)
    }
}
