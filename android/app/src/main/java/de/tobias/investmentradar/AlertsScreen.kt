package de.tobias.investmentradar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun AlertsScreen(
    alerts: List<StoredAlert>,
    preferences: AlertPreferences,
    onOpen: (StoredAlert) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onPreferencesChange: (AlertPreferences) -> Unit
) {
    var filterName by rememberSaveable { mutableStateOf(AlertFilter.ALL.name) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val filter = AlertFilter.entries.firstOrNull { it.name == filterName } ?: AlertFilter.ALL
    val visible = alerts.filter { stored -> filter.matches(stored.alert.level) }
    val unread = alerts.count { !it.isRead }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ALARME", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text("Alarmcenter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    if (unread == 1) "1 neuer Alarm" else "$unread neue Alarme",
                    color = if (unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AlertFilter.entries) { candidate ->
                        FilterChip(
                            selected = filter == candidate,
                            onClick = { filterName = candidate.name },
                            label = { Text(candidate.label) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onMarkAllRead, enabled = unread > 0, modifier = Modifier.weight(1f)) { Text("Alle gelesen") }
                    TextButton(onClick = { showSettings = true }, modifier = Modifier.weight(1f)) { Text("Alarmeinstellungen") }
                }
                if (alerts.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("Alarmverlauf leeren") }
                }
            }
        }

        if (visible.isEmpty()) {
            item { Text("Keine Alarme in diesem Filter.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        items(visible, key = { it.alert.id }) { stored ->
            AlertCard(stored = stored, onOpen = onOpen, onDelete = onDelete)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Alarmcenter leeren?") },
            text = { Text("Die aktuell gespeicherten Alarme werden lokal gelöscht. Neue Signale können später wieder erscheinen.") },
            confirmButton = {
                Button(onClick = { confirmClear = false; onClear() }) { Text("Alle löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") } }
        )
    }

    if (showSettings) {
        AlertPreferencesDialog(
            initial = preferences,
            onDismiss = { showSettings = false },
            onSave = { value -> onPreferencesChange(value); showSettings = false }
        )
    }
}

@Composable
private fun AlertCard(
    stored: StoredAlert,
    onOpen: (StoredAlert) -> Unit,
    onDelete: (String) -> Unit
) {
    val alert = stored.alert
    val accent = alertAccentColor(alert)
    val shape = RoundedCornerShape(18.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = if (stored.isRead) 0.26f else 0.62f), shape)
            .clickable { onOpen(stored) },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (stored.isRead) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(9.dp))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        alertBadgeLabel(alert),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = accent
                    )
                }
                if (!stored.isRead) {
                    Text("NEU", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = { onDelete(alert.id) }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Alarm löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                alert.title.ifBlank { alert.level },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )

            Text(
                formatAlertTimestamp(alert.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = accent.copy(alpha = 0.18f))

            Text("Warum der Radar reagiert", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
            Text(alert.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun alertBadgeLabel(alert: SignalAlert): String {
    val combined = "${alert.title} ${alert.message}".uppercase(Locale.GERMANY)
    if ("PROGNOSE" in combined || "FORECAST" in combined) return "PROGNOSE"
    return when (alert.level.trim().uppercase(Locale.GERMANY)) {
        "BUY" -> "KAUFCHANCE"
        "REVIEW" -> "PRÜFEN"
        "SELL" -> "VERKAUF"
        "THRESHOLD" -> "SCHWELLE"
        else -> "INFO"
    }
}

private fun alertAccentColor(alert: SignalAlert): Color {
    val combined = "${alert.title} ${alert.message}".uppercase(Locale.GERMANY)
    if ("PROGNOSE" in combined || "FORECAST" in combined) return Color(0xFF9F7BFF)
    return when (alert.level.trim().uppercase(Locale.GERMANY)) {
        "BUY" -> Color(0xFF2EE59D)
        "REVIEW" -> Color(0xFFFFC857)
        "SELL" -> Color(0xFFFF6577)
        "THRESHOLD" -> Color(0xFFFF8A65)
        else -> Color(0xFF4C8DFF)
    }
}

private fun formatAlertTimestamp(raw: String): String {
    if (raw.isBlank()) return "Zeitpunkt unbekannt"
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX")
    val parsed = patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)
        }.getOrNull()
    } ?: return raw
    return SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY).format(parsed)
}

@Composable
private fun AlertPreferencesDialog(
    initial: AlertPreferences,
    onDismiss: () -> Unit,
    onSave: (AlertPreferences) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    var dropText by remember(initial.localDailyDropThresholdPct) {
        mutableStateOf(initial.localDailyDropThresholdPct?.toString().orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alarmeinstellungen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertToggle("Kaufchancen", value.buyEnabled) { value = value.copy(buyEnabled = it) }
                AlertToggle("Prüfsignale", value.reviewEnabled) { value = value.copy(reviewEnabled = it) }
                AlertToggle("Verkauf / manuell prüfen", value.sellEnabled) { value = value.copy(sellEnabled = it) }
                AlertToggle("Schwellenwerte", value.thresholdEnabled) { value = value.copy(thresholdEnabled = it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = value.minimumSeverity.equals("NORMAL", true),
                        onClick = { value = value.copy(minimumSeverity = "NORMAL") },
                        label = { Text("Normal") }
                    )
                    FilterChip(
                        selected = value.minimumSeverity.equals("ALL", true),
                        onClick = { value = value.copy(minimumSeverity = "ALL") },
                        label = { Text("Alle") }
                    )
                }
                OutlinedTextField(
                    value = dropText,
                    onValueChange = { dropText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                    label = { Text("Eigener Tagesverlust-Schwellwert %") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val threshold = dropText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
                onSave(value.copy(localDailyDropThresholdPct = threshold))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun AlertToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
