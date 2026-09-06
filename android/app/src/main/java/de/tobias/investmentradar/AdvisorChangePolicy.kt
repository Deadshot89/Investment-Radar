package de.tobias.investmentradar

enum class AdvisorNotificationEventKind {
    SIGNAL_CHANGE,
    NEW_STRONG_OPPORTUNITY,
    REALLOCATION,
    RELIABILITY_LOST
}

data class AdvisorNotificationEvent(
    val id: String,
    val instrumentId: String,
    val previousSignal: AdvisorSignal?,
    val newSignal: AdvisorSignal,
    val analysisDay: String,
    val reasons: List<String>,
    val kind: AdvisorNotificationEventKind = AdvisorNotificationEventKind.SIGNAL_CHANGE,
    val fromItemId: String? = null,
    val toItemId: String? = null,
    val amountEur: Int? = null
)

object AdvisorChangePolicy {
    private const val STRONG_OPPORTUNITY_SCORE = 82
    private const val MIN_REALLOCATION_EUR = 20

    fun notificationEvent(
        previous: AdvisorResult?,
        current: AdvisorResult,
        analysisDay: String,
        isHolding: Boolean = true
    ): AdvisorNotificationEvent? {
        if (previous == null && !current.reliable) return null
        if (previous?.signal == current.signal && previous.reliable == current.reliable) return null

        if (!isHolding && previous == null) {
            if (!current.reliable || current.signal != AdvisorSignal.NACHKAUFEN || (current.score ?: 0) < STRONG_OPPORTUNITY_SCORE) {
                return null
            }
            return AdvisorNotificationEvent(
                id = "opportunity|${current.instrumentId}|$analysisDay",
                instrumentId = current.instrumentId,
                previousSignal = null,
                newSignal = current.signal,
                analysisDay = analysisDay,
                reasons = current.reasons.take(3),
                kind = AdvisorNotificationEventKind.NEW_STRONG_OPPORTUNITY
            )
        }

        val previousSignal = previous?.signal
        val previousLabel = previousSignal?.name ?: "NONE"
        val kind = if (previous?.reliable == true && !current.reliable) {
            AdvisorNotificationEventKind.RELIABILITY_LOST
        } else {
            AdvisorNotificationEventKind.SIGNAL_CHANGE
        }
        return AdvisorNotificationEvent(
            id = "${current.instrumentId}|$previousLabel|${current.signal.name}|$analysisDay",
            instrumentId = current.instrumentId,
            previousSignal = previousSignal,
            newSignal = current.signal,
            analysisDay = analysisDay,
            reasons = current.reasons.take(3),
            kind = kind
        )
    }

    fun planEvents(
        previous: PortfolioAdvisorPlan?,
        current: PortfolioAdvisorPlan,
        analysisDay: String
    ): List<AdvisorNotificationEvent> {
        val before = previous?.reallocations.orEmpty()
            .filter { it.amountEur >= MIN_REALLOCATION_EUR }
            .map { reallocationKey(it) }
            .toSet()

        return current.reallocations.asSequence()
            .filter { it.amountEur >= MIN_REALLOCATION_EUR }
            .filterNot { reallocationKey(it) in before }
            .distinctBy { reallocationKey(it) }
            .map { suggestion ->
                AdvisorNotificationEvent(
                    id = "reallocation|${suggestion.fromItemId}|${suggestion.toItemId}|${suggestion.amountEur}|$analysisDay",
                    instrumentId = suggestion.fromItemId,
                    previousSignal = null,
                    newSignal = AdvisorSignal.REDUZIEREN,
                    analysisDay = analysisDay,
                    reasons = listOf(suggestion.reason).filter { it.isNotBlank() },
                    kind = AdvisorNotificationEventKind.REALLOCATION,
                    fromItemId = suggestion.fromItemId,
                    toItemId = suggestion.toItemId,
                    amountEur = suggestion.amountEur
                )
            }
            .toList()
    }

    private fun reallocationKey(value: ReallocationSuggestion): String =
        "${value.fromItemId}|${value.toItemId}|${value.amountEur}"
}
