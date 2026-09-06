package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DatedAdvisorResult(
    val analysisDay: String,
    val result: AdvisorResult
)

data class AdvisorSnapshot(
    val current: DatedAdvisorResult? = null,
    val previous: DatedAdvisorResult? = null,
    val lastReliable: DatedAdvisorResult? = null
)

data class AdvisorHistoryEntry(
    val analysisDay: String,
    val previousSignal: AdvisorSignal?,
    val newSignal: AdvisorSignal,
    val score: Int?,
    val reasons: List<String>
)

object AdvisorHistoryState {
    fun record(
        snapshot: AdvisorSnapshot,
        result: AdvisorResult,
        analysisDay: String
    ): AdvisorSnapshot {
        val dated = DatedAdvisorResult(analysisDay = analysisDay, result = result)
        if (snapshot.current == dated) return snapshot

        return AdvisorSnapshot(
            current = dated,
            previous = snapshot.current,
            lastReliable = if (result.reliable) dated else snapshot.lastReliable
        )
    }
}

object AdvisorMaterialHistory {
    private const val MAX_ENTRIES = 20

    fun record(
        history: List<AdvisorHistoryEntry>,
        previous: AdvisorResult?,
        result: AdvisorResult,
        analysisDay: String
    ): List<AdvisorHistoryEntry> {
        val material = previous == null ||
            previous.signal != result.signal ||
            previous.reliable != result.reliable
        if (!material || analysisDay.isBlank()) return history

        val entry = AdvisorHistoryEntry(
            analysisDay = analysisDay,
            previousSignal = previous?.signal,
            newSignal = result.signal,
            score = result.score,
            reasons = result.reasons.take(3)
        )
        if (history.firstOrNull() == entry) return history
        return (listOf(entry) + history).take(MAX_ENTRIES)
    }
}

object AdvisorStore {
    private const val PREFS = "investment_radar_advisor"
    private const val KEY = "snapshots_v1"
    private const val HISTORY_KEY = "history_v2"

    fun snapshot(context: Context, instrumentId: String): AdvisorSnapshot =
        readAll(context)[instrumentId] ?: AdvisorSnapshot()

    fun history(context: Context, instrumentId: String): List<AdvisorHistoryEntry> =
        readHistory(context)[instrumentId].orEmpty()

    fun record(
        context: Context,
        result: AdvisorResult,
        analysisDay: String
    ): AdvisorSnapshot {
        if (result.instrumentId.isBlank() || analysisDay.isBlank()) return AdvisorSnapshot()
        val all = readAll(context).toMutableMap()
        val previousResult = all[result.instrumentId]?.current?.result
        val next = AdvisorHistoryState.record(
            snapshot = all[result.instrumentId] ?: AdvisorSnapshot(),
            result = result,
            analysisDay = analysisDay
        )
        all[result.instrumentId] = next
        writeAll(context, all)

        val histories = readHistory(context).toMutableMap()
        histories[result.instrumentId] = AdvisorMaterialHistory.record(
            history = histories[result.instrumentId].orEmpty(),
            previous = previousResult,
            result = result,
            analysisDay = analysisDay
        )
        writeHistory(context, histories)
        return next
    }

    fun readAll(context: Context): Map<String, AdvisorSnapshot> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "{}") ?: "{}"
        return decode(raw)
    }

    fun readHistory(context: Context): Map<String, List<AdvisorHistoryEntry>> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(HISTORY_KEY, "{}") ?: "{}"
        return decodeHistory(raw)
    }

    internal fun decode(raw: String): Map<String, AdvisorSnapshot> = runCatching {
        val root = JSONObject(raw)
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val objectValue = root.optJSONObject(id) ?: continue
                put(
                    id,
                    AdvisorSnapshot(
                        current = objectValue.optJSONObject("current")?.toDatedResult(),
                        previous = objectValue.optJSONObject("previous")?.toDatedResult(),
                        lastReliable = objectValue.optJSONObject("lastReliable")?.toDatedResult()
                    )
                )
            }
        }
    }.getOrDefault(emptyMap())

    internal fun encode(values: Map<String, AdvisorSnapshot>): String {
        val root = JSONObject()
        values.forEach { (id, snapshot) ->
            if (id.isBlank()) return@forEach
            root.put(id, JSONObject().apply {
                snapshot.current?.let { put("current", it.toJson()) }
                snapshot.previous?.let { put("previous", it.toJson()) }
                snapshot.lastReliable?.let { put("lastReliable", it.toJson()) }
            })
        }
        return root.toString()
    }

    internal fun encodeHistory(values: Map<String, List<AdvisorHistoryEntry>>): String {
        val root = JSONObject()
        values.forEach { (id, entries) ->
            if (id.isBlank()) return@forEach
            root.put(id, JSONArray().apply {
                entries.take(20).forEach { entry ->
                    put(JSONObject().apply {
                        put("analysisDay", entry.analysisDay)
                        entry.previousSignal?.let { put("previousSignal", it.name) }
                        put("newSignal", entry.newSignal.name)
                        entry.score?.let { put("score", it) }
                        put("reasons", JSONArray(entry.reasons))
                    })
                }
            })
        }
        return root.toString()
    }

    internal fun decodeHistory(raw: String): Map<String, List<AdvisorHistoryEntry>> = runCatching {
        val root = JSONObject(raw)
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val array = root.optJSONArray(id) ?: continue
                val entries = (0 until array.length()).mapNotNull { index ->
                    val value = array.optJSONObject(index) ?: return@mapNotNull null
                    val day = value.optString("analysisDay").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val newSignal = runCatching {
                        AdvisorSignal.valueOf(value.optString("newSignal"))
                    }.getOrNull() ?: return@mapNotNull null
                    val previousSignal = value.optString("previousSignal")
                        .takeIf { it.isNotBlank() }
                        ?.let { runCatching { AdvisorSignal.valueOf(it) }.getOrNull() }
                    AdvisorHistoryEntry(
                        analysisDay = day,
                        previousSignal = previousSignal,
                        newSignal = newSignal,
                        score = if (value.has("score")) value.optInt("score") else null,
                        reasons = value.optJSONArray("reasons").toStringList()
                    )
                }.take(20)
                put(id, entries)
            }
        }
    }.getOrDefault(emptyMap())

    private fun writeAll(context: Context, values: Map<String, AdvisorSnapshot>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode(values))
            .apply()
    }

    private fun writeHistory(context: Context, values: Map<String, List<AdvisorHistoryEntry>>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(HISTORY_KEY, encodeHistory(values))
            .apply()
    }

    private fun DatedAdvisorResult.toJson(): JSONObject = JSONObject().apply {
        put("analysisDay", analysisDay)
        put("result", result.toJson())
    }

    private fun AdvisorResult.toJson(): JSONObject = JSONObject().apply {
        put("instrumentId", instrumentId)
        put("signal", signal.name)
        score?.let { put("score", it) }
        put("reliable", reliable)
        put("reasons", JSONArray(reasons))
        put("risks", JSONArray(risks))
        put("confidencePct", confidencePct)
        put("timingFactor", timingFactor)
        pendingWorseSignal?.let { put("pendingWorseSignal", it.name) }
        put("pendingWorseCount", pendingWorseCount.coerceAtLeast(0))
    }

    private fun JSONObject.toDatedResult(): DatedAdvisorResult? {
        val day = optString("analysisDay").takeIf { it.isNotBlank() } ?: return null
        val resultObject = optJSONObject("result") ?: return null
        val id = resultObject.optString("instrumentId").takeIf { it.isNotBlank() } ?: return null
        val signal = runCatching {
            AdvisorSignal.valueOf(resultObject.optString("signal"))
        }.getOrNull() ?: return null
        val score = if (resultObject.has("score")) resultObject.optInt("score") else null
        val pendingWorseSignal = resultObject.optString("pendingWorseSignal")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { AdvisorSignal.valueOf(it) }.getOrNull() }
        val result = AdvisorResult(
            instrumentId = id,
            signal = signal,
            score = score,
            reliable = resultObject.optBoolean("reliable", false),
            reasons = resultObject.optJSONArray("reasons").toStringList(),
            risks = resultObject.optJSONArray("risks").toStringList(),
            confidencePct = resultObject.optInt("confidencePct", 0).coerceIn(0, 100),
            timingFactor = resultObject.optDouble("timingFactor", 1.0),
            pendingWorseSignal = pendingWorseSignal,
            pendingWorseCount = resultObject.optInt("pendingWorseCount", 0).coerceAtLeast(0)
        )
        return DatedAdvisorResult(day, result)
    }

    private fun JSONArray?.toStringList(): List<String> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }
}
