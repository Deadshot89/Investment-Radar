package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
            val storedPurchases = prefs.getString(purchasesKey(itemId), null)
                ?.let(::decodePurchases)
                .orEmpty()
            if (storedPurchases.isNotEmpty()) {
                PortfolioPosition(itemId = itemId, purchases = storedPurchases)
            } else {
                val invested = prefs.getString(investedKey(itemId), null)?.toDoubleOrNull() ?: 0.0
                val shares = prefs.getString(sharesKey(itemId), null)?.toDoubleOrNull() ?: 0.0
                val migrated = if (invested > 0.0 && shares > 0.0) {
                    listOf(
                        PortfolioPurchase(
                            id = "legacy-$itemId",
                            date = "",
                            investedAmount = invested,
                            shares = shares
                        )
                    )
                } else {
                    emptyList()
                }
                PortfolioPosition(itemId = itemId, purchases = migrated)
            }
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
            .putString(investedKey(position.itemId), position.investedAmount.toString())
            .putString(sharesKey(position.itemId), position.shares.toString())
            .putString(purchasesKey(position.itemId), encodePurchases(position.purchases))
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
            .remove(purchasesKey(itemId))
            .apply()
    }

    private fun encodePurchases(purchases: List<PortfolioPurchase>): String {
        val array = JSONArray()
        purchases.forEach { purchase ->
            array.put(
                JSONObject()
                    .put("id", purchase.id)
                    .put("date", purchase.date)
                    .put("investedAmount", purchase.investedAmount.coerceAtLeast(0.0))
                    .put("shares", purchase.shares.coerceAtLeast(0.0))
            )
        }
        return array.toString()
    }

    private fun decodePurchases(raw: String): List<PortfolioPurchase> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val amount = item.optDouble("investedAmount", Double.NaN)
                val shares = item.optDouble("shares", Double.NaN)
                if (id.isBlank() || !amount.isFinite() || !shares.isFinite() || amount < 0.0 || shares < 0.0) continue
                add(
                    PortfolioPurchase(
                        id = id,
                        date = item.optString("date").trim(),
                        investedAmount = amount,
                        shares = shares
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun investedKey(itemId: String) = "position.$itemId.invested"
    private fun sharesKey(itemId: String) = "position.$itemId.shares"
    private fun purchasesKey(itemId: String) = "position.$itemId.purchases"
}
