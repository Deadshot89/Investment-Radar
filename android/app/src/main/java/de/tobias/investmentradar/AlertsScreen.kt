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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun AlertsScreen(
    alerts: List<StoredAlert>,
    preferences: AlertPreferences,
    actionPlan: ActionPlan = ActionPlan("", "", 0.0, 0.0, emptyList()),
    advisorById: Map<String, PortfolioAdvisorCandidate> = emptyMap(),
    itemsById: Map<String, InvestmentItem> = emptyMap(),
    positions: Map<String, PortfolioPosition> = emptyMap(),
    onExecuteAction: (DepotActionCenterItem) -> Unit = {},
    onOpen: (StoredAlert) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onPreferencesChange: (AlertPreferences) -> Unit
) {
    var filterName by rememberSaveable { mutableStateOf(AlertFilter.ALL.name) }
    var portfolioOnly by rememberSaveable { mutableStateOf(false) }
    var showConfirmed by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var locallyConfirmedIds by remember { mutableStateOf(emptySet<String>()) }
    val filter = AlertFilter.entries.firstOrNull { it.name == filterName } ?: AlertFilter.ALL
    val context = LocalContext.current
    val effectiveAlerts = alerts.map { stored ->
        if (stored.alert.id in locallyConfirmedIds) stored.copy(isRead = true, isConfirmed = true) else stored
    }
    val storedAdvisorPlan = PortfolioAdvisorStore.latest(context)
    val resolvedAdvisorById = if (advisorById.isNotEmpty()) advisorById else storedAdvisorPlan?.plan?.candidates.orEmpty().associateBy { it.itemId }
    val resolvedActionPlan = when {
        actionPlan.actions.isNotEmpty() -> actionPlan
        storedAdvisorPlan != null -> ActionPlanEngine.build(storedAdvisorPlan.analysisDay, storedAdvisorPlan.plan)
        else -> actionPlan
    }
    val resolvedPositions = if (positions.isNotEmpty()) positions else PortfolioStore.readPositions(context)
    val depotActionCenter = DepotActionCenterMapper.build(
        resolvedActionPlan,
        resolvedAdvisorById,
        itemsById,
        resolvedPositions
    )
    val holdingIds = resolvedPositions.keys
    val filtered = AlertCenterState.visible(
        items = effectiveAlerts,
        filter = filter,
        holdingIds = holdingIds,
        portfolioOnly = portfolioOnly,
        includeConfirmed = showConfirmed
    )
    val visible = if (showConfirmed) filtered.filter { it.isConfirmed } else filtered
    val unread = effectiveAlerts.count { !it.isRead && !it.isConfirmed }
    val open = effectiveAlerts.count { !it.isConfirmed }
    val confirmed = effectiveAlerts.count { it.isConfirmed }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ALARME", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text("Alarmcenter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    "$open offen · $confirmed bestätigt${if (unread > 0) " · $unread neu" else ""}",
                    color = if (open > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (open > 0) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    "Jeder Alarm erklärt dir jetzt den Grund, die Bedeutung und den konkreten nächsten Schritt. Bestätigte Alarme bleiben im Verlauf erhalten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AlertFilter.entries) { candidate -> FilterChip(selected = filter == candidate, onClick = { filterName = candidate.name }, label = { Text(candidate.label) }) }
                    item {
                        FilterChip(
                            selected = portfolioOnly,
                            onClick = { portfolioOnly = !portfolioOnly },
                            label = { Text("Nur Depot") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = showConfirmed,
                            onClick = { showConfirmed = !showConfirmed },
                            label = { Text(if (showConfirmed) "Bestätigt" else "Offen") }
                        )
                    }
                }
                if (portfolioOnly) {
                    Text(
                        "Depot-Alarme zuerst: Verkauf, Schwellenwert und Prüfsignale werden nach Handlungsbedarf priorisiert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onMarkAllRead, enabled = unread > 0, modifier = Modifier.weight(1f)) { Text("Alle gelesen") }
                    TextButton(onClick = { showSettings = true }, modifier = Modifier.weight(1f)) { Text("Alarmeinstellungen") }
                }
                PushDiagnosticsPanel()
                if (effectiveAlerts.isNotEmpty()) TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("Alarmverlauf leeren") }
            }
        }
        if (!showConfirmed) {
            item {
                DepotActionCenterSection(
                    state = depotActionCenter,
                    onExecuteAction = onExecuteAction
                )
            }
        }
        if (visible.isEmpty()) item {
            Text(
                when {
                    showConfirmed -> "Keine bestätigten Alarme in diesem Filter."
                    portfolioOnly -> "Keine passenden offenen Alarme für dein Depot."
                    else -> "Keine offenen Alarme in diesem Filter."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(visible, key = { it.alert.id }) { stored ->
            val itemId = stored.alert.itemId
            AlertCard(
                stored = stored,
                isHolding = itemId.isNotBlank() && itemId in holdingIds,
                actionPlan = resolvedActionPlan,
                advisorCandidate = resolvedAdvisorById[itemId],
                item = itemsById[itemId],
                position = resolvedPositions[itemId],
                onOpen = onOpen,
                onConfirm = { alertId ->
                    AlertStore.confirm(context, alertId)
                    locallyConfirmedIds = locallyConfirmedIds + alertId
                },
                onDelete = onDelete
            )
        }
    }

    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Alarmcenter leeren?") }, text = { Text("Die aktuell gespeicherten Alarme werden lokal gelöscht. Neue Signale können später wieder erscheinen.") }, confirmButton = { Button(onClick = { confirmClear = false; onClear() }) { Text("Alle löschen") } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") } })
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Alarmeinstellungen") }, text = { AlertPreferencesEditor(initial = preferences, onSave = { value -> onPreferencesChange(value); showSettings = false }) }, confirmButton = {}, dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Abbrechen") } })
}

@Composable
private fun DepotActionCenterSection(
    state: DepotActionCenterState,
    onExecuteAction: (DepotActionCenterItem) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Das solltest du jetzt mit deinem Depot machen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Eine gemeinsame Arbeitsliste aus deinem aktuellen Aktionsplan. Keine Order wird automatisch ausgeführt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Dringende Aktionen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.summary.urgentActions.toString(), fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text("Geplantes Kaufbudget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatEur(state.summary.plannedBuyEur), fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text("Cash halten", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatEur(state.summary.cashEur), fontWeight = FontWeight.Black)
                }
            }
            HorizontalDivider(color = accent.copy(alpha = 0.18f))
            if (state.isEmpty) {
                Text("Noch kein belastbarer Depot-Aktionsplan verfügbar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.items.forEach { item ->
                    DepotActionCenterCard(item = item, onExecuteAction = onExecuteAction)
                }
            }
        }
    }
}

@Composable
private fun DepotActionCenterCard(
    item: DepotActionCenterItem,
    onExecuteAction: (DepotActionCenterItem) -> Unit
) {
    val accent = when (item.type) {
        ActionType.SELL -> Color(0xFFFF6577)
        ActionType.REDUCE, ActionType.REVIEW_SAVINGS_PLAN -> Color(0xFFFFC857)
        ActionType.BUY_MORE, ActionType.OPEN_POSITION -> Color(0xFF2EE59D)
        ActionType.KEEP_SAVINGS_PLAN -> Color(0xFF4C8DFF)
        ActionType.HOLD_CASH -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(accent.copy(alpha = 0.07f), RoundedCornerShape(14.dp)).border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(item.instrumentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text(if (item.isHolding) "IM DEPOT" else if (item.type == ActionType.OPEN_POSITION) "NEUE POSITION" else "", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent)
        }
        Text(item.actionText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = if (item.buyBlocked) Color(0xFFFFC857) else MaterialTheme.colorScheme.onSurface)
        if (item.buyBlocked) {
            Text("WATCH · NICHT BESTÄTIGT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFFFFC857))
        }
        if (item.reason.isNotBlank()) Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MetricRow("Depotwert", item.depotValueEur?.let(::formatEur) ?: "–")
            MetricRow("Einstand / G/V", if (item.costBasisEur != null) "${formatEur(item.costBasisEur)} / ${formatSignedEur(item.profitLossEur)}" else "–")
            MetricRow("Score", item.score?.toString() ?: "–")
            MetricRow("Risiko", item.riskScore?.let { "$it/5" } ?: "–")
            MetricRow("Datenabdeckung", item.coveragePct?.let { "$it %" } ?: "–")
            MetricRow("Prognose", item.forecastDirection ?: "–")
            MetricRow("Datenqualität", item.dataQualityLabel)
        }
        if (item.executable) {
            Button(onClick = { onExecuteAction(item) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (item.type) {
                        ActionType.BUY_MORE, ActionType.OPEN_POSITION -> "Buchung öffnen"
                        ActionType.SELL, ActionType.REDUCE -> "Verkauf erfassen"
                        ActionType.REVIEW_SAVINGS_PLAN, ActionType.KEEP_SAVINGS_PLAN -> "Sparplan öffnen"
                        ActionType.HOLD_CASH -> ""
                    }
                )
            }
        }
    }
}

private data class AlertActionGuidance(
    val status: String,
    val action: String
)

private data class AlertDataQuality(
    val label: String,
    val detail: String,
    val blocksBuyDecision: Boolean
)

@Composable
private fun AlertCard(
    stored: StoredAlert,
    isHolding: Boolean,
    actionPlan: ActionPlan,
    advisorCandidate: PortfolioAdvisorCandidate?,
    item: InvestmentItem?,
    position: PortfolioPosition?,
    onOpen: (StoredAlert) -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val alert = stored.alert
    val accent = alertAccentColor(alert)
    val shape = RoundedCornerShape(18.dp)
    val isForecast = alertBadgeLabel(alert) == "PROGNOSE"
    val dataQuality = alertDataQuality(alert, advisorCandidate)
    val guidance = alertActionGuidance(alert, advisorCandidate)
    val concretePlan = plannedActionForAlert(alert, actionPlan, dataQuality)
    val nextStep = concretePlan ?: guidance.action
    val importance = alertImportance(alert, isHolding, dataQuality)
    val reviewCondition = alertReviewCondition(alert, dataQuality)
    Card(modifier = Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = if (stored.isConfirmed) 0.18f else if (stored.isRead) 0.26f else 0.62f), shape).clickable { onOpen(stored) }, shape = shape, colors = CardDefaults.cardColors(containerColor = if (stored.isConfirmed || stored.isRead) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.background(accent.copy(alpha = 0.16f), RoundedCornerShape(9.dp)).border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(9.dp)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                    Text(alertBadgeLabel(alert), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = accent)
                }
                if (isHolding) Text("IM DEPOT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                if (stored.isConfirmed) Text("BESTÄTIGT", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                else if (!stored.isRead) Text("NEU", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                Box(Modifier.weight(1f))
                IconButton(onClick = { onDelete(alert.id) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Alarm löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(alert.title.ifBlank { alert.level }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(formatAlertTimestamp(alert.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isForecast) ForecastAlertSummary(alert, accent)
            HorizontalDivider(color = accent.copy(alpha = 0.18f))

            Text("1. Was ist passiert?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = accent)
            Text(if (isForecast) forecastReason(alert.message) else alert.message.ifBlank { "Der Radar hat eine relevante Veränderung erkannt." }, style = MaterialTheme.typography.bodyMedium)

            Text("2. Warum ist das wichtig?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = accent)
            Text(importance, style = MaterialTheme.typography.bodyMedium)

            Text("3. Was sollst du jetzt tun?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = accent)
            Column(modifier = Modifier.fillMaxWidth().background(accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(guidance.status, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = accent)
                Text(nextStep, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }

            Text("4. Wann wieder prüfen?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = accent)
            Text(reviewCondition, style = MaterialTheme.typography.bodyMedium)

            AlertDecisionMetrics(advisorCandidate, item, position)
            Text("Datenqualität", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dataQuality.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = if (dataQuality.blocksBuyDecision) Color(0xFFFFC857) else MaterialTheme.colorScheme.primary)
                Text(dataQuality.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 10.dp))
            }
            HorizontalDivider(color = accent.copy(alpha = 0.18f))
            if (stored.isConfirmed) {
                Text("Dieser Alarm wurde von dir bestätigt und ist erledigt. Er bleibt zur Nachverfolgung im Verlauf sichtbar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Button(onClick = { onConfirm(alert.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Verstanden · als erledigt bestätigen")
                }
            }
        }
    }
}

@Composable
private fun AlertDecisionMetrics(candidate: PortfolioAdvisorCandidate?, item: InvestmentItem?, position: PortfolioPosition?) {
    val currentValue = candidate?.currentValueEur
    val basis = position?.activeCostBasis?.takeIf { position.performanceCostBasisKnown }
    val totalProfitLoss = if (basis != null && currentValue != null) currentValue - basis + position.realizedProfitLoss() else null
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        MetricRow("Depotwert", currentValue?.let(::formatEur) ?: "–")
        MetricRow("Einstand / G/V", if (basis != null) "${formatEur(basis)} / ${formatSignedEur(totalProfitLoss)}" else "–")
        MetricRow("Score", candidate?.advisor?.score?.toString() ?: item?.scoreTotal?.toString() ?: "–")
        MetricRow("Risiko", candidate?.riskScore?.let { "$it/5" } ?: item?.risk?.takeIf { it > 0 }?.let { "$it/5" } ?: "–")
        MetricRow("Datenabdeckung", candidate?.coveragePct?.let { "$it %" } ?: item?.coverage?.let { "$it %" } ?: "–")
        MetricRow("Prognose", candidate?.forecastDirection ?: "–")
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

private fun plannedActionForAlert(alert: SignalAlert, actionPlan: ActionPlan, dataQuality: AlertDataQuality): String? {
    val level = alert.level.trim().uppercase(Locale.GERMANY)
    if (dataQuality.blocksBuyDecision && (level == "BUY" || isWatchCandidate(alert))) {
        return "Nicht kaufen – Datenbasis unvollständig"
    }
    val matching = actionPlan.actions.filter { it.instrumentId == alert.itemId }
    val preferred = when (level) {
        "SELL" -> listOf(ActionType.SELL, ActionType.REDUCE)
        "THRESHOLD" -> listOf(ActionType.REDUCE, ActionType.SELL)
        "REVIEW" -> listOf(ActionType.REVIEW_SAVINGS_PLAN, ActionType.REDUCE, ActionType.SELL)
        "BUY" -> listOf(ActionType.BUY_MORE, ActionType.OPEN_POSITION, ActionType.KEEP_SAVINGS_PLAN)
        else -> listOf(ActionType.REVIEW_SAVINGS_PLAN, ActionType.BUY_MORE, ActionType.OPEN_POSITION, ActionType.REDUCE, ActionType.SELL, ActionType.KEEP_SAVINGS_PLAN)
    }
    val action = preferred.firstNotNullOfOrNull { type -> matching.firstOrNull { it.type == type } } ?: matching.firstOrNull()
    return when (action?.type) {
        ActionType.BUY_MORE -> "Position um ca. ${formatEur(action.amountEur)} erhöhen"
        ActionType.OPEN_POSITION -> "Neue Position mit ca. ${formatEur(action.amountEur)} eröffnen"
        ActionType.REDUCE -> "Position um ca. ${formatEur(action.amountEur)} reduzieren"
        ActionType.SELL -> "Verkauf von ca. ${formatEur(action.amountEur)} prüfen"
        ActionType.KEEP_SAVINGS_PLAN -> "Sparplan ${formatEur(action.amountEur)} beibehalten"
        ActionType.REVIEW_SAVINGS_PLAN -> "Sparplan ${formatEur(action.amountEur)} prüfen"
        ActionType.HOLD_CASH, null -> null
    }
}

private fun alertImportance(alert: SignalAlert, isHolding: Boolean, dataQuality: AlertDataQuality): String {
    if (dataQuality.blocksBuyDecision) return "Die Daten reichen noch nicht für eine belastbare Kaufentscheidung. Eine vorschnelle Aktion soll vermieden werden."
    val holdingText = if (isHolding) "Diese Position liegt bereits in deinem Depot. " else ""
    return holdingText + when (alert.level.trim().uppercase(Locale.GERMANY)) {
        "SELL" -> "Das Signal kann dein Verlustrisiko oder die weitere Depotentwicklung direkt beeinflussen und sollte zeitnah geprüft werden."
        "THRESHOLD" -> "Ein definierter Schwellenwert wurde erreicht. Damit hat sich die Lage gegenüber deiner bisherigen Planung messbar verändert."
        "BUY" -> "Der Wert erfüllt aktuell wichtige Kaufkriterien. Vor einer Order müssen Score, Risiko, Datenqualität und dein verfügbares Budget zusammenpassen."
        "REVIEW" -> "Die Ausgangslage hat sich verändert, aber das Signal reicht noch nicht für eine automatische Kauf- oder Verkaufsaussage."
        else -> "Die Veränderung ist relevant für deine weitere Beobachtung, erfordert aber nicht zwingend eine sofortige Transaktion."
    }
}

private fun alertReviewCondition(alert: SignalAlert, dataQuality: AlertDataQuality): String {
    if (dataQuality.blocksBuyDecision) return "Erneut prüfen, sobald Kurs-, Historien- und Analysedaten wieder vollständig und aktuell sind."
    return when (alert.level.trim().uppercase(Locale.GERMANY)) {
        "SELL" -> "Nach deiner Entscheidung erneut prüfen, spätestens wenn sich Score, Risiko, Prognose oder Verkaufssignal sichtbar ändern."
        "THRESHOLD" -> "Erneut prüfen, wenn der Kurs den Schwellenwert zurückerobert oder ein neues Kauf-/Verkaufssignal entsteht."
        "BUY" -> "Vor dem Kauf noch einmal den aktuellen Kurs und das verfügbare Monatsbudget prüfen; danach bei einer deutlichen Signaländerung neu bewerten."
        "REVIEW" -> "Bei der nächsten Analyse oder sobald sich Score, Prognose, Risiko oder Datenlage deutlich ändern."
        else -> "Bei der nächsten Radar-Aktualisierung oder wenn ein konkretes Kauf-, Prüf- oder Verkaufssignal entsteht."
    }
}

private fun formatEur(value: Double): String = String.format(Locale.GERMANY, "%.0f €", value)
private fun formatSignedEur(value: Double?): String = value?.let { String.format(Locale.GERMANY, "%+.0f €", it) } ?: "–"

private fun alertActionGuidance(alert: SignalAlert, candidate: PortfolioAdvisorCandidate? = null): AlertActionGuidance {
    val level = alert.level.trim().uppercase(Locale.GERMANY)
    val quality = alertDataQuality(alert, candidate)
    if (quality.blocksBuyDecision) {
        return AlertActionGuidance(
            status = "DATEN PRÜFEN",
            action = "Datenbasis unvollständig – noch keine Entscheidung"
        )
    }
    if (isWatchCandidate(alert)) {
        return AlertActionGuidance("BEOBACHTEN", "Kaufchance beobachten")
    }
    return when (level) {
        "SELL" -> AlertActionGuidance("JETZT HANDELN", "Verkauf jetzt prüfen")
        "THRESHOLD" -> AlertActionGuidance("JETZT HANDELN", "Position und Schwellenwert prüfen")
        "BUY" -> AlertActionGuidance("KAUF PRÜFEN", "Bestätigte Kaufchance anhand Score, Risiko, Budget und Datenqualität prüfen")
        "REVIEW" -> AlertActionGuidance("BEOBACHTEN", "Analyse und Position prüfen")
        else -> AlertActionGuidance("BEOBACHTEN", "Entwicklung beobachten")
    }
}

private fun alertDataQuality(alert: SignalAlert, candidate: PortfolioAdvisorCandidate? = null): AlertDataQuality {
    val combined = "${alert.title} ${alert.message}".uppercase(Locale.GERMANY)
    val hasDataGap = listOf(
        "DATEN FEHLEN",
        "DATEN FEHLT",
        "DATENBASIS UNVOLLSTÄNDIG",
        "DATENABDECKUNG UNZUREICHEND",
        "ANALYSE VERALTET",
        "KURS FEHLT",
        "HISTORIE FEHLT",
        "FUNDAMENTALDATEN FEHLEN"
    ).any { it in combined } || candidate?.advisor?.reliable == false || (candidate?.coveragePct != null && candidate.coveragePct < 50)
    return if (hasDataGap) {
        AlertDataQuality(
            label = "UNVOLLSTÄNDIG",
            detail = "Keine Kaufentscheidung bei unvollständigen Daten",
            blocksBuyDecision = true
        )
    } else {
        AlertDataQuality(
            label = "AUSREICHEND",
            detail = "Signal anhand der vorhandenen Daten prüfbar",
            blocksBuyDecision = false
        )
    }
}

private fun isWatchCandidate(alert: SignalAlert): Boolean {
    val combined = "${alert.title} ${alert.message}".uppercase(Locale.GERMANY)
    return "WATCH" in combined || "KAUFKANDIDAT" in combined || "NOCH NICHT BESTÄTIGT" in combined || "NICHT BESTÄTIGT" in combined
}

@Composable
private fun ForecastAlertSummary(alert: SignalAlert, accent: Color) {
    val data = parseForecastAlert(alert.message)
    Column(modifier = Modifier.fillMaxWidth().background(accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("PROGNOSE · 12 MONATE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = accent)
        Text("${data.direction} · ${data.expected}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        data.previous?.let { Text("Vorher: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HorizontalDivider(color = accent.copy(alpha = 0.16f))
        ForecastScenarioRow("Pessimistisch", data.pessimistic)
        ForecastScenarioRow("Erwartet", data.expected)
        ForecastScenarioRow("Optimistisch", data.optimistic)
    }
}

@Composable
private fun ForecastScenarioRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

private data class ForecastAlertData(val direction: String, val previous: String?, val pessimistic: String, val expected: String, val optimistic: String)

private fun parseForecastAlert(message: String): ForecastAlertData {
    fun value(label: String): String? = Regex("$label:\\s*([+-]?\\d+(?:[.,]\\d+)?)\\s*%", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.let { "$it %" }
    val transition = Regex("(Aufwärts|Abwärts|Seitwärts)(?:\\s*\\(([+-]?\\d+(?:[.,]\\d+)?)\\s*%\\))?\\s*→\\s*(Aufwärts|Abwärts|Seitwärts)\\s*\\(([+-]?\\d+(?:[.,]\\d+)?)\\s*%\\)", RegexOption.IGNORE_CASE).find(message)
    val direction = when (transition?.groupValues?.getOrNull(3)?.lowercase(Locale.GERMANY)) { "aufwärts" -> "AUFWÄRTS"; "abwärts" -> "ABWÄRTS"; else -> "SEITWÄRTS" }
    val previous = transition?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { "$it %" }
    val expected = value("Erwartet") ?: transition?.groupValues?.getOrNull(4)?.let { "$it %" } ?: "–"
    return ForecastAlertData(direction = direction, previous = previous, pessimistic = value("Pessimistisch") ?: "–", expected = expected, optimistic = value("Optimistisch") ?: "–")
}

private fun forecastReason(message: String): String = message.substringAfter("Warum:", message).trim()

private fun alertBadgeLabel(alert: SignalAlert): String {
    val combined = "${alert.title} ${alert.message}".uppercase(Locale.GERMANY)
    if ("PROGNOSE" in combined || "FORECAST" in combined) return "PROGNOSE"
    if (isWatchCandidate(alert)) return "WATCH · NICHT BESTÄTIGT"
    return when (alert.level.trim().uppercase(Locale.GERMANY)) {
        "BUY" -> "KAUF BESTÄTIGT"
        "REVIEW" -> "PRÜFEN"
        "SELL" -> "VERKAUF"
        "THRESHOLD" -> "SCHWELLE"
        else -> "INFO"
    }
}

private fun alertAccentColor(alert: SignalAlert): Color {
    val combined = "${alert.title} ${alert.message}".uppercase(Locale.GERMANY)
    if ("PROGNOSE" in combined || "FORECAST" in combined) return Color(0xFF9F7BFF)
    return when (alert.level.trim().uppercase(Locale.GERMANY)) { "BUY" -> Color(0xFF2EE59D); "REVIEW" -> Color(0xFFFFC857); "SELL" -> Color(0xFFFF6577); "THRESHOLD" -> Color(0xFFFF8A65); else -> Color(0xFF4C8DFF) }
}

private fun formatAlertTimestamp(raw: String): String {
    if (raw.isBlank()) return "Zeitpunkt unbekannt"
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX")
    val parsed = patterns.firstNotNullOfOrNull { pattern -> runCatching { SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw) }.getOrNull() } ?: return raw
    return SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY).format(parsed)
}

@Composable
private fun AlertPreferencesEditor(initial: AlertPreferences, onSave: (AlertPreferences) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    var dropText by remember(initial.localDailyDropThresholdPct) { mutableStateOf(initial.localDailyDropThresholdPct?.toString().orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AlertToggle("Kaufchancen", value.buyEnabled) { value = value.copy(buyEnabled = it) }
        AlertToggle("Prüfsignale", value.reviewEnabled) { value = value.copy(reviewEnabled = it) }
        AlertToggle("Verkauf / manuell prüfen", value.sellEnabled) { value = value.copy(sellEnabled = it) }
        AlertToggle("Schwellenwerte", value.thresholdEnabled) { value = value.copy(thresholdEnabled = it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = value.minimumSeverity.equals("NORMAL", true), onClick = { value = value.copy(minimumSeverity = "NORMAL") }, label = { Text("Normal") })
            FilterChip(selected = value.minimumSeverity.equals("ALL", true), onClick = { value = value.copy(minimumSeverity = "ALL") }, label = { Text("Alle") })
        }
        OutlinedTextField(value = dropText, onValueChange = { dropText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } }, label = { Text("Eigener Tagesverlust-Schwellwert %") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { val threshold = dropText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }; onSave(value.copy(localDailyDropThresholdPct = threshold)) }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") }
    }
}

@Composable
private fun AlertToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onCheckedChange) }
}
