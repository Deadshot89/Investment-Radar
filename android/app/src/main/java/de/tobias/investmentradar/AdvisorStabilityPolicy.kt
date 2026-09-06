package de.tobias.investmentradar

object AdvisorStabilityPolicy {
    fun resolve(previous: AdvisorSnapshot, proposed: AdvisorResult): AdvisorResult {
        val cleanProposed = proposed.clearPending()
        if (!proposed.reliable) return cleanProposed

        val current = previous.current?.result ?: return cleanProposed
        if (current.instrumentId != proposed.instrumentId) return cleanProposed
        if (!current.reliable) return cleanProposed
        if (!isWorse(proposed.signal, current.signal)) return cleanProposed

        val proposedScore = proposed.score
        val lastReliable = previous.lastReliable?.result
        val lastReliableScore = lastReliable?.score
        val severeSell = proposed.signal == AdvisorSignal.VERKAUFEN &&
            proposedScore != null && proposedScore <= 25
        val largeScoreDrop = proposedScore != null && lastReliableScore != null &&
            lastReliableScore - proposedScore >= 25
        if (severeSell || largeScoreDrop) return cleanProposed

        if (current.pendingWorseSignal == proposed.signal && current.pendingWorseCount >= 1) {
            return cleanProposed
        }

        val stable = lastReliable
            ?.takeIf { it.instrumentId == proposed.instrumentId && it.reliable }
            ?: current
        return stable.copy(
            pendingWorseSignal = proposed.signal,
            pendingWorseCount = if (current.pendingWorseSignal == proposed.signal) {
                current.pendingWorseCount + 1
            } else {
                1
            }
        )
    }

    private fun AdvisorResult.clearPending(): AdvisorResult = copy(
        pendingWorseSignal = null,
        pendingWorseCount = 0
    )

    private fun isWorse(proposed: AdvisorSignal, current: AdvisorSignal): Boolean {
        val proposedSeverity = severity(proposed) ?: return false
        val currentSeverity = severity(current) ?: return false
        return proposedSeverity > currentSeverity
    }

    private fun severity(signal: AdvisorSignal): Int? = when (signal) {
        AdvisorSignal.NACHKAUFEN -> 0
        AdvisorSignal.HALTEN -> 1
        AdvisorSignal.REDUZIEREN -> 2
        AdvisorSignal.VERKAUFEN -> 3
        AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> null
    }
}
