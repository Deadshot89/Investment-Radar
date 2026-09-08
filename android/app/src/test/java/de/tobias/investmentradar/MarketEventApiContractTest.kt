package de.tobias.investmentradar

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketEventApiContractTest {
    @Test
    fun validEventResponseParsesAllTrustedFields() {
        val response = MarketEventsJsonParser.parse(
            JSONObject(
                """
                {
                  "generatedAt": "2026-09-06T17:10:00.000Z",
                  "items": [
                    {
                      "eventId": "issuer-feed:release-123",
                      "instrumentId": "msft",
                      "type": "GUIDANCE",
                      "title": "Guidance updated",
                      "summary": "Issuer updated its guidance.",
                      "eventAt": "2026-09-05T12:00:00.000Z",
                      "publishedAt": "2026-09-05T12:05:00.000Z",
                      "sourceName": "Microsoft Investor Relations",
                      "sourceUrl": "https://www.microsoft.com/en-us/Investor/release-123",
                      "verification": "VERIFIED_PRIMARY",
                      "direction": "POSITIVE",
                      "horizon": "MEDIUM",
                      "materiality": 82,
                      "confidencePct": 96,
                      "fingerprint": "msft|guidance|issuer-feed|release-123"
                    }
                  ],
                  "errors": []
                }
                """.trimIndent()
            )
        )

        assertEquals("2026-09-06T17:10:00.000Z", response.generatedAt)
        assertEquals(1, response.items.size)
        val event = response.items.single()
        assertEquals("issuer-feed:release-123", event.eventId)
        assertEquals("msft", event.instrumentId)
        assertEquals(MarketEventType.GUIDANCE, event.type)
        assertEquals(MarketEventVerification.VERIFIED_PRIMARY, event.verification)
        assertEquals(MarketEventDirection.POSITIVE, event.direction)
        assertEquals(MarketEventHorizon.MEDIUM, event.horizon)
        assertEquals(82, event.materiality)
        assertEquals(96, event.confidencePct)
        assertTrue(response.errors.isEmpty())
    }

    @Test
    fun malformedOrUnknownEventEntriesAreSkippedInsteadOfInvented() {
        val response = MarketEventsJsonParser.parse(
            JSONObject(
                """
                {
                  "generatedAt": "2026-09-06T17:10:00.000Z",
                  "items": [
                    {
                      "eventId": "bad-1",
                      "instrumentId": "msft",
                      "type": "UNKNOWN_TYPE",
                      "title": "Unknown",
                      "eventAt": "2026-09-05T12:00:00.000Z",
                      "publishedAt": "2026-09-05T12:05:00.000Z",
                      "sourceName": "Unknown",
                      "sourceUrl": "https://example.com/unknown",
                      "verification": "VERIFIED_PRIMARY",
                      "direction": "POSITIVE",
                      "horizon": "MEDIUM",
                      "materiality": 99,
                      "confidencePct": 99,
                      "fingerprint": "bad"
                    },
                    {
                      "eventId": "bad-2",
                      "instrumentId": "",
                      "type": "GUIDANCE",
                      "title": "Missing instrument",
                      "eventAt": "2026-09-05T12:00:00.000Z",
                      "publishedAt": "2026-09-05T12:05:00.000Z",
                      "sourceName": "Issuer",
                      "sourceUrl": "https://example.com/missing",
                      "verification": "VERIFIED_PRIMARY",
                      "direction": "NEGATIVE",
                      "horizon": "MEDIUM",
                      "materiality": 90,
                      "confidencePct": 90,
                      "fingerprint": "missing"
                    }
                  ],
                  "errors": []
                }
                """.trimIndent()
            )
        )

        assertTrue(response.items.isEmpty())
    }

    @Test
    fun providerErrorsRemainExplicit() {
        val response = MarketEventsJsonParser.parse(
            JSONObject(
                """
                {
                  "generatedAt": "2026-09-06T17:10:00.000Z",
                  "items": [],
                  "errors": [
                    {
                      "providerId": "wire",
                      "code": "PROVIDER_FAILED",
                      "message": "wire timeout"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(1, response.errors.size)
        assertEquals("wire", response.errors.single().providerId)
        assertEquals("PROVIDER_FAILED", response.errors.single().code)
        assertEquals("wire timeout", response.errors.single().message)
    }
}
