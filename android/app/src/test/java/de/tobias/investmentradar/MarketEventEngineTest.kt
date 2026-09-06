package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketEventEngineTest {
    @Test
    fun duplicateSourceRecordsCollapseToOneStableEvent() {
        val first = event(
            eventId = "issuer-123",
            fingerprint = "msft|guidance|issuer-123"
        )
        val duplicate = first.copy(
            title = "  Guidance updated  ",
            summary = "Same issuer release, normalized by stable source identity"
        )

        val normalized = MarketEventEngine.normalize(listOf(first, duplicate))

        assertEquals(1, normalized.size)
        assertEquals("msft|guidance|issuer-123", normalized.single().fingerprint)
    }

    @Test
    fun blankInstrumentOrSourceIdentityIsRejected() {
        val missingInstrument = event(instrumentId = "")
        val missingSourceUrl = event(sourceUrl = "")
        val missingFingerprint = event(fingerprint = "")

        val normalized = MarketEventEngine.normalize(
            listOf(missingInstrument, missingSourceUrl, missingFingerprint)
        )

        assertTrue(normalized.isEmpty())
    }

    @Test
    fun unverifiedProfitWarningCannotChangeAdvisorImpact() {
        val impacts = MarketEventEngine.impacts(
            listOf(
                event(
                    type = MarketEventType.PROFIT_WARNING,
                    verification = MarketEventVerification.UNVERIFIED,
                    direction = MarketEventDirection.NEGATIVE,
                    materiality = 100,
                    confidencePct = 100
                )
            )
        )

        assertTrue(impacts.isEmpty())
    }

    @Test
    fun verifiedPrimaryAndSecondaryEventsKeepTheirVerificationLevel() {
        val events = MarketEventEngine.normalize(
            listOf(
                event(
                    eventId = "primary",
                    fingerprint = "msft|guidance|primary",
                    verification = MarketEventVerification.VERIFIED_PRIMARY
                ),
                event(
                    eventId = "secondary",
                    fingerprint = "msft|guidance|secondary",
                    verification = MarketEventVerification.VERIFIED_SECONDARY,
                    sourceName = "trusted-wire",
                    sourceUrl = "https://news.example.com/msft-guidance"
                )
            )
        )

        assertEquals(
            setOf(
                MarketEventVerification.VERIFIED_PRIMARY,
                MarketEventVerification.VERIFIED_SECONDARY
            ),
            events.map { it.verification }.toSet()
        )
    }

    @Test
    fun onlyHighMaterialityVerifiedNegativeThesisEventCanBeCritical() {
        val critical = MarketEventEngine.impacts(
            listOf(
                event(
                    eventId = "warning-critical",
                    fingerprint = "msft|profit_warning|critical",
                    type = MarketEventType.PROFIT_WARNING,
                    verification = MarketEventVerification.VERIFIED_PRIMARY,
                    direction = MarketEventDirection.NEGATIVE,
                    materiality = 90,
                    confidencePct = 90
                )
            )
        ).single()
        val lowMateriality = MarketEventEngine.impacts(
            listOf(
                event(
                    eventId = "warning-small",
                    fingerprint = "msft|profit_warning|small",
                    type = MarketEventType.PROFIT_WARNING,
                    verification = MarketEventVerification.VERIFIED_PRIMARY,
                    direction = MarketEventDirection.NEGATIVE,
                    materiality = 70,
                    confidencePct = 90
                )
            )
        ).single()
        val positive = MarketEventEngine.impacts(
            listOf(
                event(
                    eventId = "guidance-positive",
                    fingerprint = "msft|guidance|positive",
                    type = MarketEventType.GUIDANCE,
                    verification = MarketEventVerification.VERIFIED_PRIMARY,
                    direction = MarketEventDirection.POSITIVE,
                    materiality = 95,
                    confidencePct = 95
                )
            )
        ).single()

        assertTrue(critical.criticalThesisBreak)
        assertTrue(critical.scoreAdjustment < 0)
        assertFalse(lowMateriality.criticalThesisBreak)
        assertFalse(positive.criticalThesisBreak)
        assertTrue(positive.scoreAdjustment > 0)
    }

    @Test
    fun normalizationClampsMaterialityAndConfidenceWithoutInventingVerification() {
        val normalized = MarketEventEngine.normalize(
            listOf(
                event(
                    materiality = 150,
                    confidencePct = -20,
                    verification = MarketEventVerification.UNVERIFIED
                )
            )
        ).single()

        assertEquals(100, normalized.materiality)
        assertEquals(0, normalized.confidencePct)
        assertEquals(MarketEventVerification.UNVERIFIED, normalized.verification)
    }

    private fun event(
        eventId: String = "issuer-123",
        instrumentId: String = "msft",
        type: MarketEventType = MarketEventType.GUIDANCE,
        title: String = "Guidance updated",
        summary: String = "Issuer updated its guidance",
        eventAt: String = "2026-09-05T12:00:00Z",
        publishedAt: String = "2026-09-05T12:05:00Z",
        sourceName: String = "issuer",
        sourceUrl: String = "https://issuer.example.com/release/123",
        verification: MarketEventVerification = MarketEventVerification.VERIFIED_PRIMARY,
        direction: MarketEventDirection = MarketEventDirection.NEGATIVE,
        horizon: MarketEventHorizon = MarketEventHorizon.MEDIUM,
        materiality: Int = 90,
        confidencePct: Int = 90,
        fingerprint: String = "msft|guidance|issuer-123"
    ): MarketEvent = MarketEvent(
        eventId = eventId,
        instrumentId = instrumentId,
        type = type,
        title = title,
        summary = summary,
        eventAt = eventAt,
        publishedAt = publishedAt,
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        verification = verification,
        direction = direction,
        horizon = horizon,
        materiality = materiality,
        confidencePct = confidencePct,
        fingerprint = fingerprint
    )
}
