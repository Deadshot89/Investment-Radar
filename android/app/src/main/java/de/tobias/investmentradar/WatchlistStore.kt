package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray

object WatchlistStore {
    private const val PREFS = "investment_radar_watchlist"
    private const val KEY = "item_ids"

    fun read(context: Context): Set<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    array.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    fun toggle(context: Context, itemId: String): Set<String> {
        val next = read(context).toMutableSet().apply {
            if (!add(itemId)) remove(itemId)
        }
        write(context, next)
        return next
    }

    fun remove(context: Context, itemId: String): Set<String> {
        val next = read(context).filterNot { it == itemId }.toSet()
        write(context, next)
        return next
    }

    private fun write(context: Context, ids: Set<String>) {
        val array = JSONArray()
        ids.sorted().forEach(array::put)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
