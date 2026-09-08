package de.tobias.investmentradar

import org.json.JSONArray
import org.json.JSONObject

object MarketEventsJsonParser {
    fun parse(obj: JSONObject): MarketEventsResponse = MarketEventsResponse(
        generatedAt = obj.optString("generatedAt", "").trim(),
        items = MarketEventEngine.normalize(obj.optJSONArray("items").toEvents()),
        errors = obj.optJSONArray("errors").toProviderErrors()
    )

    private fun JSONArray?.toEvents(): List<MarketEvent> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let(::parseEvent)
        }
    }

    private fun parseEvent(obj: JSONObject): MarketEvent? {
        val eventId = obj.optString("eventId", "").trim()
        val instrumentId = obj.optString("instrumentId", "").trim().lowercase()
        val title = obj.optString("title", "").trim()
        val eventAt = obj.optString("eventAt", "").trim()
        val publishedAt = obj.optString("publishedAt", "").trim()
        val sourceName = obj.optString("sourceName", "").trim()
        val sourceUrl = obj.optString("sourceUrl", "").trim()
        val fingerprint = obj.optString("fingerprint", "").trim()
        val type = enumValueOrNull<MarketEventType>(obj.optString("type", ""))
        val verification = enumValueOrNull<MarketEventVerification>(obj.optString("verification", ""))
        val direction = enumValueOrNull<MarketEventDirection>(obj.optString("direction", ""))
        val horizon = enumValueOrNull<MarketEventHorizon>(obj.optString("horizon", ""))

        if (
            eventId.isBlank() ||
            instrumentId.isBlank() ||
            title.isBlank() ||
            eventAt.isBlank() ||
            publishedAt.isBlank() ||
            sourceName.isBlank() ||
            !sourceUrl.startsWith("https://") ||
            fingerprint.isBlank() ||
            type == null ||
            verification == null ||
            direction == null ||
            horizon == null
        ) return null

        return MarketEvent(
            eventId = eventId,
            instrumentId = instrumentId,
            type = type,
            title = title,
            summary = obj.optString("summary", "").trim(),
            eventAt = eventAt,
            publishedAt = publishedAt,
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            verification = verification,
            direction = direction,
            horizon = horizon,
            materiality = obj.optInt("materiality", 0).coerceIn(0, 100),
            confidencePct = obj.optInt("confidencePct", 0).coerceIn(0, 100),
            fingerprint = fingerprint
        )
    }

    private fun JSONArray?.toProviderErrors(): List<MarketEventProviderError> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val obj = optJSONObject(index) ?: return@mapNotNull null
            val providerId = obj.optString("providerId", "").trim()
            val code = obj.optString("code", "").trim()
            val message = obj.optString("message", "").trim()
            if (providerId.isBlank() || code.isBlank() || message.isBlank()) null
            else MarketEventProviderError(providerId, code, message)
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        runCatching { enumValueOf<T>(value.trim().uppercase()) }.getOrNull()
}
