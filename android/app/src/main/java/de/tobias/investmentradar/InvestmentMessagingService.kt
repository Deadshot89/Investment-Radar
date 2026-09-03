package de.tobias.investmentradar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InvestmentMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "Investment Radar"
        val body = data["message"] ?: message.notification?.body ?: "Neue Marktinformation"
        val level = data["level"] ?: "INFO"
        val id = data["alertId"] ?: "${System.currentTimeMillis()}"
        val itemId = data["itemId"] ?: ""
        val createdAt = data["createdAt"] ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
        val alert = SignalAlert(id, itemId, level, title, body, createdAt)
        val preferences = AlertPreferencesStore.read(this)

        if (AlertPolicy.shouldStore(alert, preferences)) {
            AlertStore.add(this, alert)
        }
        if (AlertPolicy.shouldNotify(alert, preferences)) {
            showNotification(title, body, level, id.hashCode(), itemId, id)
        }
    }

    private fun showNotification(title: String, body: String, level: String, id: Int, itemId: String, alertId: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = "investment_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Investment-Alarme", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Kauf-, Verkaufs-, Prüf- und Schwellenwertsignale"
                }
            )
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("openAlerts", true)
            if (itemId.isNotBlank()) putExtra("openItemId", itemId)
            putExtra("openAlertId", alertId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("${levelEmoji(level)} $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(id, notification)
    }

    private fun levelEmoji(level: String) = when (level.uppercase()) {
        "SELL" -> "🔴"
        "REVIEW" -> "🟠"
        "BUY" -> "🟢"
        "THRESHOLD" -> "🟡"
        else -> "🔵"
    }
}
