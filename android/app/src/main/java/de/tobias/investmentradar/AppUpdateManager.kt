package de.tobias.investmentradar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val notes: String = ""
)

object AppUpdateManager {
    suspend fun check(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val endpoint = apiBaseUrl()?.trimEnd('/')?.plus("/app-update") ?: return@withContext null
        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7_000
                readTimeout = 7_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val info = AppUpdateInfo(
                    versionCode = json.optLong("versionCode", 0L),
                    versionName = UpdatePolicy.displayVersion(json.optString("versionName")),
                    apkUrl = json.optString("apkUrl").trim(),
                    notes = json.optString("notes").trim()
                )
                if (info.versionCode <= 0L || info.apkUrl.isBlank()) return@runCatching null
                if (UpdatePolicy.isNewer(installedVersionCode(context), info.versionCode)) info else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    fun openUpdate(context: Context, info: AppUpdateInfo) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun apiBaseUrl(): String? = runCatching {
        val field = BuildConfig::class.java.getField("API_BASE_URL")
        (field.get(null) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }
}
