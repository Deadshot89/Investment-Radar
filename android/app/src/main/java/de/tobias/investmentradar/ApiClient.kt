package de.tobias.investmentradar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    suspend fun loadDashboard(): DashboardData = withContext(Dispatchers.IO) {
        NetworkRetryPolicy.execute {
            loadDashboardOnce()
        }
    }

    private fun loadDashboardOnce(): DashboardData {
        val baseUrl = BuildConfig.API_BASE_URL.trim().trimEnd('/')
        require(baseUrl.startsWith("https://") && !baseUrl.contains("YOUR-FUNCTION-APP")) {
            "Backend noch nicht eingerichtet. Azure Function App zuerst verbinden."
        }
        val endpoint = "$baseUrl/api/dashboard"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NetworkRetryPolicy.CONNECT_TIMEOUT_MS
            readTimeout = NetworkRetryPolicy.READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("Serverfehler $code: $body")
            return parseDashboard(JSONObject(body))
        } finally {
            conn.disconnect()
        }
    }

    private fun parseDashboard(obj: JSONObject): DashboardData {
        val items = obj.optJSONArray("items").toInvestmentItems()
        val alerts = obj.optJSONArray("alerts").toAlerts()
        return DashboardData(
            generatedAt = obj.optString("generatedAt", ""),
            marketLight = obj.optString("marketLight", "GELB"),
            budget = obj.optInt("budget", 100),
            topPickId = obj.optString("topPickId", items.firstOrNull()?.id.orEmpty()),
            items = items,
            alerts = alerts
        )
    }

    private fun JSONArray?.toInvestmentItems(): List<InvestmentItem> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            optJSONObject(i)?.let { o ->
                InvestmentItem(
                    id = o.optString("id"),
                    type = o.optString("type"),
                    name = o.optString("name"),
                    ticker = o.optString("ticker"),
                    isin = o.optString("isin"),
                    tradeRepublicName = o.optString("tradeRepublicName"),
                    status = o.optString("status"),
                    allocation = o.optInt("allocation", 0),
                    risk = o.optInt("risk", 3),
                    price = if (o.isNull("price")) null else o.optDouble("price"),
                    priceEur = if (o.isNull("priceEur")) null else o.optDouble("priceEur"),
                    currency = o.optString("currency", ""),
                    fxRateToEur = if (o.isNull("fxRateToEur")) null else o.optDouble("fxRateToEur"),
                    fxSource = o.optString("fxSource", ""),
                    fxDelayed = o.optBoolean("fxDelayed", false),
                    fxAsOf = if (o.isNull("fxAsOf")) null else o.optString("fxAsOf"),
                    percentChange = if (o.isNull("percentChange")) null else o.optDouble("percentChange"),
                    marketOpen = if (o.isNull("marketOpen")) null else o.optBoolean("marketOpen"),
                    dataSource = o.optString("dataSource", ""),
                    dataDelayed = o.optBoolean("dataDelayed", false),
                    dataError = if (o.isNull("dataError")) null else o.optString("dataError")
                )
            }
        }
    }

    private fun JSONArray?.toAlerts(): List<SignalAlert> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            optJSONObject(i)?.let { o ->
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
    }
}
