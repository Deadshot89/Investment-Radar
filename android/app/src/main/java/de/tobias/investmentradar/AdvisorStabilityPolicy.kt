package de.tobias.investmentradar

object AdvisorStabilityPolicy {
    fun resolve(previous: AdvisorSnapshot, proposed: AdvisorResult): AdvisorResult {
        if (!proposed.reliable) return proposed

        val current = previous.current?.result ?: return proposed
        if (current.instrumentId != proposed.instrumentId) return proposed
        if (!current.reliable) return proposed
        if (!isWorse(proposed.signal, current.signal)) return proposed

        val proposedScore = proposed.score
        val lastReliable = previous.lastReliable?.result
        val lastReliableScore = lastReliable?.score
        val severeSell = proposed.signal == AdvisorSignal.VERKAUFEN &&
            proposedScore != null && proposedScore <= 25
        val largeScoreDrop = proposedScore != null && lastReliableScore != null &&
            lastReliableScore - proposedScore >= 25
        if (severeSell || largeScoreDrop) return proposed

        if (current.signal == proposed.signal) return proposed

        return lastReliable
            ?.takeIf { it.instrumentId == proposed.instrumentId && it.reliable }
            ?: current
    }

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
