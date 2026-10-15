package de.tobias.investmentradar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class DepotScreenshotImportUiActions(
    val onPickImages: () -> Unit = {},
    val onAnalyze: () -> Unit = {},
    val onCorrectValues: (String, Double, Double?, Double?) -> Unit = { _, _, _, _ -> },
    val onAssignMatch: (String, String) -> Unit = { _, _ -> },
    val onConfirmReviewed: (String) -> Unit = {},
    val onConfirmKnownNew: (String) -> Unit = {},
    val onConfirmCustomNew: (String, CustomAssetDraft) -> Unit = { _, _ -> },
    val onConfirmReactivation: (String) -> Unit = {},
    val onChooseMissing: (String, MissingPositionDecision) -> Unit = { _, _ -> },
    val onRetryFailedImage: (String) -> Unit = {},
    val onApply: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onClose: () -> Unit = {}
)

@Composable
fun DepotScreenshotImportScreen(
    coordinator: DepotScreenshotImportViewModel,
    items: List<InvestmentItem>,
    customItems: List<CustomInvestment>,
    onClose: () -> Unit
) {
    val state by coordinator.state.collectAsState()
    val scope = rememberCoroutineScope()
    val itemNames = remember(items, customItems) {
        buildMap {
            items.forEach { put(it.id, it.name) }
            customItems.forEach { put(it.id, it.name) }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        coordinator.selectImages(
            uris.mapIndexed { index, uri ->
                ScreenshotImageRef(sourceId = "${uri}#$index", uriString = uri.toString())
            }
        )
    }

    DepotScreenshotImportContent(
        state = state,
        itemNames = itemNames,
        actions = DepotScreenshotImportUiActions(
            onPickImages = { picker.launch(arrayOf("image/*")) },
            onAnalyze = { scope.launch { coordinator.analyze() } },
            onCorrectValues = coordinator::correctValues,
            onAssignMatch = coordinator::assignMatch,
            onConfirmReviewed = coordinator::confirmReviewedRow,
            onConfirmKnownNew = coordinator::confirmKnownNewPosition,
            onConfirmCustomNew = coordinator::confirmCustomNewPosition,
            onConfirmReactivation = coordinator::confirmReactivation,
            onChooseMissing = coordinator::chooseMissingPosition,
            onRetryFailedImage = { sourceId -> scope.launch { coordinator.retryFailedImage(sourceId) } },
            onApply = coordinator::apply,
            onCancel = coordinator::cancel,
            onClose = {
                coordinator.cancel()
                onClose()
            }
        )
    )
}

@Composable
fun DepotScreenshotImportContent(
    state: DepotScreenshotImportState,
    itemNames: Map<String, String>,
    actions: DepotScreenshotImportUiActions
) {
    var correctionRow by remember { mutableStateOf<DepotScreenshotImportReviewRow?>(null) }
    var deleteRow by remember { mutableStateOf<DepotScreenshotImportReviewRow?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ImportCard("Depot aktualisieren") {
                Text(
                    "Trade-Republic-Screenshots werden ausschließlich lokal auf diesem Gerät ausgewertet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = actions.onClose) { Text("Schließen") }
            }
        }

        when (state) {
            is DepotScreenshotImportState.Selecting -> item {
                ImportCard("1 von 4 · Screenshots auswählen") {
                    val count = state.images.size
                    Text(if (count == 1) "1 Screenshot ausgewählt" else "$count Screenshots ausgewählt")
                    Text(
                        "Wähle alle Screenshots aus, die zusammen dein aktuelles Trade-Republic-Depot abdecken.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = actions.onPickImages, modifier = Modifier.fillMaxWidth()) {
                        Text(if (count == 0) "Screenshots auswählen" else "Auswahl ändern")
                    }
                    OutlinedButton(
                        onClick = actions.onAnalyze,
                        enabled = count > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Screenshots analysieren") }
                }
            }

            is DepotScreenshotImportState.Recognizing -> item {
                ImportCard("2 von 4 · Lokal erkennen") {
                    Text("${state.completed} von ${state.total} Screenshots analysiert")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text(
                            "Texterkennung läuft lokal. Bilder und OCR-Rohtext werden nicht hochgeladen.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            is DepotScreenshotImportState.Reviewing -> {
                item {
                    ImportCard("3 von 4 · Prüfen & korrigieren") {
                        Text("${state.images.size} Screenshots · ${state.rows.size} Depotzeilen")
                        Text(
                            "Erst nach deiner Bestätigung werden Änderungen gespeichert. Fehlende Positionen werden niemals automatisch verkauft oder gelöscht.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (state.failedImages.isNotEmpty()) {
                    item {
                        ImportCard("Nicht lesbare Screenshots") {
                            state.failedImages.forEach { failed ->
                                Text(failed.errorMessage ?: "Screenshot konnte nicht gelesen werden")
                                OutlinedButton(onClick = { actions.onRetryFailedImage(failed.image.sourceId) }) {
                                    Text("Erneut versuchen")
                                }
                            }
                        }
                    }
                }

                items(state.rows, key = { it.key }) { row ->
                    ReviewRow(
                        row = row,
                        itemNames = itemNames,
                        actions = actions,
                        onCorrect = { correctionRow = row },
                        onDeleteRequested = { deleteRow = row }
                    )
                }

                item {
                    ImportCard("Vorschau") {
                        ImportSummaryText(state.summary)
                        Button(
                            onClick = actions.onApply,
                            enabled = state.canApply,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Änderungen übernehmen") }
                        if (!state.canApply) {
                            Text(
                                "Alle Konflikte, neuen Werte und fehlenden Positionen müssen zuerst eindeutig bestätigt werden.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            is DepotScreenshotImportState.Applying -> item {
                ImportCard("4 von 4 · Änderungen speichern") {
                    CircularProgressIndicator()
                    ImportSummaryText(state.summary)
                    Text("Der Depotstand wird atomar gespeichert.")
                }
            }

            is DepotScreenshotImportState.Completed -> item {
                ImportCard("4 von 4 · Übernommen") {
                    ImportSummaryText(state.summary)
                    Text(
                        "Depot erfolgreich aktualisiert. Der Screenshot-Import hat keine Kauf-, Verkaufs- oder Budgetbuchung erzeugt.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = actions.onClose, modifier = Modifier.fillMaxWidth()) {
                        Text("Zurück zu Mein Depot")
                    }
                }
            }

            is DepotScreenshotImportState.Failed -> item {
                ImportCard("Import nicht übernommen") {
                    Text(state.message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text(
                        "Es wurden keine unvollständigen Änderungen übernommen.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = actions.onCancel, modifier = Modifier.fillMaxWidth()) { Text("Neu starten") }
                }
            }
        }
    }

    correctionRow?.let { row ->
        ValueCorrectionDialog(
            row = row,
            onDismiss = { correctionRow = null },
            onConfirm = { value, costBasis, shares ->
                actions.onCorrectValues(row.key, value, costBasis, shares)
                correctionRow = null
            }
        )
    }

    deleteRow?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteRow = null },
            title = { Text("Position endgültig löschen?") },
            text = {
                Text("Die Position inklusive gespeicherter Kauf-/Verkaufshistorie und zugehöriger Depotdaten wird unwiderruflich entfernt.")
            },
            confirmButton = {
                Button(onClick = {
                    row.itemId?.let { actions.onChooseMissing(it, MissingPositionDecision.DELETE) }
                    deleteRow = null
                }) { Text("Endgültig löschen") }
            },
            dismissButton = { TextButton(onClick = { deleteRow = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun ReviewRow(
    row: DepotScreenshotImportReviewRow,
    itemNames: Map<String, String>,
    actions: DepotScreenshotImportUiActions,
    onCorrect: () -> Unit,
    onDeleteRequested: () -> Unit
) {
    var ticker by remember(row.key) { mutableStateOf("") }
    var isin by remember(row.key) { mutableStateOf("") }
    var type by remember(row.key) { mutableStateOf("Aktie") }
    val title = row.observation?.name ?: row.itemId?.let(itemNames::get) ?: row.itemId ?: "Unbekannte Position"

    ImportCard(title) {
        Text(reviewStatusLabel(row), fontWeight = FontWeight.Bold, color = reviewStatusColor(row))
        row.reason?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        row.observation?.let { observation ->
            Text("Erkannter Depotwert: ${observation.valueEur?.let(::formatImportMoney) ?: "–"}")
            observation.derivedCostBasisEur?.let { Text("Abgeleiteter Einstand: ${formatImportMoney(it)}") }
            observation.shares?.let { Text("Erkannte Anteile: ${formatImportNumber(it)}") }
            OutlinedButton(onClick = onCorrect, modifier = Modifier.fillMaxWidth()) {
                Text("Erkannte Werte korrigieren")
            }
        }

        if (row.candidateItemIds.isNotEmpty() && !row.resolved) {
            Text("Zuordnung auswählen", fontWeight = FontWeight.Bold)
            row.candidateItemIds.distinct().forEach { candidateId ->
                OutlinedButton(
                    onClick = { actions.onAssignMatch(row.key, candidateId) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(itemNames[candidateId] ?: candidateId) }
            }
        }

        when (row.kind) {
            DepotScreenshotImportRowKind.UPDATE -> if (!row.resolved && row.itemId != null && row.observation != null) {
                Button(onClick = { actions.onConfirmReviewed(row.key) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Erkannte Werte bestätigen")
                }
            }

            DepotScreenshotImportRowKind.ADD -> {
                if (!row.resolved && row.itemId != null && itemNames.containsKey(row.itemId)) {
                    Button(onClick = { actions.onConfirmKnownNew(row.key) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Neue Position bestätigen")
                    }
                }
                if (!row.resolved && row.observation != null && (row.itemId == null || !itemNames.containsKey(row.itemId))) {
                    HorizontalDivider()
                    Text("Neuen Wert anlegen", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = ticker,
                        onValueChange = { ticker = it.take(16).uppercase() },
                        label = { Text("Ticker") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = isin,
                        onValueChange = { isin = it.take(16).uppercase() },
                        label = { Text("ISIN optional") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "Aktie", onClick = { type = "Aktie" }, label = { Text("Aktie") })
                        FilterChip(selected = type == "ETF", onClick = { type = "ETF" }, label = { Text("ETF") })
                    }
                    Button(
                        onClick = {
                            actions.onConfirmCustomNew(
                                row.key,
                                CustomAssetDraft(
                                    id = "tr-import-${UUID.randomUUID()}",
                                    name = row.observation.name,
                                    ticker = ticker.trim(),
                                    isin = isin.trim(),
                                    type = type
                                )
                            )
                        },
                        enabled = ticker.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Als neuen Wert anlegen") }
                }
            }

            DepotScreenshotImportRowKind.REACTIVATE -> if (!row.resolved) {
                Button(onClick = { actions.onConfirmReactivation(row.key) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Position reaktivieren")
                }
            }

            DepotScreenshotImportRowKind.MISSING -> {
                val itemId = row.itemId
                Text("Auf den ausgewählten Trade-Republic-Screenshots nicht gefunden.")
                OutlinedButton(
                    onClick = { itemId?.let { actions.onChooseMissing(it, MissingPositionDecision.KEEP) } },
                    enabled = itemId != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Behalten") }
                OutlinedButton(
                    onClick = { itemId?.let { actions.onChooseMissing(it, MissingPositionDecision.MARK_SOLD) } },
                    enabled = itemId != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Als verkauft markieren") }
                OutlinedButton(
                    onClick = onDeleteRequested,
                    enabled = itemId != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Löschen") }
            }
        }

        if (row.resolved) {
            Text("✓ Entscheidung bestätigt", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ValueCorrectionDialog(
    row: DepotScreenshotImportReviewRow,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double?, Double?) -> Unit
) {
    val observation = row.observation ?: return
    var valueText by remember(row.key) { mutableStateOf(observation.valueEur?.let(::formatEditableImportNumber).orEmpty()) }
    var costText by remember(row.key) { mutableStateOf(observation.derivedCostBasisEur?.let(::formatEditableImportNumber).orEmpty()) }
    var sharesText by remember(row.key) { mutableStateOf(observation.shares?.let(::formatEditableImportNumber).orEmpty()) }
    val value = parseImportNumber(valueText)
    val cost = costText.takeIf { it.isNotBlank() }?.let(::parseImportNumber)
    val shares = sharesText.takeIf { it.isNotBlank() }?.let(::parseImportNumber)
    val valid = value != null && value >= 0.0 &&
        (costText.isBlank() || (cost != null && cost >= 0.0)) &&
        (sharesText.isBlank() || (shares != null && shares > 0.0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Erkannte Werte korrigieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(valueText, { valueText = it }, label = { Text("Depotwert in €") }, singleLine = true)
                OutlinedTextField(costText, { costText = it }, label = { Text("Einstandswert optional") }, singleLine = true)
                OutlinedTextField(sharesText, { sharesText = it }, label = { Text("Anteile optional") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { value?.let { onConfirm(it, cost, shares) } }) {
                Text("Korrektur übernehmen")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun ImportCard(title: String, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun ImportSummaryText(summary: PortfolioImportSummary) {
    Text("${summary.updatedCount} aktualisiert · ${summary.addedCount} neu · ${summary.soldCount} verkauft · ${summary.deletedCount} gelöscht · ${summary.keptCount} behalten")
    if (summary.reactivatedCount > 0) Text("${summary.reactivatedCount} reaktiviert")
}

@Composable
private fun reviewStatusColor(row: DepotScreenshotImportReviewRow) = when {
    row.resolved -> MaterialTheme.colorScheme.primary
    row.status == ScreenshotReviewStatus.CONFLICT || row.status == ScreenshotReviewStatus.INVALID -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

private fun reviewStatusLabel(row: DepotScreenshotImportReviewRow): String = when {
    row.resolved -> "✓ Bestätigt"
    row.status == ScreenshotReviewStatus.CONFLICT -> "Konflikt · bitte prüfen"
    row.status == ScreenshotReviewStatus.INVALID -> "Ungültiger Wert · bitte prüfen"
    row.status == ScreenshotReviewStatus.NOT_FOUND -> "Nicht gefunden · Entscheidung erforderlich"
    row.status == ScreenshotReviewStatus.SOLD_REAPPEARED -> "Verkaufte Position wieder erkannt"
    row.status == ScreenshotReviewStatus.NEW_POSITION -> "Neue Position · bestätigen"
    row.status == ScreenshotReviewStatus.NEEDS_REVIEW -> "Zuordnung prüfen"
    else -> "Erkannt"
}

private fun formatImportMoney(value: Double): String = String.format(Locale.GERMANY, "%.2f €", value)
private fun formatImportNumber(value: Double): String = String.format(Locale.GERMANY, "%.4f", value).trimEnd('0').trimEnd(',')
private fun formatEditableImportNumber(value: Double): String = value.toString().replace('.', ',')
private fun parseImportNumber(raw: String): Double? {
    val value = raw.trim().replace("€", "").replace(" ", "")
    if (value.isBlank()) return null
    val normalized = if (value.contains(',')) value.replace(".", "").replace(',', '.') else value
    return normalized.toDoubleOrNull()
}
