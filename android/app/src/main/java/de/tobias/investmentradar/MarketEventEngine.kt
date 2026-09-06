package de.tobias.investmentradar

import kotlin.math.max
import kotlin.math.roundToInt

object MarketEventEngine {
    private val criticalNegativeTypes = setOf(
        MarketEventType.PROFIT_WARNING,
        MarketEventType.REGULATORY_OR_LEGAL,
        MarketEventType.CREDIT_RATING,
        MarketEventType.GUIDANCE
    )

    fun normalize(raw: List<MarketEvent>): List<MarketEvent> = raw
        .asSequence()
        .map { event ->
            event.copy(
                eventId = event.eventId.trim(),
                instrumentId = event.instrumentId.trim().lowercase(),
                title = event.title.trim(),
                summary = event.summary.trim(),
                eventAt = event.eventAt.trim(),
                publishedAt = event.publishedAt.trim(),
                sourceName = event.sourceName.trim(),
                sourceUrl = event.sourceUrl.trim(),
                materiality = event.materiality.coerceIn(0, 100),
                confidencePct = event.confidencePct.coerceIn(0, 100),
                fingerprint = event.fingerprint.trim()
            )
        }
        .filter(::isValid)
        .distinctBy { it.fingerprint }
        .toList()

    fun impacts(events: List<MarketEvent>): List<MarketEventImpact> = normalize(events)
        .asSequence()
        .filter { it.verification != MarketEventVerification.UNVERIFIED }
        .map { event ->
            val adjustment = scoreAdjustment(event)
            val critical = event.direction == MarketEventDirection.NEGATIVE &&
                event.materiality >= 85 &&
                event.confidencePct >= 80 &&
                event.type in criticalNegativeTypes

            MarketEventImpact(
                event = event,
                scoreAdjustment = adjustment,
                criticalThesisBreak = critical,
                reasons = listOf("Verifiziertes Ereignis: ${event.title}")
            )
        }
        .toList()

    private fun isValid(event: MarketEvent): Boolean =
        event.eventId.isNotBlank() &&
            event.instrumentId.isNotBlank() &&
            event.title.isNotBlank() &&
            event.eventAt.isNotBlank() &&
            event.publishedAt.isNotBlank() &&
            event.sourceName.isNotBlank() &&
            event.sourceUrl.isNotBlank() &&
            event.fingerprint.isNotBlank()

    private fun scoreAdjustment(event: MarketEvent): Int {
        if (event.direction == MarketEventDirection.NEUTRAL ||
            event.direction == MarketEventDirection.MIXED ||
            event.materiality == 0 ||
            event.confidencePct == 0
        ) {
            return 0
        }

        val baseMagnitude = when {
            event.materiality >= 85 -> 12
            event.materiality >= 65 -> 8
            event.materiality >= 40 -> 4
            else -> 2
        }
        val confidenceScaled = max(
            1,
            (baseMagnitude * (event.confidencePct / 100.0)).roundToInt()
        ).coerceAtMost(15)

        return when (event.direction) {
            MarketEventDirection.POSITIVE -> confidenceScaled
            MarketEventDirection.NEGATIVE -> -confidenceScaled
            MarketEventDirection.MIXED,
            MarketEventDirection.NEUTRAL -> 0
        }
    }
}
