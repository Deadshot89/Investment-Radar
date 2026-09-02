package de.tobias.investmentradar

import android.content.Context

data class AlertPreferences(
    val buyEnabled: Boolean = true,
    val reviewEnabled: Boolean = true,
    val sellEnabled: Boolean = true,
    val thresholdEnabled: Boolean = true,
    val minimumSeverity: String = "NORMAL",
    val localDailyDropThresholdPct: Double? = null
)

object AlertPreferencesStore {
    private const val PREFS = "investment_radar_alert_preferences"

    fun read(context: Context): AlertPreferences {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val threshold = if (prefs.contains("daily_drop_threshold")) {
            prefs.getFloat("daily_drop_threshold", 0f).toDouble().takeIf { it > 0.0 }
        } else null
        return AlertPreferences(
            buyEnabled = prefs.getBoolean("buy", true),
            reviewEnabled = prefs.getBoolean("review", true),
            sellEnabled = prefs.getBoolean("sell", true),
            thresholdEnabled = prefs.getBoolean("threshold", true),
            minimumSeverity = prefs.getString("minimum_severity", "NORMAL") ?: "NORMAL",
            localDailyDropThresholdPct = threshold
        )
    }

    fun save(context: Context, value: AlertPreferences) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putBoolean("buy", value.buyEnabled)
            putBoolean("review", value.reviewEnabled)
            putBoolean("sell", value.sellEnabled)
            putBoolean("threshold", value.thresholdEnabled)
            putString("minimum_severity", value.minimumSeverity)
            if (value.localDailyDropThresholdPct != null && value.localDailyDropThresholdPct > 0.0) {
                putFloat("daily_drop_threshold", value.localDailyDropThresholdPct.toFloat())
            } else remove("daily_drop_threshold")
        }.apply()
    }
}
