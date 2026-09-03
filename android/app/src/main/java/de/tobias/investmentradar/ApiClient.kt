package de.tobias.investmentradar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal fun recommendationFallback(recommendation: String, status: String): String =
    recommendation.ifBlank {
        when (status.trim().uppercase()) {
            "KAUFEN", "BUY" -> "BUY"
            "VERKAUFEN", "SELL", "VERKAUF PRÜFEN", "DRINGEND PRÜFEN", "DRINGEND_PRUEFEN", "REVIEW" -> "REVIEW"
            "NICHT KAUFEN", "NO_BUY" -> "NO_BUY"
            else -> "WATCH"
        }
    }

object ApiClient {
    suspend fun loadDashboard(): DashboardData = withContext(Dispatchers.IO) {
        NetworkRetryPolicy.execute { loadDashboardOnce() }
    }

    suspend fun loadCustomQuote(item: CustomInvestment): InvestmentItem = withContext(Dispatchers.IO) {
        NetworkRetryPolicy.execute { loadCustomQuoteOnce(item) }
    }

    suspend fun loadRadarPage(query: RadarQuery): RadarPage = withContext(Dispatchers.IO) {
        NetworkRetryPolicy.execute { loadRadarPageOnce(query) }
    }

    suspend fun loadRadarDetail(id: String): RadarSummaryItem = withContext(Dispatchers.IO) {
        NetworkRetryPolicy.execute { loadRadarDetailOnce(id) }
    }

    private fun loadCustomQuoteOnce(item: CustomInvestment): InvestmentItem {
        val baseUrl = checkedBaseUrl()
        val query = listOf(
            "id" to item.id, "name" to item.name, "ticker" to item.ticker, "isin" to item.isin,
            "type" to item.type, "risk" to item.risk.toString()
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return getJson("$baseUrl/api/custom-quote?$query") { parseInvestmentItem(it) }
    }

    private fun loadDashboardOnce(): DashboardData {
        val baseUrl = checkedBaseUrl()
        return getJson("$baseUrl/api/dashboard", ::parseDashboard)
    }

    private fun loadRadarPageOnce(query: RadarQuery): RadarPage {
        val baseUrl = checkedBaseUrl()
        val params = buildList {
            if (query.query.isNotBlank()) add("q" to query.query)
            query.type?.takeIf { it.isNotBlank() }?.let { add("type" to it) }
            query.region?.takeIf { it.isNotBlank() }?.let { add("region" to it) }
            query.country?.takeIf { it.isNotBlank() }?.let { add("country" to it) }
            query.sector?.takeIf { it.isNotBlank() }?.let { add("sector" to it) }
            query.recommendation?.takeIf { it.isNotBlank() }?.let { add("recommendation" to it) }
            query.qualityTier?.takeIf { it.isNotBlank() }?.let { add("qualityTier" to it) }
            query.riskMax?.let { add("riskMax" to it.toString()) }
            add("sort" to query.sort)
            add("page" to query.page.toString())
            add("pageSize" to query.pageSize.toString())
            if (query.tradeRepublicVerified) add("tradeRepublicVerified" to "true")
        }.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        return getJson("$baseUrl/api/radar?$params", ::parseRadarPage)
    }

    private fun loadRadarDetailOnce(id: String): RadarSummaryItem {
        val baseUrl = checkedBaseUrl()
        val encoded = URLEncoder.encode(id, "UTF-8").replace("+", "%20")
        return getJson("$baseUrl/api/instrument/$encoded", ::parseRadarSummary)
    }

    private fun checkedBaseUrl(): String {
        val baseUrl = BuildConfig.API_BASE_URL.trim().trimEnd('/')
        require(baseUrl.startsWith("https://") && !baseUrl.contains("YOUR-FUNCTION-APP")) {
            "Backend noch nicht eingerichtet. Azure Function App zuerst verbinden."
        }
        return baseUrl
    }

    private fun <T> getJson(endpoint: String, parser: (JSONObject) -> T): T {
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
            return parser(JSONObject(body))
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

    private fun parseRadarPage(obj: JSONObject): RadarPage = RadarPage(
        generatedAt = obj.optString("generatedAt", ""),
        total = obj.optInt("total", 0),
        universeTotal = obj.optInt("universeTotal", obj.optInt("total", 0)),
        page = obj.optInt("page", 1),
        pageSize = obj.optInt("pageSize", 40),
        hasMore = obj.optBoolean("hasMore", false),
        items = obj.optJSONArray("items").toRadarItems(),
        facets = parseRadarFacets(obj.optJSONObject("facets")),
        tradeRepublicVerifiedCount = obj.optInt("tradeRepublicVerifiedCount", 0),
        tradeRepublicUnverifiedCount = obj.optInt("tradeRepublicUnverifiedCount", 0)
    )

    private fun parseRadarFacets(obj: JSONObject?): RadarFacets = RadarFacets(
        types = obj?.optJSONArray("types").toRadarFacets(),
        regions = obj?.optJSONArray("regions").toRadarFacets(),
        countries = obj?.optJSONArray("countries").toRadarFacets(),
        sectors = obj?.optJSONArray("sectors").toRadarFacets(),
        qualityTiers = obj?.optJSONArray("qualityTiers").toRadarFacets()
    )

    private fun JSONArray?.toRadarItems(): List<RadarSummaryItem> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(::parseRadarSummary) }
    }

    private fun JSONArray?.toRadarFacets(): List<RadarFacet> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            optJSONObject(i)?.let { RadarFacet(it.optString("value", ""), it.optInt("count", 0)) }
        }.filter { it.value.isNotBlank() }
    }

    private fun parseRadarSummary(o: JSONObject): RadarSummaryItem {
        val recommendation = recommendationFallback(o.optString("recommendation"), o.optString("status"))
        return RadarSummaryItem(
            id = o.optString("id"),
            type = o.optString("type"),
            name = o.optString("name"),
            ticker = o.optString("ticker"),
            isin = o.optString("isin"),
            tradeRepublicName = o.optString("tradeRepublicName"),
            region = o.optString("region"),
            country = o.optString("country"),
            sector = o.optString("sector"),
            industry = o.optString("industry"),
            marketCapBucket = o.optString("marketCapBucket"),
            tradeRepublicEligible = o.nullableBoolean("tradeRepublicEligible"),
            dataQualityTier = o.optString("dataQualityTier", "B"),
            risk = o.optInt("risk", 3),
            price = o.nullableDouble("price"),
            priceEur = o.nullableDouble("priceEur"),
            currency = o.optString("currency", ""),
            percentChange = o.nullableDouble("percentChange"),
            scoreTotal = o.nullableInt("scoreTotal"),
            scoreQuality = o.nullableInt("scoreQuality"),
            scoreValuation = o.nullableInt("scoreValuation"),
            scoreGrowth = o.nullableInt("scoreGrowth"),
            scoreMomentum = o.nullableInt("scoreMomentum"),
            scoreRisk = o.nullableInt("scoreRisk"),
            coverage = o.nullableInt("coverage"),
            recommendation = recommendation,
            recommendationReasons = o.optJSONArray("recommendationReasons").toStrings(),
            purchaseEligible = o.optBoolean("purchaseEligible", false),
            dataSource = o.optString("dataSource", ""),
            dataDelayed = o.optBoolean("dataDelayed", false),
            dataError = o.nullableString("dataError"),
            analysisAsOf = o.nullableString("analysisAsOf"),
            momentum = o.optJSONObject("momentum")?.let(::parseMomentum),
            fundamentals = o.optJSONObject("fundamentals")?.let(::parseFundamentals)
        )
    }

    private fun JSONArray?.toInvestmentItems(): List<InvestmentItem> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(::parseInvestmentItem) }
    }

    private fun parseInvestmentItem(o: JSONObject): InvestmentItem {
        val status = o.optString("status")
        val recommendation = recommendationFallback(o.optString("recommendation"), status)
        return InvestmentItem(
            id = o.optString("id"), type = o.optString("type"), name = o.optString("name"),
            ticker = o.optString("ticker"), isin = o.optString("isin"), tradeRepublicName = o.optString("tradeRepublicName"),
            status = status, allocation = o.optInt("allocation", 0), risk = o.optInt("risk", 3),
            price = o.nullableDouble("price"), priceEur = o.nullableDouble("priceEur"), currency = o.optString("currency", ""),
            fxRateToEur = o.nullableDouble("fxRateToEur"), fxSource = o.optString("fxSource", ""),
            fxDelayed = o.optBoolean("fxDelayed", false), fxAsOf = o.nullableString("fxAsOf"),
            percentChange = o.nullableDouble("percentChange"), marketOpen = o.nullableBoolean("marketOpen"),
            dataSource = o.optString("dataSource", ""), dataDelayed = o.optBoolean("dataDelayed", false), dataError = o.nullableString("dataError"),
            scoreTotal = o.nullableInt("scoreTotal"), scoreQuality = o.nullableInt("scoreQuality"), scoreValuation = o.nullableInt("scoreValuation"),
            scoreGrowth = o.nullableInt("scoreGrowth"), scoreMomentum = o.nullableInt("scoreMomentum"), scoreRisk = o.nullableInt("scoreRisk"),
            coverage = o.nullableInt("coverage"), recommendation = recommendation,
            recommendationReasons = o.optJSONArray("recommendationReasons").toStrings(),
            momentum = o.optJSONObject("momentum")?.let(::parseMomentum),
            fundamentals = o.optJSONObject("fundamentals")?.let(::parseFundamentals),
            analysisAsOf = o.nullableString("analysisAsOf"),
            portfolioOnly = o.optBoolean("portfolioOnly", false)
        )
    }

    private fun parseMomentum(o: JSONObject): MomentumSnapshot = MomentumSnapshot(
        d1 = o.nullableDouble("d1"), m1 = o.nullableDouble("m1"), m3 = o.nullableDouble("m3"),
        m6 = o.nullableDouble("m6"), m12 = o.nullableDouble("m12"), score = o.nullableInt("score"),
        coveragePct = o.nullableInt("coveragePct"), stale = o.optBoolean("stale", false), source = o.optString("source", ""),
        asOf = o.nullableString("asOf"), error = o.nullableString("error")
    )

    private fun parseFundamentals(o: JSONObject): FundamentalSnapshot = FundamentalSnapshot(
        pe = o.nullableDouble("pe"), priceToSales = o.nullableDouble("priceToSales"), evToEbitda = o.nullableDouble("evToEbitda"),
        freeCashFlowYield = o.nullableDouble("freeCashFlowYield"), revenueGrowth = o.nullableDouble("revenueGrowth"), epsGrowth = o.nullableDouble("epsGrowth"),
        operatingMargin = o.nullableDouble("operatingMargin"), netMargin = o.nullableDouble("netMargin"), roe = o.nullableDouble("roe"),
        roic = o.nullableDouble("roic"), debtToEquity = o.nullableDouble("debtToEquity"), coveragePct = o.nullableInt("coveragePct"),
        stale = o.optBoolean("stale", false), source = o.optString("source", ""), asOf = o.nullableString("asOf"), error = o.nullableString("error")
    )

    private fun JSONArray?.toAlerts(): List<SignalAlert> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            optJSONObject(i)?.let { o ->
                SignalAlert(o.optString("id"), o.optString("itemId"), o.optString("level"), o.optString("title"), o.optString("message"), o.optString("createdAt"))
            }
        }
    }
}

private fun JSONObject.nullableDouble(name: String): Double? = if (!has(name) || isNull(name)) null else optDouble(name).takeIf { it.isFinite() }
private fun JSONObject.nullableInt(name: String): Int? = if (!has(name) || isNull(name)) null else optInt(name)
private fun JSONObject.nullableString(name: String): String? = if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
private fun JSONObject.nullableBoolean(name: String): Boolean? = if (!has(name) || isNull(name)) null else optBoolean(name)
private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
