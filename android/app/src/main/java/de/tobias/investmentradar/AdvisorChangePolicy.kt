package de.tobias.investmentradar

data class AdvisorNotificationEvent(
    val id: String,
    val instrumentId: String,
    val previousSignal: AdvisorSignal?,
    val newSignal: AdvisorSignal,
    val analysisDay: String,
    val reasons: List<String>
)

object AdvisorChangePolicy {
    fun notificationEvent(
        previous: AdvisorResult?,
        current: AdvisorResult,
        analysisDay: String
    ): AdvisorNotificationEvent? {
        if (previous == null && !current.reliable) return null
        if (previous?.signal == current.signal) return null

        val previousSignal = previous?.signal
        val previousLabel = previousSignal?.name ?: "NONE"
        return AdvisorNotificationEvent(
            id = "${current.instrumentId}|$previousLabel|${current.signal.name}|$analysisDay",
            instrumentId = current.instrumentId,
            previousSignal = previousSignal,
            newSignal = current.signal,
            analysisDay = analysisDay,
            reasons = current.reasons.take(3)
        )
    }
}
