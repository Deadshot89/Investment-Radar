package de.tobias.investmentradar

object AdvisorStabilityPolicy {
    fun resolve(
        previous: AdvisorSnapshot,
        proposed: AdvisorResult,
        analysisDay: String? = null,
        criticalEvent: Boolean = false
    ): AdvisorResult {
        val cleanProposed = proposed.copy(
            pendingWorseSignal = null,
            pendingWorseCount = 0
        )
        if (!proposed.reliable) return cleanProposed

        val current = previous.current?.result
            ?.takeIf { it.instrumentId == proposed.instrumentId && it.reliable }
            ?: return cleanProposed
        if (!isWorse(proposed.signal, current.signal)) return cleanProposed

        if (criticalEvent) return cleanProposed

        val proposedScore = proposed.score
        val currentScore = current.score
        val severeSell = proposed.signal == AdvisorSignal.VERKAUFEN && proposedScore != null && proposedScore <= 25
        val severeScoreDrop = proposedScore != null && currentScore != null && currentScore - proposedScore >= 25
        if (severeSell || severeScoreDrop) return cleanProposed

        val alreadyCountedToday = analysisDay != null && previous.current?.day == analysisDay
        val nextPendingCount = when {
            alreadyCountedToday && current.pendingWorseSignal == proposed.signal -> current.pendingWorseCount
            current.pendingWorseSignal == proposed.signal -> current.pendingWorseCount + 1
            else -> 1
        }
        if (nextPendingCount >= 2) return cleanProposed

        return current.copy(
            pendingWorseSignal = proposed.signal,
            pendingWorseCount = nextPendingCount,
            reasons = (current.reasons + "Schwächeres Signal wartet auf Bestätigung").distinct().take(5)
        )
    }

    private fun isWorse(proposed: AdvisorSignal, current: AdvisorSignal): Boolean =
        severity(proposed) > severity(current)

    private fun severity(signal: AdvisorSignal): Int = when (signal) {
        AdvisorSignal.NACHKAUFEN -> 0
        AdvisorSignal.HALTEN -> 1
        AdvisorSignal.REDUZIEREN -> 2
        AdvisorSignal.VERKAUFEN -> 3
        AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> 4
    }
}
