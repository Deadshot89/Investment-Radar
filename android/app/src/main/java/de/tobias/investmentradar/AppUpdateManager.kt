package de.tobias.investmentradar

import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String
)

object AppUpdateManager {
    private const val RELEASE_ASSET_NAME = "investment-radar.apk"
    private const val APK_MIME = "application/vnd.android.package-archive"

    suspend fun check(context: Context): AppUpdateInfo? = when (val result = checkResult(context)) {
        is UpdateCheckResult.Available -> result.update
        is UpdateCheckResult.Current, is UpdateCheckResult.Error -> null
    }

    suspend fun checkResult(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val repository = BuildConfig.GITHUB_REPOSITORY.trim()
        if (repository.isBlank() || !repository.contains('/')) {
            return@withContext UpdateCheckResult.Error("GitHub-Repository für Updates ist nicht konfiguriert.")
        }

        val connection = (URL("https://api.github.com/repos/$repository/releases/latest").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "InvestmentRadar/${BuildConfig.VERSION_NAME}")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext UpdateCheckResult.Error("GitHub antwortet mit HTTP $responseCode.")
            }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)
            val latestVersion = json.optString("tag_name").removePrefix("v").trim()
            if (latestVersion.isBlank()) {
                return@withContext UpdateCheckResult.Error("Die neueste Release-Version konnte nicht gelesen werden.")
            }
            if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                return@withContext UpdateCheckResult.Current(BuildConfig.VERSION_NAME)
            }

            val assets = json.optJSONArray("assets")
                ?: return@withContext UpdateCheckResult.Error("Das Release enthält keine Update-Dateien.")
            var apkUrl = ""
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") == RELEASE_ASSET_NAME) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isBlank()) {
                return@withContext UpdateCheckResult.Error("Die Update-APK '$RELEASE_ASSET_NAME' fehlt im neuesten Release.")
            }

            UpdateCheckResult.Available(
                AppUpdateInfo(
                    versionName = latestVersion,
                    notes = json.optString("body").trim(),
                    apkUrl = apkUrl
                )
            )
        } catch (error: Exception) {
            UpdateCheckResult.Error(error.message ?: "Verbindung zu GitHub fehlgeschlagen.")
        } finally {
            connection.disconnect()
        }
    }

    fun openUpdate(context: Context, update: AppUpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            continueAfterInstallPermission(context, update)
            return
        }

        startDownload(context, update)
    }

    private fun continueAfterInstallPermission(context: Context, update: AppUpdateInfo) {
        val application = context.applicationContext as? Application
        if (application == null) {
            Toast.makeText(
                context,
                "Einmal 'Aus dieser Quelle zulassen' aktivieren. Danach Update erneut drücken.",
                Toast.LENGTH_LONG
            ).show()
            openUnknownSourceSettings(context)
            return
        }

        val callback = object : Application.ActivityLifecycleCallbacks {
            private var leftInvestmentRadar = false

            override fun onActivityPaused(activity: Activity) {
                if (activity.packageName == context.packageName) {
                    leftInvestmentRadar = true
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if (!leftInvestmentRadar || activity.packageName != context.packageName) return

                application.unregisterActivityLifecycleCallbacks(this)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()) {
                    startDownload(activity, update)
                } else {
                    Toast.makeText(
                        activity,
                        "Freigabe wurde nicht aktiviert. Tippe erneut auf Update, wenn du es später versuchen möchtest.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }

        application.registerActivityLifecycleCallbacks(callback)
        Toast.makeText(
            context,
            "Einmal 'Aus dieser Quelle zulassen' aktivieren. Das Update startet automatisch, sobald du zurückkehrst.",
            Toast.LENGTH_LONG
        ).show()

        try {
            openUnknownSourceSettings(context)
        } catch (_: ActivityNotFoundException) {
            application.unregisterActivityLifecycleCallbacks(callback)
            Toast.makeText(context, "Android-Einstellung für App-Installationen konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openUnknownSourceSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun startDownload(context: Context, update: AppUpdateInfo) {
        val updatesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (updatesDir == null) {
            Toast.makeText(context, "Update-Speicher ist nicht verfügbar.", Toast.LENGTH_LONG).show()
            return
        }
        val apkFile = File(updatesDir, "investment-radar-${update.versionName}.apk")
        if (apkFile.exists()) apkFile.delete()

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Investment Radar ${update.versionName}")
            .setDescription("Update wird heruntergeladen")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                apkFile.name
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val downloadId = manager.enqueue(request)
        Toast.makeText(context, "Update ${update.versionName} wird heruntergeladen …", Toast.LENGTH_LONG).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return

                runCatching { context.unregisterReceiver(this) }

                val query = DownloadManager.Query().setFilterById(downloadId)
                manager.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) return
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(context, "Update-Download fehlgeschlagen.", Toast.LENGTH_LONG).show()
                        return
                    }
                }

                installDownloadedApk(context, apkFile)
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
    }

    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        val candidateParts = versionParts(candidate)
        val currentParts = versionParts(current)
        val max = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until max) {
            val left = candidateParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun versionParts(value: String): List<Int> = value
        .removePrefix("v")
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    private fun installDownloadedApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Toast.makeText(context, "Heruntergeladene APK wurde nicht gefunden.", Toast.LENGTH_LONG).show()
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updateprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(installIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Android-Installer konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show()
        }
    }
}
