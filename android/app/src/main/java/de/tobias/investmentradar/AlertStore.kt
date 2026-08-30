package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AlertStore {
    private const val PREFS = "investment_radar_alerts"
    private const val KEY = "history"
    private const val MAX = 50

    fun add(context: Context, alert: SignalAlert) {
        val current = read(context).toMutableList()
        current.removeAll { it.id == alert.id }
        current.add(0, alert)
        save(context, current.take(MAX))
    }

    fun read(context: Context): List<SignalAlert> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    SignalAlert(
                        id = o.optString("id"),
                        itemId = o.optString("itemId"),
                        level = o.optString("level"),
                        title = o.optString("title"),
                        message = o.optString("message"),
                        createdAt = o.optString("createdAt")
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, alerts: List<SignalAlert>) {
        val arr = JSONArray()
        alerts.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("itemId", a.itemId)
                put("level", a.level)
                put("title", a.title)
                put("message", a.message)
                put("createdAt", a.createdAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
