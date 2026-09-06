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

object AdvisorStore {
    private const val PREFS = "investment_radar_advisor"
    private const val KEY = "snapshots_v1"

    fun snapshot(context: Context, instrumentId: String): AdvisorSnapshot =
        readAll(context)[instrumentId] ?: AdvisorSnapshot()

    fun record(
        context: Context,
        result: AdvisorResult,
        analysisDay: String
    ): AdvisorSnapshot {
        if (result.instrumentId.isBlank() || analysisDay.isBlank()) return AdvisorSnapshot()
        val all = readAll(context).toMutableMap()
        val next = AdvisorHistoryState.record(
            snapshot = all[result.instrumentId] ?: AdvisorSnapshot(),
            result = result,
            analysisDay = analysisDay
        )
        all[result.instrumentId] = next
        writeAll(context, all)
        return next
    }

    fun readAll(context: Context): Map<String, AdvisorSnapshot> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "{}") ?: "{}"
        return decode(raw)
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

    private fun writeAll(context: Context, values: Map<String, AdvisorSnapshot>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode(values))
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
    }

    private fun JSONObject.toDatedResult(): DatedAdvisorResult? {
        val day = optString("analysisDay").takeIf { it.isNotBlank() } ?: return null
        val resultObject = optJSONObject("result") ?: return null
        val id = resultObject.optString("instrumentId").takeIf { it.isNotBlank() } ?: return null
        val signal = runCatching {
            AdvisorSignal.valueOf(resultObject.optString("signal"))
        }.getOrNull() ?: return null
        val score = if (resultObject.has("score")) resultObject.optInt("score") else null
        val result = AdvisorResult(
            instrumentId = id,
            signal = signal,
            score = score,
            reliable = resultObject.optBoolean("reliable", false),
            reasons = resultObject.optJSONArray("reasons").toStringList(),
            risks = resultObject.optJSONArray("risks").toStringList()
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
