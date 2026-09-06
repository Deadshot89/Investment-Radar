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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

object NotificationEventLedger {
    fun unseen(existing: Set<String>, candidates: List<String>): List<String> =
        candidates.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { it in existing }
            .toList()
}

object AdvisorNotificationManager {
    private const val PREFS = "investment_radar_advisor_notifications"
    private const val ADVISOR_IDS = "advisor_event_ids"
    private const val SAVINGS_IDS = "savings_execution_ids"
    private const val CHANNEL_ID = "investment_advisor"
    private const val MAX_LEDGER_IDS = 250

    fun publishAdvisorEvents(
        context: Context,
        events: List<AdvisorNotificationEvent>,
        displayNames: Map<String, String> = emptyMap()
    ) {
        if (events.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(ADVISOR_IDS, emptySet()).orEmpty()
        val unseenIds = NotificationEventLedger.unseen(existing, events.map { it.id }).toSet()
        if (unseenIds.isEmpty()) return

        val successfullyNotified = mutableListOf<String>()
        events.distinctBy { it.id }.filter { it.id in unseenIds }.forEach { event ->
            val name = displayNames[event.instrumentId]?.takeIf { it.isNotBlank() }
                ?: event.instrumentId.uppercase(Locale.GERMANY)
            val previous = event.previousSignal?.userLabel() ?: "Neue Bewertung"
            val current = event.newSignal.userLabel()
            val reason = event.reasons.firstOrNull().orEmpty()
            val body = buildString {
                append("$name: $previous → $current")
                if (reason.isNotBlank()) append(". $reason")
            }
            val alert = SignalAlert(
                id = event.id,
                itemId = event.instrumentId,
                level = event.newSignal.alertLevel(),
                title = "Depot-Empfehlung geändert",
                message = body,
                createdAt = event.analysisDay
            )
            AlertStore.add(context, alert)

            if (show(
                    context = context,
                    notificationId = event.id.hashCode(),
                    title = "${event.newSignal.emoji()} $name: $current",
                    body = body,
                    intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("openAlerts", true)
                        putExtra("openItemId", event.instrumentId)
                        putExtra("openAlertId", event.id)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            ) {
                successfullyNotified += event.id
            }
        }
        persistIds(prefs, ADVISOR_IDS, existing, successfullyNotified)
    }

    fun publishDueSavingsPlans(
        context: Context,
        executions: List<SavingsPlanExecution>,
        plans: List<SavingsPlan>
    ) {
        val pending = executions.filter { it.status == SavingsPlanExecutionStatus.PENDING }
        if (pending.isEmpty()) return
        val plansById = plans.associateBy { it.id }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(SAVINGS_IDS, emptySet()).orEmpty()
        val unseenIds = NotificationEventLedger.unseen(existing, pending.map { it.id }).toSet()
        if (unseenIds.isEmpty()) return

        val successfullyNotified = mutableListOf<String>()
        pending.distinctBy { it.id }.filter { it.id in unseenIds }.forEach { execution ->
            val plan = plansById[execution.planId]
            val name = plan?.name?.takeIf { it.isNotBlank() } ?: "Sparplan"
            val amount = String.format(Locale.GERMANY, "%.2f €", execution.amountEur)
            val body = "$name · $amount · ${execution.scheduledDate}. Bitte Ausführung bestätigen."
            if (show(
                    context = context,
                    notificationId = execution.id.hashCode(),
                    title = "💶 Sparplan fällig",
                    body = body,
                    intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("openSavingsPlans", true)
                        putExtra("openItemId", PushNavigationTarget.SAVINGS_ITEM_ID)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            ) {
                successfullyNotified += execution.id
            }
        }
        persistIds(prefs, SAVINGS_IDS, existing, successfullyNotified)
    }

    private fun show(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        intent: Intent
    ): Boolean {
        if (!canNotify(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Depot-Berater", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Handlungssignale und fällige Sparpläne"
                }
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    private fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun persistIds(
        prefs: android.content.SharedPreferences,
        key: String,
        existing: Set<String>,
        added: List<String>
    ) {
        if (added.isEmpty()) return
        val next = (existing + added).takeLast(MAX_LEDGER_IDS).toSet()
        prefs.edit().putStringSet(key, next).apply()
    }

    private fun AdvisorSignal.userLabel(): String = when (this) {
        AdvisorSignal.NACHKAUFEN -> "Nachkaufen"
        AdvisorSignal.HALTEN -> "Halten"
        AdvisorSignal.REDUZIEREN -> "Reduzieren"
        AdvisorSignal.VERKAUFEN -> "Verkaufen"
        AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> "Neue Prüfung nötig"
    }

    private fun AdvisorSignal.alertLevel(): String = when (this) {
        AdvisorSignal.NACHKAUFEN -> "BUY"
        AdvisorSignal.HALTEN -> "INFO"
        AdvisorSignal.REDUZIEREN -> "REVIEW"
        AdvisorSignal.VERKAUFEN -> "SELL"
        AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> "REVIEW"
    }

    private fun AdvisorSignal.emoji(): String = when (this) {
        AdvisorSignal.NACHKAUFEN -> "🟢"
        AdvisorSignal.HALTEN -> "🔵"
        AdvisorSignal.REDUZIEREN -> "🟠"
        AdvisorSignal.VERKAUFEN -> "🔴"
        AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> "🟡"
    }
}
