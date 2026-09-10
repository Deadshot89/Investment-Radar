package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun confirmedStateWinsWhenSameRemoteAlertRepeats() {
        val alert = SignalAlert("same", "msft", "SELL", "Verkauf prüfen", "Text", "2026-09-02T08:00:00Z")
        val merged = AlertCenterState.merge(
            local = listOf(StoredAlert(alert = alert, isRead = true, isConfirmed = true)),
            remote = listOf(alert),
            tombstones = emptyMap(),
            nowEpochMs = 1_788_336_100_000L
        )

        assertTrue(merged.single().isConfirmed)
    }

    @Test fun confirmMarksAlertReadAndConfirmed() {
        val alert = StoredAlert(SignalAlert("a", "msft", "REVIEW", "Prüfen", "Text", "2026-09-02T08:00:00Z"))

        val next = AlertCenterState.confirm(listOf(alert), "a")

        assertTrue(next.single().isRead)
        assertTrue(next.single().isConfirmed)
    }

    @Test fun openItemsExcludeConfirmedAlertsButHistoryCanStillShowThem() {
        val open = StoredAlert(SignalAlert("open", "msft", "SELL", "Verkauf", "", "2026-09-02T09:00:00Z"))
        val done = StoredAlert(SignalAlert("done", "msft", "REVIEW", "Prüfen", "", "2026-09-02T08:00:00Z"), isRead = true, isConfirmed = true)

        val openOnly = AlertCenterState.visible(
            items = listOf(done, open),
            filter = AlertFilter.ALL,
            holdingIds = setOf("msft"),
            portfolioOnly = false,
            includeConfirmed = false
        )
        val history = AlertCenterState.visible(
            items = listOf(done, open),
            filter = AlertFilter.ALL,
            holdingIds = setOf("msft"),
            portfolioOnly = false,
            includeConfirmed = true
        )

        assertEquals(listOf("open"), openOnly.map { it.alert.id })
        assertEquals(setOf("open", "done"), history.map { it.alert.id }.toSet())
    }

    @Test fun markAllReadPreservesConfirmationState() {
        val a = StoredAlert(SignalAlert("a", "msft", "BUY", "A", "", "2026-09-02T08:00:00Z"), false, false)
        val b = StoredAlert(SignalAlert("b", "googl", "REVIEW", "B", "", "2026-09-02T09:00:00Z"), true, true)
        val next = AlertCenterState.markAllRead(listOf(a, b))
        assertEquals(2, next.size)
        assertTrue(next.all { it.isRead })
        assertFalse(next.first { it.alert.id == "a" }.isConfirmed)
        assertTrue(next.first { it.alert.id == "b" }.isConfirmed)
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

    @Test fun actionablePortfolioAlertsArePrioritizedAheadOfGenericBuyAlerts() {
        val buy = StoredAlert(SignalAlert("buy", "new", "BUY", "Kauf", "", "2026-09-02T10:00:00Z"))
        val reviewHeld = StoredAlert(SignalAlert("review", "held", "REVIEW", "Prüfen", "", "2026-09-02T09:00:00Z"))
        val sellHeld = StoredAlert(SignalAlert("sell", "held", "SELL", "Verkaufen", "", "2026-09-02T08:00:00Z"))
        val thresholdHeld = StoredAlert(SignalAlert("drop", "held", "THRESHOLD", "Tagesverlust", "", "2026-09-02T11:00:00Z"))

        val sorted = AlertCenterState.prioritize(
            items = listOf(buy, reviewHeld, sellHeld, thresholdHeld),
            holdingIds = setOf("held")
        )

        assertEquals(listOf("sell", "drop", "review", "buy"), sorted.map { it.alert.id })
    }

    @Test fun everyPortfolioAlertStaysAheadOfOutsideSignals() {
        val outsideSell = StoredAlert(SignalAlert("outside-sell", "outside", "SELL", "Verkauf", "", "2026-09-02T12:00:00Z"))
        val heldBuy = StoredAlert(SignalAlert("held-buy", "held", "BUY", "Kauf", "", "2026-09-02T08:00:00Z"))
        val heldReview = StoredAlert(SignalAlert("held-review", "held", "REVIEW", "Prüfen", "", "2026-09-02T09:00:00Z"))

        val sorted = AlertCenterState.prioritize(
            items = listOf(outsideSell, heldBuy, heldReview),
            holdingIds = setOf("held")
        )

        assertEquals(listOf("held-review", "held-buy", "outside-sell"), sorted.map { it.alert.id })
    }

    @Test fun portfolioOnlyFilterKeepsOnlyAlertsForCurrentHoldings() {
        val heldSell = StoredAlert(SignalAlert("held-sell", "held", "SELL", "Verkaufen", "", "2026-09-02T08:00:00Z"))
        val heldReview = StoredAlert(SignalAlert("held-review", "held", "REVIEW", "Prüfen", "", "2026-09-02T09:00:00Z"))
        val outsideBuy = StoredAlert(SignalAlert("outside-buy", "outside", "BUY", "Kauf", "", "2026-09-02T10:00:00Z"))

        val visible = AlertCenterState.visible(
            items = listOf(outsideBuy, heldReview, heldSell),
            filter = AlertFilter.ALL,
            holdingIds = setOf("held"),
            portfolioOnly = true,
            includeConfirmed = false
        )

        assertEquals(listOf("held-sell", "held-review"), visible.map { it.alert.id })
    }

    @Test fun currentIncompleteAnalysisDowngradesOpenBuyButPreservesConfirmedHistory() {
        val openBuy = StoredAlert(SignalAlert("open-buy", "apple", "BUY", "Kaufchance", "", "2026-09-02T10:00:00Z"))
        val confirmedBuy = StoredAlert(SignalAlert("done-buy", "apple", "BUY", "Frühere Kaufchance", "", "2026-09-01T10:00:00Z"), isRead = true, isConfirmed = true)
        val current = testInvestmentItem(id = "apple", coverage = 85).copy(
            recommendation = "REVIEW",
            forecast = RadarForecast(quality = "NICHT_BELASTBAR"),
            dataQuality = RadarDataQuality(overallCoverage = 85, forecastInputCoverage = 30, missingBlocks = listOf("history", "forecast"))
        )

        val reconciled = AlertCenterState.reconcileCurrentAnalysis(
            listOf(openBuy, confirmedBuy),
            mapOf("apple" to current)
        )

        val open = reconciled.first { it.alert.id == "open-buy" }
        val done = reconciled.first { it.alert.id == "done-buy" }
        assertEquals("REVIEW", open.alert.level)
        assertTrue(open.alert.message.contains("aktuelle Datenbasis", ignoreCase = true))
        assertEquals("BUY", done.alert.level)
        assertTrue(done.isConfirmed)
    }

    @Test fun openBuyIsDowngradedWhenStockFundamentalsAreBelowBuyThreshold() {
        val openBuy = StoredAlert(SignalAlert("open-buy", "apple", "BUY", "Kaufchance", "", "2026-09-10T18:00:00Z"))
        val current = testInvestmentItem(id = "apple", type = "AKTIE", recommendation = "BUY", coverage = 75).copy(
            forecast = RadarForecast(quality = "MITTEL", confidencePct = 75),
            dataQuality = RadarDataQuality(
                quoteCoverage = 100,
                historyCoverage = 80,
                fundamentalCoverage = 55,
                forecastInputCoverage = 75,
                overallCoverage = 75,
                qualityTier = "GUT"
            )
        )

        val reconciled = AlertCenterState.reconcileCurrentAnalysis(listOf(openBuy), mapOf("apple" to current))

        assertEquals("REVIEW", reconciled.single().alert.level)
        assertTrue(reconciled.single().alert.message.contains("aktuelle Datenbasis", ignoreCase = true))
    }
}
