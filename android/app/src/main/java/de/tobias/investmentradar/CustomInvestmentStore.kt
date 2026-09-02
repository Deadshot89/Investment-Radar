package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CustomInvestment(
    val id: String,
    val name: String,
    val ticker: String,
    val isin: String,
    val type: String,
    val tradeRepublicUrl: String = "",
    val risk: Int = 3,
    val manualPriceEur: Double? = null
) {
    fun fallbackItem(error: String? = null, manual: Double? = manualPriceEur) = InvestmentItem(
        id = id,
        type = type,
        name = name,
        ticker = ticker,
        isin = isin,
        tradeRepublicName = name,
        status = "EIGEN",
        allocation = 0,
        risk = risk,
        price = manual,
        priceEur = manual,
        currency = if (manual != null) "EUR" else "",
        fxRateToEur = null,
        fxSource = "",
        fxDelayed = false,
        fxAsOf = null,
        percentChange = null,
        marketOpen = null,
        dataSource = if (manual != null) "Manueller EUR-Kurs" else "",
        dataDelayed = manual != null,
        dataError = if (manual != null) null else error
    )
}

object CustomInvestmentStore {
    private const val PREFS = "investment_radar_custom_assets"
    private const val KEY = "items"

    fun read(context: Context): List<CustomInvestment> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val ticker = o.optString("ticker").trim().uppercase()
                    val id = o.optString("id").trim()
                    if (id.isBlank() || ticker.isBlank()) continue
                    add(CustomInvestment(
                        id = id,
                        name = o.optString("name", ticker).trim().ifBlank { ticker },
                        ticker = ticker,
                        isin = o.optString("isin").trim().uppercase(),
                        type = if (o.optString("type").equals("ETF", true)) "ETF" else "Aktie",
                        tradeRepublicUrl = o.optString("tradeRepublicUrl").trim(),
                        risk = o.optInt("risk", 3).coerceIn(1, 5),
                        manualPriceEur = o.optDouble("manualPriceEur").takeIf { it.isFinite() && it > 0.0 }
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, item: CustomInvestment) {
        val next = read(context).filterNot { it.id == item.id }.toMutableList().apply { add(item) }
        write(context, next)
    }

    fun remove(context: Context, itemId: String) {
        write(context, read(context).filterNot { it.id == itemId })
    }

    fun createId(ticker: String, isin: String): String {
        val key = isin.ifBlank { ticker }.trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-').take(80)
        return "custom-$key"
    }

    private fun write(context: Context, items: List<CustomInvestment>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("name", item.name)
                .put("ticker", item.ticker)
                .put("isin", item.isin)
                .put("type", item.type)
                .put("tradeRepublicUrl", item.tradeRepublicUrl)
                .put("risk", item.risk)
                .apply { item.manualPriceEur?.let { put("manualPriceEur", it) } })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
