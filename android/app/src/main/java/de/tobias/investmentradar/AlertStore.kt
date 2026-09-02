package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AlertStore {
    private const val PREFS = "investment_radar_alerts"
    private const val KEY = "history"
    private const val TOMBSTONES_KEY = "tombstones"
    private const val MAX = 50

    fun add(context: Context, alert: SignalAlert) {
        mergeRemote(context, listOf(alert))
    }

    fun mergeRemote(context: Context, remote: List<SignalAlert>): List<StoredAlert> {
        val now = System.currentTimeMillis()
        val tombstones = readTombstones(context)
        val merged = AlertCenterState.merge(readStored(context), remote, tombstones, now).take(MAX)
        write(context, merged, AlertCenterState.activeTombstones(tombstones, now))
        return merged
    }

    fun read(context: Context): List<SignalAlert> = readStored(context).map { it.alert }

    fun readStored(context: Context): List<StoredAlert> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    val alert = SignalAlert(
                        id = o.optString("id"),
                        itemId = o.optString("itemId"),
                        level = o.optString("level"),
                        title = o.optString("title"),
                        message = o.optString("message"),
                        createdAt = o.optString("createdAt")
                    )
                    if (alert.id.isBlank()) null else StoredAlert(alert, o.optBoolean("isRead", false))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun markRead(context: Context, alertId: String) {
        if (alertId.isBlank()) return
        val next = readStored(context).map { stored ->
            if (stored.alert.id == alertId) stored.copy(isRead = true) else stored
        }
        write(context, next, readTombstones(context))
    }

    fun markAllRead(context: Context) {
        write(context, AlertCenterState.markAllRead(readStored(context)), readTombstones(context))
    }

    fun delete(context: Context, alertId: String) {
        val now = System.currentTimeMillis()
        val next = AlertCenterState.delete(readStored(context), readTombstones(context), alertId, now)
        write(context, next.items, next.tombstones)
    }

    fun clear(context: Context) {
        val now = System.currentTimeMillis()
        val next = AlertCenterState.clear(readStored(context), readTombstones(context), now)
        write(context, next.items, next.tombstones)
    }

    private fun readTombstones(context: Context): Map<String, Long> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOMBSTONES_KEY, "{}") ?: "{}"
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val timestamp = obj.optLong(id, 0L)
                    if (id.isNotBlank() && timestamp > 0L) put(id, timestamp)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun write(context: Context, alerts: List<StoredAlert>, tombstones: Map<String, Long>) {
        val arr = JSONArray()
        alerts.take(MAX).forEach { stored ->
            val a = stored.alert
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("itemId", a.itemId)
                put("level", a.level)
                put("title", a.title)
                put("message", a.message)
                put("createdAt", a.createdAt)
                put("isRead", stored.isRead)
            })
        }
        val tombstoneJson = JSONObject()
        tombstones.forEach { (id, timestamp) -> tombstoneJson.put(id, timestamp) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString())
            .putString(TOMBSTONES_KEY, tombstoneJson.toString())
            .apply()
    }
}
