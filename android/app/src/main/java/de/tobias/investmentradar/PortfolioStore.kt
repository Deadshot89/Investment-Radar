package de.tobias.investmentradar

import android.content.Context

object PortfolioStore {
    private const val PREFS = "investment_radar_portfolio"
    private const val KEY = "holding_ids"

    fun read(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())
            ?.toSet()
            ?: emptySet()

    fun add(context: Context, itemId: String) {
        val next = read(context).toMutableSet().apply { add(itemId) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, next).apply()
    }

    fun remove(context: Context, itemId: String) {
        val next = read(context).toMutableSet().apply { remove(itemId) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, next).apply()
    }
}
