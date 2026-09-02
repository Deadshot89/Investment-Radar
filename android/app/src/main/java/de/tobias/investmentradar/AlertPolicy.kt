package de.tobias.investmentradar

import kotlin.math.abs

object AlertPolicy {
    private val DAILY_DROP_PATTERN = Regex("Tagesbewegung\\s+(-?\\d+(?:[.,]\\d+)?)\\s*%", RegexOption.IGNORE_CASE)

    fun shouldNotify(alert: SignalAlert, prefs: AlertPreferences): Boolean = when (alert.level.trim().uppercase()) {
        "BUY" -> prefs.buyEnabled
        "REVIEW" -> prefs.reviewEnabled
        "SELL" -> prefs.sellEnabled
        "THRESHOLD" -> prefs.thresholdEnabled && passesLocalDailyDropThreshold(alert, prefs.localDailyDropThresholdPct)
        else -> prefs.minimumSeverity.equals("ALL", true)
    }

    fun shouldStore(alert: SignalAlert, prefs: AlertPreferences): Boolean = when (alert.level.trim().uppercase()) {
        "BUY", "REVIEW", "SELL", "THRESHOLD" -> true
        else -> prefs.minimumSeverity.equals("ALL", true)
    }

    fun isRelevantForPortfolio(alert: SignalAlert, holdingIds: Set<String>): Boolean = when (alert.level.trim().uppercase()) {
        "REVIEW", "SELL", "THRESHOLD" -> alert.itemId.isNotBlank() && alert.itemId in holdingIds
        else -> true
    }

    private fun passesLocalDailyDropThreshold(alert: SignalAlert, configuredPct: Double?): Boolean {
        val threshold = configuredPct?.takeIf { it.isFinite() && it > 0.0 } ?: return true
        if (!alert.title.contains("Tagesverlust", ignoreCase = true)) return true
        val actual = DAILY_DROP_PATTERN.find(alert.message)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?: return true
        return actual <= -abs(threshold)
    }
}
