package de.tobias.investmentradar

object AlertPolicy {
    fun shouldNotify(alert: SignalAlert, prefs: AlertPreferences): Boolean = when (alert.level.trim().uppercase()) {
        "BUY" -> prefs.buyEnabled
        "REVIEW" -> prefs.reviewEnabled
        "SELL" -> prefs.sellEnabled
        "THRESHOLD" -> prefs.thresholdEnabled
        else -> prefs.minimumSeverity.equals("ALL", true)
    }

    fun shouldStore(alert: SignalAlert, prefs: AlertPreferences): Boolean = when (alert.level.trim().uppercase()) {
        "BUY", "REVIEW", "SELL", "THRESHOLD" -> true
        else -> prefs.minimumSeverity.equals("ALL", true)
    }
}
