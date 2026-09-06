package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioAdvisorCandidateFactoryTest {
    @Test
    fun strongExistingHoldingMapsToNachkaufen() {
        val candidate = PortfolioAdvisorCandidateFactory.create(
            item = advisorReadyItem("meta", quality = 88, valuation = 82, growth = 86, momentum = 80, riskScore = 78),
            isHolding = true,
            currentValueEur = 1200.0,
            monthlySavingsEur = 20,
            freshness = currentFreshness(90)
        )

        assertTrue(candidate.isHolding)
        assertEquals(PortfolioAdvisorAction.NACHKAUFEN, candidate.action)
        assertEquals(1200.0, candidate.currentValueEur!!, 0.0001)
        assertEquals(20, candidate.monthlySavingsEur)
        assertTrue(candidate.advisor.reliable)
    }

    @Test
    fun weakExistingHoldingMapsToReduceOrSellInsteadOfNewCandidateAction() {
        val candidate = PortfolioAdvisorCandidateFactory.create(
            item = advisorReadyItem("weak", quality = 42, valuation = 38, growth = 35, momentum = 34, riskScore = 38, m6 = -8.0, m12 = -12.0),
            isHolding = true,
            currentValueEur = 150.0,
            monthlySavingsEur = 0,
            freshness = currentFreshness(88)
        )

        assertTrue(candidate.action == PortfolioAdvisorAction.REDUZIEREN || candidate.action == PortfolioAdvisorAction.VERKAUFEN)
        assertTrue(candidate.isHolding)
    }

    @Test
    fun strongNonHoldingMapsToNeuAufnehmen() {
        val candidate = PortfolioAdvisorCandidateFactory.create(
            item = advisorReadyItem("new", quality = 90, valuation = 84, growth = 88, momentum = 82, riskScore = 80),
            isHolding = false,
            currentValueEur = null,
            monthlySavingsEur = 0,
            freshness = currentFreshness(92)
        )

        assertFalse(candidate.isHolding)
        assertEquals(PortfolioAdvisorAction.NEU_AUFNEHMEN, candidate.action)
    }

    @Test
    fun ordinaryReliableNonHoldingMapsToNichtAufnehmen() {
        val candidate = PortfolioAdvisorCandidateFactory.create(
            item = advisorReadyItem("watch", quality = 67, valuation = 58, growth = 61, momentum = 55, riskScore = 64, m6 = 4.0, m12 = 6.0),
            isHolding = false,
            currentValueEur = null,
            monthlySavingsEur = 0,
            freshness = currentFreshness(85)
        )

        assertEquals(PortfolioAdvisorAction.NICHT_AUFNEHMEN, candidate.action)
        assertTrue(candidate.advisor.reliable)
    }

    @Test
    fun unreliableDataNeverCreatesHoldingOrNewBuyAction() {
        val holding = PortfolioAdvisorCandidateFactory.create(
            item = advisorReadyItem("meta", quality = 90, valuation = 90, growth = 90, momentum = 90, riskScore = 90),
            isHolding = true,
            currentValueEur = 1000.0,
            monthlySavingsEur = 20,
            freshness = DataFreshnessSummary(FreshnessStatus.STALE, "Veraltet", null, "test", "test", "test", 90)
        )
        val newCandidate = PortfolioAdvisorCandidateFactory.create(
            item = advisorReadyItem("new", quality = 90, valuation = 90, growth = 90, momentum = 90, riskScore = 90),
            isHolding = false,
            currentValueEur = null,
            monthlySavingsEur = 0,
            freshness = DataFreshnessSummary(FreshnessStatus.STALE, "Veraltet", null, "test", "test", "test", 90)
        )

        assertEquals(PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG, holding.action)
        assertEquals(PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG, newCandidate.action)
    }

    private fun advisorReadyItem(
        id: String,
        quality: Int,
        valuation: Int,
        growth: Int,
        momentum: Int,
        riskScore: Int,
        m6: Double = 14.0,
        m12: Double = 20.0
    ) = testInvestmentItem(id = id, type = "Aktie", coverage = 90, risk = 2, priceEur = 100.0).copy(
        scoreQuality = quality,
        scoreValuation = valuation,
        scoreGrowth = growth,
        scoreMomentum = momentum,
        scoreRisk = riskScore,
        momentum = MomentumSnapshot(
            m1 = 3.0,
            m3 = 7.0,
            m6 = m6,
            m12 = m12,
            score = momentum,
            source = "test",
            asOf = "2026-09-06T08:00:00Z"
        ),
        fundamentals = FundamentalSnapshot(
            pe = 20.0,
            revenueGrowth = 0.10,
            epsGrowth = 0.12,
            source = "test",
            asOf = "2026-09-06T08:00:00Z"
        )
    )

    private fun currentFreshness(coverage: Int) = DataFreshnessSummary(
        status = FreshnessStatus.CURRENT,
        label = "Aktuell",
        analysisAsOf = "2026-09-06T08:00:00Z",
        quoteSource = "test",
        historySource = "test",
        fundamentalSource = "test",
        coverage = coverage
    )
}
