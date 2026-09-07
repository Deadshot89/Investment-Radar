package de.tobias.investmentradar

import kotlin.math.roundToInt

object EventAwareAdvisorPolicy {
    private const val MAX_TOTAL_EVENT_ADJUSTMENT = 15
    private const val EVENT_POLICY_WEIGHT = 1.25

    fun context(events: List<MarketEvent>): AdvisorEventContext {
        val impacts = MarketEventEngine.impacts(events)
        return AdvisorEventContext(
            impacts = impacts,
            fingerprint = impacts
                .map { it.event.fingerprint }
                .filter(String::isNotBlank)
                .sorted()
                .joinToString("|"),
            criticalThesisBreak = impacts.any { it.criticalThesisBreak }
        )
    }

    fun apply(base: AdvisorResult, context: AdvisorEventContext): AdvisorResult {
        val baseScore = base.score ?: return base
        if (!base.reliable || context.impacts.isEmpty()) return base

        val weightedAdjustment = context.impacts
            .sumOf { impact -> (impact.scoreAdjustment * EVENT_POLICY_WEIGHT).roundToInt() }
            .coerceIn(-MAX_TOTAL_EVENT_ADJUSTMENT, MAX_TOTAL_EVENT_ADJUSTMENT)
        if (weightedAdjustment == 0) return base

        val adjustedScore = (baseScore + weightedAdjustment).coerceIn(0, 100)
        val eventReasons = context.impacts
            .flatMap { it.reasons }
            .filter(String::isNotBlank)
        val eventRisks = context.impacts
            .filter { it.scoreAdjustment < 0 }
            .map { "Verifiziertes Ereignis belastet die These: ${it.event.title}" }

        return base.copy(
            score = adjustedScore,
            signal = signalForScore(adjustedScore),
            reasons = (base.reasons + eventReasons).distinct().take(6),
            risks = (base.risks + eventRisks).distinct().take(5)
        )
    }

    private fun signalForScore(score: Int): AdvisorSignal = when {
        score >= 72 -> AdvisorSignal.NACHKAUFEN
        score >= 52 -> AdvisorSignal.HALTEN
        score >= 35 -> AdvisorSignal.REDUZIEREN
        else -> AdvisorSignal.VERKAUFEN
    }
}
