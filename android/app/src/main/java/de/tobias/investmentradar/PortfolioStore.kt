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
            val snapshotValue = prefs.getString(snapshotKey(itemId), null)
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 }
            val storedPurchases = prefs.getString(purchasesKey(itemId), null)
                ?.let(::decodePurchases)
                .orEmpty()
            val storedSales = prefs.getString(salesKey(itemId), null)
                ?.let(::decodeSales)
                .orEmpty()
            if (storedPurchases.isNotEmpty() || storedSales.isNotEmpty()) {
                PortfolioPosition(itemId = itemId, snapshotValueEur = snapshotValue, purchases = storedPurchases, sales = storedSales)
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
                PortfolioPosition(itemId = itemId, snapshotValueEur = snapshotValue, purchases = migrated)
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
        val editor = prefs.edit()
            .putStringSet(LEGACY_KEY, ids)
            .putStringSet(POSITION_IDS_KEY, ids)
            .putString(investedKey(position.itemId), position.investedAmount.toString())
            .putString(sharesKey(position.itemId), position.shares.toString())
            .putString(purchasesKey(position.itemId), encodePurchases(position.purchases))
            .putString(salesKey(position.itemId), encodeSales(position.sales))
        position.snapshotValueEur?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            editor.putString(snapshotKey(position.itemId), it.toString())
        } ?: editor.remove(snapshotKey(position.itemId))
        editor.apply()
    }

    fun remove(context: Context, itemId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = readPositions(context).keys.toMutableSet().apply { remove(itemId) }
        prefs.edit()
            .putStringSet(LEGACY_KEY, ids)
            .putStringSet(POSITION_IDS_KEY, ids)
            .remove(investedKey(itemId))
            .remove(sharesKey(itemId))
            .remove(snapshotKey(itemId))
            .remove(purchasesKey(itemId))
            .remove(salesKey(itemId))
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

    private fun encodeSales(sales: List<PortfolioSale>): String {
        val array = JSONArray()
        sales.forEach { sale ->
            array.put(
                JSONObject()
                    .put("id", sale.id)
                    .put("date", sale.date)
                    .put("proceeds", sale.proceeds.coerceAtLeast(0.0))
                    .put("shares", sale.shares.coerceAtLeast(0.0))
            )
        }
        return array.toString()
    }

    private fun decodeSales(raw: String): List<PortfolioSale> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val proceeds = item.optDouble("proceeds", Double.NaN)
                val shares = item.optDouble("shares", Double.NaN)
                if (id.isBlank() || !proceeds.isFinite() || !shares.isFinite() || proceeds < 0.0 || shares <= 0.0) continue
                add(
                    PortfolioSale(
                        id = id,
                        date = item.optString("date").trim(),
                        proceeds = proceeds,
                        shares = shares
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun investedKey(itemId: String) = "position.$itemId.invested"
    private fun sharesKey(itemId: String) = "position.$itemId.shares"
    private fun snapshotKey(itemId: String) = "position.$itemId.snapshotValueEur"
    private fun purchasesKey(itemId: String) = "position.$itemId.purchases"
    private fun salesKey(itemId: String) = "position.$itemId.sales"
}
