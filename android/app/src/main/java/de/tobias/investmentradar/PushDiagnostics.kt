package de.tobias.investmentradar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PushDiagnostics(
    val notificationsAllowed: Boolean,
    val firebaseConfigured: Boolean,
    val tokenAvailable: Boolean,
    val generalTopicSubscribed: Boolean,
    val holdingTopicsExpected: Int,
    val holdingTopicsSubscribed: Int,
    val lastPushAt: String? = null
) {
    val ready: Boolean
        get() = notificationsAllowed &&
            firebaseConfigured &&
            tokenAvailable &&
            generalTopicSubscribed &&
            holdingTopicsSubscribed >= holdingTopicsExpected

    val summary: String
        get() = when {
            !notificationsAllowed -> "Android-Benachrichtigungen nicht erlaubt"
            !firebaseConfigured -> "Firebase nicht konfiguriert"
            !tokenAvailable -> "Firebase-Token fehlt"
            !generalTopicSubscribed -> "Allgemeiner Alarmkanal nicht verbunden"
            holdingTopicsSubscribed < holdingTopicsExpected -> "Depot-Alarme $holdingTopicsSubscribed/$holdingTopicsExpected verbunden"
            else -> "Push bereit"
        }
}

object PushDiagnosticsStore {
    private const val PREFS = "investment_radar_push_diagnostics"
    private const val TOKEN_AVAILABLE = "token_available"
    private const val GENERAL_TOPIC = "general_topic"
    private const val HOLDING_EXPECTED = "holding_expected"
    private const val HOLDING_SUBSCRIBED = "holding_subscribed"
    private const val LAST_PUSH_AT = "last_push_at"

    fun read(context: Context): PushDiagnostics {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return PushDiagnostics(
            notificationsAllowed = notificationsAllowed,
            firebaseConfigured = FirebaseBootstrap.isConfigured(),
            tokenAvailable = prefs.getBoolean(TOKEN_AVAILABLE, false),
            generalTopicSubscribed = prefs.getBoolean(GENERAL_TOPIC, false),
            holdingTopicsExpected = prefs.getInt(HOLDING_EXPECTED, 0).coerceAtLeast(0),
            holdingTopicsSubscribed = prefs.getInt(HOLDING_SUBSCRIBED, 0).coerceAtLeast(0),
            lastPushAt = prefs.getString(LAST_PUSH_AT, null)
        )
    }

    fun refreshRegistration(context: Context, onChanged: (() -> Unit)? = null) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!FirebaseBootstrap.isConfigured()) {
            prefs.edit()
                .putBoolean(TOKEN_AVAILABLE, false)
                .putBoolean(GENERAL_TOPIC, false)
                .putInt(HOLDING_EXPECTED, 0)
                .putInt(HOLDING_SUBSCRIBED, 0)
                .apply()
            onChanged?.invoke()
            return
        }

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                prefs.edit().putBoolean(TOKEN_AVAILABLE, token.isNotBlank()).apply()
                onChanged?.invoke()
            }
            .addOnFailureListener {
                prefs.edit().putBoolean(TOKEN_AVAILABLE, false).apply()
                onChanged?.invoke()
            }

        FirebaseMessaging.getInstance().subscribeToTopic("investment-alerts")
            .addOnSuccessListener {
                prefs.edit().putBoolean(GENERAL_TOPIC, true).apply()
                onChanged?.invoke()
            }
            .addOnFailureListener {
                prefs.edit().putBoolean(GENERAL_TOPIC, false).apply()
                onChanged?.invoke()
            }

        val holdingIds = PortfolioStore.read(context).toList()
        prefs.edit()
            .putInt(HOLDING_EXPECTED, holdingIds.size)
            .putInt(HOLDING_SUBSCRIBED, 0)
            .apply()
        onChanged?.invoke()
        holdingIds.forEach { itemId ->
            FirebaseMessaging.getInstance().subscribeToTopic(MainViewModel.holdingTopic(itemId))
                .addOnSuccessListener {
                    markHoldingSubscribed(context)
                    onChanged?.invoke()
                }
                .addOnFailureListener { onChanged?.invoke() }
        }
    }

    @Synchronized
    private fun markHoldingSubscribed(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expected = prefs.getInt(HOLDING_EXPECTED, 0).coerceAtLeast(0)
        val next = (prefs.getInt(HOLDING_SUBSCRIBED, 0) + 1).coerceAtMost(expected)
        prefs.edit().putInt(HOLDING_SUBSCRIBED, next).apply()
    }

    fun markTokenAvailable(context: Context, available: Boolean = true) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(TOKEN_AVAILABLE, available).apply()
    }

    fun markPushReceived(context: Context, at: String = nowIso()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(LAST_PUSH_AT, at).apply()
    }

    fun showLocalTestNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "investment_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Investment-Alarme", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Kauf-, Verkaufs-, Prüf- und Schwellenwertsignale"
                }
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("openAlerts", true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            20903,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("🔵 Investment Radar: Test")
            .setContentText("Lokale Benachrichtigungen funktionieren auf diesem Gerät.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Lokale Benachrichtigungen funktionieren auf diesem Gerät."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(20903, notification)
    }

    private fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
}
