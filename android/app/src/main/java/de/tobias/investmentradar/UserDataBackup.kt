package de.tobias.investmentradar

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class UserDataBackupSnapshot(
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: String,
    val preferences: Map<String, Map<String, Any>>
)

object UserDataBackupCodec {
    const val FORMAT = "investment-radar-user-backup"
    const val SCHEMA_VERSION = 1

    fun encode(
        preferences: Map<String, Map<String, *>>,
        appVersion: String,
        createdAt: String = Instant.now().toString()
    ): String {
        val preferenceRoot = JSONObject()
        preferences.toSortedMap().forEach { (fileName, values) ->
            val fileObject = JSONObject()
            values.toSortedMap().forEach { (key, value) ->
                fileObject.put(key, encodeValue(value))
            }
            preferenceRoot.put(fileName, fileObject)
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("appVersion", appVersion)
            .put("createdAt", createdAt)
            .put("preferences", preferenceRoot)
            .toString(2)
    }

    fun decode(raw: String): UserDataBackupSnapshot {
        val root = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("Die Datei ist kein gültiges Investment-Radar-Backup.", it) }

        require(root.optString("format") == FORMAT) {
            "Die ausgewählte Datei ist kein Investment-Radar-Backup."
        }
        val schemaVersion = root.optInt("schemaVersion", -1)
        require(schemaVersion == SCHEMA_VERSION) {
            "Backup-Version $schemaVersion wird von dieser App nicht unterstützt."
        }
        val preferencesJson = root.optJSONObject("preferences")
            ?: throw IllegalArgumentException("Das Backup enthält keine Nutzerdaten.")

        val preferences = buildMap {
            val fileNames = preferencesJson.keys()
            while (fileNames.hasNext()) {
                val fileName = fileNames.next()
                val fileJson = preferencesJson.optJSONObject(fileName)
                    ?: throw IllegalArgumentException("Ungültiger Backup-Bereich: $fileName")
                val values = buildMap<String, Any> {
                    val keys = fileJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val encoded = fileJson.optJSONObject(key)
                            ?: throw IllegalArgumentException("Ungültiger Backup-Wert: $fileName/$key")
                        put(key, decodeValue(encoded))
                    }
                }
                put(fileName, values)
            }
        }

        return UserDataBackupSnapshot(
            schemaVersion = schemaVersion,
            appVersion = root.optString("appVersion"),
            createdAt = root.optString("createdAt"),
            preferences = preferences
        )
    }

    private fun encodeValue(value: Any?): JSONObject = when (value) {
        is String -> JSONObject().put("type", "string").put("value", value)
        is Boolean -> JSONObject().put("type", "boolean").put("value", value)
        is Int -> JSONObject().put("type", "int").put("value", value)
        is Long -> JSONObject().put("type", "long").put("value", value)
        is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
        is Set<*> -> {
            require(value.all { it is String }) { "Backups unterstützen nur String-Sets." }
            JSONObject()
                .put("type", "string_set")
                .put("value", JSONArray(value.filterIsInstance<String>().sorted()))
        }
        else -> throw IllegalArgumentException("Nicht unterstützter Backup-Datentyp: ${value?.javaClass?.name ?: "null"}")
    }

    private fun decodeValue(encoded: JSONObject): Any = when (encoded.optString("type")) {
        "string" -> encoded.getString("value")
        "boolean" -> encoded.getBoolean("value")
        "int" -> encoded.getInt("value")
        "long" -> encoded.getLong("value")
        "float" -> encoded.getDouble("value").also {
            require(it.isFinite()) { "Ungültiger Float-Wert im Backup." }
        }.toFloat()
        "string_set" -> {
            val array = encoded.optJSONArray("value")
                ?: throw IllegalArgumentException("Ungültiges String-Set im Backup.")
            buildSet {
                for (index in 0 until array.length()) add(array.getString(index))
            }
        }
        else -> throw IllegalArgumentException("Unbekannter Backup-Datentyp '${encoded.optString("type")}'.")
    }
}

object UserDataBackupManager {
    val preferenceFiles: List<String> = listOf(
        "investment_radar_portfolio",
        "investment_radar_budget",
        "investment_radar_settings",
        "investment_radar_watchlist",
        "investment_radar_savings_plans",
        "investment_radar_custom_assets",
        "investment_radar_exit_strategy",
        "investment_radar_advisor",
        "investment_radar_alerts",
        "investment_radar_alert_preferences"
    )

    fun exportJson(
        context: Context,
        appVersion: String,
        createdAt: String = Instant.now().toString()
    ): String {
        val content = preferenceFiles.associateWith { fileName ->
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE).all.toMap()
        }
        return UserDataBackupCodec.encode(content, appVersion, createdAt)
    }

    fun restoreJson(context: Context, raw: String): Int {
        val snapshot = UserDataBackupCodec.decode(raw)
        val unknownFiles = snapshot.preferences.keys - preferenceFiles.toSet()
        require(unknownFiles.isEmpty()) {
            "Das Backup enthält unbekannte Datenbereiche: ${unknownFiles.sorted().joinToString()}."
        }
        val missingFiles = preferenceFiles.filterNot(snapshot.preferences::containsKey)
        require(missingFiles.isEmpty()) {
            "Das Backup ist unvollständig und wird nicht eingespielt."
        }

        val previous = preferenceFiles.associateWith { fileName ->
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE).all.toMap()
        }

        try {
            preferenceFiles.forEach { fileName ->
                val preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                if (!replace(preferences, snapshot.preferences.getValue(fileName))) {
                    throw IllegalStateException("Datenbereich '$fileName' konnte nicht gespeichert werden.")
                }
            }
        } catch (error: Throwable) {
            previous.forEach { (fileName, values) ->
                replace(context.getSharedPreferences(fileName, Context.MODE_PRIVATE), values)
            }
            throw error
        }

        return snapshot.preferences.values.sumOf { it.size }
    }

    private fun replace(preferences: SharedPreferences, values: Map<String, *>): Boolean {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    require(value.all { it is String }) { "Ungültiges String-Set für '$key'." }
                    editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
                else -> throw IllegalArgumentException("Nicht unterstützter Wert für '$key'.")
            }
        }
        return editor.commit()
    }
}
