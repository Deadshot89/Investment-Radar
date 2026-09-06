package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyAnalysisCoordinatorTest {
    @Test
    fun evaluatesOnlyKnownHoldingsAndCreatesStableChangeEvents() {
        val meta = advisorReadyItem("meta", quality = 80, valuation = 72, growth = 76, momentum = 68, risk = 70)
        val msft = advisorReadyItem("msft", quality = 82, valuation = 64, growth = 74, momentum = 65, risk = 72)
        val outsider = advisorReadyItem("outside", quality = 90, valuation = 90, growth = 90, momentum = 90, risk = 90)

        val previous = mapOf(
            "meta" to AdvisorSnapshot(
                current = DatedAdvisorResult("2026-09-05", AdvisorResult("meta", AdvisorSignal.HALTEN, 60, true, listOf("alt"), emptyList())),
                lastReliable = DatedAdvisorResult("2026-09-05", AdvisorResult("meta", AdvisorSignal.HALTEN, 60, true, listOf("alt"), emptyList()))
            )
        )

        val output = DailyAnalysisCoordinator.analyze(
            analysisDay = "2026-09-06",
            holdingIds = setOf("meta", "msft"),
            items = listOf(meta, msft, outsider),
            previousSnapshots = previous,
            freshnessFor = { DataFreshnessSummary(FreshnessStatus.CURRENT, "Aktuell", null, "test", "test", "test", 85) }
        )

        assertEquals(setOf("meta", "msft"), output.results.map { it.instrumentId }.toSet())
        assertTrue(output.results.none { it.instrumentId == "outside" })
        assertEquals(output.events.distinctBy { it.id }.size, output.events.size)
    }

    @Test
    fun evaluatesUnionOfHoldingsAndExplicitRadarCandidatesButIgnoresOutsider() {
        val holding = advisorReadyItem("holding", quality = 74, valuation = 70, growth = 72, momentum = 66, risk = 70)
        val candidate = advisorReadyItem("candidate", quality = 88, valuation = 80, growth = 84, momentum = 78, risk = 76)
        val outsider = advisorReadyItem("outside", quality = 99, valuation = 99, growth = 99, momentum = 99, risk = 99)

        val output = DailyAnalysisCoordinator.analyze(
            analysisDay = "2026-09-06",
            holdingIds = setOf("holding"),
            candidateIds = setOf("candidate"),
            items = listOf(holding, candidate, outsider),
            previousSnapshots = emptyMap(),
            freshnessFor = { DataFreshnessSummary(FreshnessStatus.CURRENT, "Aktuell", null, "test", "test", "test", 90) }
        )

        assertEquals(setOf("holding", "candidate"), output.results.map { it.instrumentId }.toSet())
        assertTrue(output.results.none { it.instrumentId == "outside" })
    }

    @Test
    fun unchangedReliableSignalDoesNotCreateAnotherEvent() {
        val item = advisorReadyItem("meta", quality = 67, valuation = 58, growth = 61, momentum = 55, risk = 64)
        val forecast = ForecastEngine.forecast(item)
        val input = AdvisorInputFactory.from(
            item,
            forecast,
            DataFreshnessSummary(FreshnessStatus.CURRENT, "Aktuell", null, "test", "test", "test", 85)
        )
        val current = AdvisorEngine.evaluate(input)
        val snapshot = AdvisorSnapshot(
            current = DatedAdvisorResult("2026-09-05", current),
            lastReliable = DatedAdvisorResult("2026-09-05", current)
        )

        val output = DailyAnalysisCoordinator.analyze(
            analysisDay = "2026-09-06",
            holdingIds = setOf("meta"),
            items = listOf(item),
            previousSnapshots = mapOf("meta" to snapshot),
            freshnessFor = { DataFreshnessSummary(FreshnessStatus.CURRENT, "Aktuell", null, "test", "test", "test", 85) }
        )

        assertTrue(output.events.isEmpty())
    }

    private fun advisorReadyItem(
        id: String,
        quality: Int,
        valuation: Int,
        growth: Int,
        momentum: Int,
        risk: Int
    ) = testInvestmentItem(id = id, type = "Aktie", coverage = 85, risk = 2, priceEur = 100.0).copy(
        scoreQuality = quality,
        scoreValuation = valuation,
        scoreGrowth = growth,
        scoreMomentum = momentum,
        scoreRisk = risk,
        momentum = MomentumSnapshot(m12 = 8.0, score = momentum, source = "test", asOf = "2026-09-06T07:00:00Z"),
        fundamentals = FundamentalSnapshot(pe = 20.0, revenueGrowth = 0.1, source = "test", asOf = "2026-09-06T07:00:00Z")
    )
}
