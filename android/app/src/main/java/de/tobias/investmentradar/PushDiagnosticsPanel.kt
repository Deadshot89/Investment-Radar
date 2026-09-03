package de.tobias.investmentradar

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PushDiagnosticsPanel() {
    val context = LocalContext.current
    var diagnostics by remember { mutableStateOf(PushDiagnosticsStore.read(context)) }

    fun refresh() {
        diagnostics = PushDiagnosticsStore.read(context)
        PushDiagnosticsStore.refreshRegistration(context) {
            diagnostics = PushDiagnosticsStore.read(context)
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PUSH-DIAGNOSE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = if (diagnostics.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text(diagnostics.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            DiagnosticRow("Benachrichtigungen", if (diagnostics.notificationsAllowed) "Erlaubt" else "Blockiert")
            DiagnosticRow("Firebase", if (diagnostics.firebaseConfigured) "Konfiguriert" else "Fehlt")
            DiagnosticRow("Firebase-Token", if (diagnostics.tokenAvailable) "Vorhanden" else "Fehlt")
            DiagnosticRow("Alarmkanal", if (diagnostics.generalTopicSubscribed) "Verbunden" else "Nicht verbunden")
            DiagnosticRow("Depot-Alarme", "${diagnostics.holdingTopicsSubscribed}/${diagnostics.holdingTopicsExpected} verbunden")
            DiagnosticRow("Letzter Push", diagnostics.lastPushAt?.replace('T', ' ')?.replace("Z", " UTC") ?: "Noch keiner empfangen")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) { Text("Status prüfen") }
                Button(
                    onClick = { PushDiagnosticsStore.showLocalTestNotification(context) },
                    enabled = diagnostics.notificationsAllowed,
                    modifier = Modifier.weight(1f)
                ) { Text("Test anzeigen") }
            }
            if (!diagnostics.notificationsAllowed) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Benachrichtigungen freigeben") }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
