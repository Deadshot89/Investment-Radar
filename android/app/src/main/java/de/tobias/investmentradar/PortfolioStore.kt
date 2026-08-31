package de.tobias.investmentradar

import android.content.Context

object PortfolioStore {
    private const val PREFS = "investment_radar_portfolio"
    private const val LEGACY_KEY = "holding_ids"
    private const val POSITION_IDS_KEY = "position_ids"

    fun read(context: Context): Set<String> = readPositions(context).keys

    fun readPositions(context: Context): Map<String, PortfolioPosition> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = buildSet {
            addAll(prefs.getStringSet(LEGACY_KEY, emptySet()).orEmpty())
            addAll(prefs.getStringSet(POSITION_IDS_KEY, emptySet()).orEmpty())
        }
        return ids.associateWith { itemId ->
            PortfolioPosition(
                itemId = itemId,
                investedAmount = prefs.getString(investedKey(itemId), null)?.toDoubleOrNull() ?: 0.0,
                shares = prefs.getString(sharesKey(itemId), null)?.toDoubleOrNull() ?: 0.0
            )
        }
    }

    fun add(context: Context, itemId: String) {
        val existing = readPositions(context)[itemId] ?: PortfolioPosition(itemId)
        save(context, existing)
    }

    fun save(context: Context, position: PortfolioPosition) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = readPositions(context).keys.toMutableSet().apply { add(position.itemId) }
        prefs.edit()
            .putStringSet(LEGACY_KEY, ids)
            .putStringSet(POSITION_IDS_KEY, ids)
            .putString(investedKey(position.itemId), position.investedAmount.coerceAtLeast(0.0).toString())
            .putString(sharesKey(position.itemId), position.shares.coerceAtLeast(0.0).toString())
            .apply()
    }

    fun remove(context: Context, itemId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = readPositions(context).keys.toMutableSet().apply { remove(itemId) }
        prefs.edit()
            .putStringSet(LEGACY_KEY, ids)
            .putStringSet(POSITION_IDS_KEY, ids)
            .remove(investedKey(itemId))
            .remove(sharesKey(itemId))
            .apply()
    }

    private fun investedKey(itemId: String) = "position.$itemId.invested"
    private fun sharesKey(itemId: String) = "position.$itemId.shares"
}
