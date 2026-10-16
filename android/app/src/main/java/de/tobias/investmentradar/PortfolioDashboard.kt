package de.tobias.investmentradar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun PortfolioDashboard(
    vm: MainViewModel,
    positions: Map<String, PortfolioPosition>,
    customItems: List<CustomInvestment>,
    allItems: List<InvestmentItem>,
    advisorPlan: PortfolioAdvisorPlan,
    onOpenDetail: (String) -> Unit,
    onEdit: (InvestmentItem) -> Unit,
    onRemove: (String) -> Unit,
    onEditCustom: (CustomInvestment) -> Unit,
    onRemoveCustom: (String) -> Unit,
    onAddCustom: () -> Unit,
    onOpenSavingsPlans: () -> Unit,
    onUpdateDepotFromScreenshots: () -> Unit
) {
    val rows = PortfolioMetrics.rows(
        positions = positions,
        items = allItems,
        customItems = customItems
    )
    val activeRows = rows.filter { it.active }
    val soldMarkedRows = rows.filter { row ->
        positions[row.itemId]?.lifecycleStatus == PortfolioLifecycleStatus.SOLD_MARKED
    }
    val activePortfolio = PortfolioMetrics.summary(activeRows)
    val itemById = allItems.associateBy { it.id }
    val customById = customItems.associateBy { it.id }
    val advisorById = advisorPlan.byItemId
    var trackedSharesDialogItemId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "portfolio-header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mein Depot", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "Live-Werte basieren nur auf belastbaren aktuellen Kursen und erfassten Stückzahlen. Bestätigte Trade-Republic-Snapshots bleiben separat nachvollziehbar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onUpdateDepotFromScreenshots, modifier = Modifier.fillMaxWidth()) {
                    Text("Depot aktualisieren")
                }
                OutlinedButton(onClick = onOpenSavingsPlans, modifier = Modifier.fillMaxWidth()) {
                    Text("Sparpläne")
                }
                OutlinedButton(onClick = onAddCustom, modifier = Modifier.fillMaxWidth()) {
                    Text("Eigenen Wert hinzufügen")
                }
            }
        }

        item(key = "portfolio-summary") {
            PortfolioDashboardCard {
                Text("Depotübersicht", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                PortfolioDashboardValue("Depotwert", activePortfolio.currentValue?.let(::portfolioMoney) ?: "Nicht vollständig berechenbar")
                PortfolioDashboardValue("Einstand", activePortfolio.investedCostBasis.takeIf { activePortfolio.costBasisKnown }?.let(::portfolioMoney) ?: "Nicht vollständig erfasst")
                PortfolioDashboardValue("Gewinn / Verlust", activePortfolio.unrealizedProfitLoss?.let(::portfolioSignedMoney) ?: "Nicht vollständig berechenbar")
                PortfolioDashboardValue("Größte Position", activePortfolio.largestPositionWeightPct?.let(::portfolioPercent) ?: "Nicht berechenbar")
            }
        }

        if (advisorPlan.cashHoldAmount > 0 || advisorPlan.reallocationReserve > 0 || advisorPlan.savingsPlanReviewAmount > 0) {
            item(key = "advisor-summary") {
                PortfolioDashboardCard {
                    Text("Was soll ich jetzt tun?", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    if (advisorPlan.cashHoldAmount > 0) PortfolioDashboardValue("Cash halten", portfolioMoney(advisorPlan.cashHoldAmount))
                    if (advisorPlan.reallocationReserve > 0) PortfolioDashboardValue("Umschichten", portfolioMoney(advisorPlan.reallocationReserve))
                    if (advisorPlan.savingsPlanReviewAmount > 0) PortfolioDashboardValue("Sparplan prüfen", portfolioMoney(advisorPlan.savingsPlanReviewAmount))
                }
            }
        }

        items(activeRows, key = { it.itemId }) { row ->
            val canonicalItem = itemById[row.itemId]
            val item = canonicalItem ?: customById[row.itemId]?.fallbackItem()
            val custom = customById[row.itemId]
            val position = positions[row.itemId]
            val advisor = advisorById[row.itemId]
            val isFixedIncome = custom?.type == "Festzins"
            val importedSnapshot = position?.snapshotValueEur != null && position.purchases.isEmpty() && position.sales.isEmpty()
            val hasTrackedShares = position?.trackedShares?.let { it.isFinite() && it > 0.0 } == true
            val liveTrackedValue = importedSnapshot && hasTrackedShares && row.hasUsablePrice
            val currentMarketPrice = canonicalItem?.priceEur?.takeIf { it.isFinite() && it > 0.0 }
            val currentValueMissingReason = when {
                row.currentPriceEur != null -> "Stückzahl fehlt"
                else -> "Kurs fehlt"
            }

            PortfolioDashboardCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item?.name ?: row.itemId, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(item?.ticker?.takeIf { it.isNotBlank() }, item?.type?.takeIf { it.isNotBlank() }).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (advisor != null) {
                        Text(portfolioAdvisorActionLabel(advisor.action), fontWeight = FontWeight.Black)
                    }
                }
                HorizontalDivider()

                if (importedSnapshot && !isFixedIncome) {
                    position?.snapshotValueEur?.takeIf { it.isFinite() && it >= 0.0 }?.let { snapshotValue ->
                        PortfolioDashboardValue(
                            "Letzter bestätigter Trade-Republic-Wert",
                            portfolioSnapshotValue(snapshotValue, position.snapshotCapturedAt)
                        )
                    }
                    if (hasTrackedShares) {
                        PortfolioDashboardValue(
                            "Aktueller Depotwert",
                            row.currentValue?.let(::portfolioMoney) ?: currentValueMissingReason
                        )
                    } else {
                        PortfolioDashboardValue("Aktueller Depotwert", "Nicht berechenbar · $currentValueMissingReason")
                    }
                    PortfolioDashboardValue(
                        "Aktueller Marktpreis",
                        currentMarketPrice?.let(::portfolioMoney) ?: "Kurs nicht verfügbar"
                    )
                    if (!hasTrackedShares) {
                        OutlinedButton(
                            onClick = { trackedSharesDialogItemId = row.itemId },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Stückzahl ergänzen") }
                    }
                } else {
                    PortfolioDashboardValue(
                        if (isFixedIncome) "Anlagewert" else "Aktueller Wert",
                        row.currentValue?.let(::portfolioMoney) ?: if (isFixedIncome) "Anlagebetrag fehlt" else currentValueMissingReason
                    )
                    if (!isFixedIncome && !(importedSnapshot && !hasTrackedShares)) {
                        PortfolioDashboardValue("Aktueller Kurs", row.currentPriceEur?.let(::portfolioMoney) ?: "Kurs fehlt")
                    }
                }

                if (isFixedIncome) {
                    custom?.fixedMaturityValueEur?.let { PortfolioDashboardValue("Fälligkeit", portfolioMoney(it)) }
                    custom?.maturityLabel?.takeIf { it.isNotBlank() }?.let { PortfolioDashboardValue("Laufzeit", it) }
                } else {
                    PortfolioDashboardValue("Einstand", if (row.costBasisKnown) portfolioMoney(row.investedCostBasis) else "Nicht erfasst")
                    PortfolioDashboardValue("Gewinn / Verlust", row.unrealizedProfitLoss?.let(::portfolioSignedMoney) ?: "Nicht berechenbar")
                    row.unrealizedProfitLossPct?.let { PortfolioDashboardValue("Performance", portfolioSignedPercent(it)) }
                    if (liveTrackedValue) {
                        Text("Live-Kurs × Stückzahl", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (advisor != null) {
                    HorizontalDivider()
                    PortfolioDashboardValue("Empfehlung", portfolioAdvisorActionLabel(advisor.action))
                    advisor.suggestedAmountEur?.let { PortfolioDashboardValue("Betrag", portfolioMoney(it)) }
                }

                if (item != null && custom == null) {
                    Button(onClick = { onOpenDetail(row.itemId) }, modifier = Modifier.fillMaxWidth()) { Text("Details") }
                    OutlinedButton(onClick = { onEdit(item) }, modifier = Modifier.fillMaxWidth()) { Text("Transaktionen verwalten") }
                }
                if (custom != null) {
                    OutlinedButton(onClick = { onEditCustom(custom) }, modifier = Modifier.fillMaxWidth()) { Text("Stammdaten bearbeiten") }
                    TextButton(onClick = { onRemoveCustom(row.itemId) }, modifier = Modifier.fillMaxWidth()) { Text("Eigenen Wert löschen", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { onRemove(row.itemId) }, modifier = Modifier.fillMaxWidth()) { Text("Aus Portfolio entfernen", color = MaterialTheme.colorScheme.error) }
                }

                if (row.active && !row.hasUsablePrice && !(importedSnapshot && !hasTrackedShares)) {
                    Text("Aktueller Wert fehlt – historische Snapshotwerte werden nicht als heutiger Depotwert verwendet.", color = MaterialTheme.colorScheme.error)
                }
                if (!isFixedIncome) position?.let {
                    Text(
                        when {
                            importedSnapshot && liveTrackedValue && row.costBasisKnown -> "Live-Tracking aktiv · Einstand importiert"
                            importedSnapshot && liveTrackedValue -> "Live-Tracking aktiv · Einstand fehlt"
                            importedSnapshot && row.costBasisKnown -> "Bestätigter Depot-Snapshot · Einstand importiert"
                            importedSnapshot -> "Bestätigter Depot-Snapshot · Einstand fehlt"
                            else -> "${it.purchases.size} Käufe · ${it.sales.size} Verkäufe"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (soldMarkedRows.isNotEmpty()) {
            item(key = "sold-section") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Verkaufte Positionen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        "Diese Werte zählen nicht zum aktiven Depot. Angezeigte Kurse sind nur heutige Marktpreise.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            items(soldMarkedRows, key = { "sold-${it.itemId}" }) { row ->
                val canonicalItem = itemById[row.itemId]
                val item = canonicalItem ?: customById[row.itemId]?.fallbackItem()
                val custom = customById[row.itemId]
                val position = positions[row.itemId]
                val currentMarketPrice = canonicalItem?.priceEur?.takeIf { it.isFinite() && it > 0.0 }

                PortfolioDashboardCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item?.name ?: row.itemId, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text(listOfNotNull(item?.ticker?.takeIf { it.isNotBlank() }, item?.type?.takeIf { it.isNotBlank() }).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("VERKAUFT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Black)
                    }
                    HorizontalDivider()
                    PortfolioDashboardValue(
                        "Aktueller Marktpreis",
                        currentMarketPrice?.let(::portfolioMoney) ?: "Kurs nicht verfügbar"
                    )
                    Text(
                        "Der heutige Marktpreis ist nur eine Referenz. Ein tatsächlicher Verkaufskurs oder Erlös wird durch den Screenshot-Abgleich nicht erfunden.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    position?.snapshotValueEur?.takeIf { it.isFinite() && it >= 0.0 }?.let { snapshotValue ->
                        PortfolioDashboardValue(
                            "Letzter bestätigter Trade-Republic-Wert",
                            portfolioSnapshotValue(snapshotValue, position.snapshotCapturedAt)
                        )
                    }
                    position?.let {
                        PortfolioDashboardValue("Historie", "${it.purchases.size} Käufe · ${it.sales.size} Verkäufe")
                        if (it.sales.isNotEmpty()) {
                            PortfolioDashboardValue("Realisierter G/V aus erfassten Verkäufen", portfolioSignedMoney(row.realizedProfitLoss))
                        }
                    }
                    if (item != null && custom == null) {
                        Button(onClick = { onOpenDetail(row.itemId) }, modifier = Modifier.fillMaxWidth()) { Text("Details") }
                        OutlinedButton(onClick = { onEdit(item) }, modifier = Modifier.fillMaxWidth()) { Text("Transaktionen verwalten") }
                    }
                    if (custom != null) {
                        OutlinedButton(onClick = { onEditCustom(custom) }, modifier = Modifier.fillMaxWidth()) { Text("Stammdaten bearbeiten") }
                        TextButton(onClick = { onRemoveCustom(row.itemId) }, modifier = Modifier.fillMaxWidth()) { Text("Eigenen Wert löschen", color = MaterialTheme.colorScheme.error) }
                    } else {
                        TextButton(onClick = { onRemove(row.itemId) }, modifier = Modifier.fillMaxWidth()) { Text("Aus Portfolio entfernen", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    trackedSharesDialogItemId?.let { itemId ->
        val position = positions[itemId]
        val name = itemById[itemId]?.name ?: customById[itemId]?.name ?: itemId
        var input by remember(itemId, position?.trackedShares) {
            mutableStateOf(position?.trackedShares?.let(::portfolioShares).orEmpty())
        }
        val parsed = GermanDecimalInput.parse(input)?.takeIf { it.isFinite() && it > 0.0 }
        AlertDialog(
            onDismissRequest = { trackedSharesDialogItemId = null },
            title = { Text("Stückzahl ergänzen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(name, fontWeight = FontWeight.Bold)
                    Text("Trage die aktuelle Stückzahl aus deinem Depot ein. Damit wird der Depotwert künftig aus Stückzahl × aktuellem Kurs berechnet. Ein importierter Einstand bleibt für die Performanceberechnung erhalten.")
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Stückzahl") },
                        singleLine = true,
                        supportingText = { if (input.isNotBlank() && parsed == null) Text("Bitte eine Zahl größer als 0 eingeben.") }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = parsed != null,
                    onClick = {
                        val value = parsed ?: return@Button
                        if (vm.setTrackedShares(itemId, value)) trackedSharesDialogItemId = null
                    }
                ) { Text("Stückzahl speichern") }
            },
            dismissButton = {
                TextButton(onClick = { trackedSharesDialogItemId = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun PortfolioDashboardCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun PortfolioDashboardValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

private fun portfolioMoney(value: Double): String = String.format(Locale.GERMANY, "%.2f €", value)
private fun portfolioSignedMoney(value: Double): String = String.format(Locale.GERMANY, "%+.2f €", value)
private fun portfolioPercent(value: Double): String = String.format(Locale.GERMANY, "%.1f %%", value)
private fun portfolioSignedPercent(value: Double): String = String.format(Locale.GERMANY, "%+.1f %%", value)
private fun portfolioShares(value: Double): String = String.format(Locale.GERMANY, "%.6f", value).trimEnd('0').trimEnd(',')
private fun portfolioSnapshotValue(value: Double, capturedAt: String?): String {
    val timestamp = capturedAt?.let(::portfolioSnapshotTimestamp)?.takeIf { it.isNotBlank() }
    return listOfNotNull(portfolioMoney(value), timestamp).joinToString(" · ")
}
private fun portfolioSnapshotTimestamp(value: String): String {
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})").find(value.trim()) ?: return value
    val (year, month, day, hour, minute) = match.destructured
    return "$day.$month.$year · $hour:$minute"
}

private fun portfolioAdvisorActionLabel(action: PortfolioAdvisorAction): String = when (action) {
    PortfolioAdvisorAction.NACHKAUFEN -> "Nachkaufen"
    PortfolioAdvisorAction.HALTEN -> "Halten"
    PortfolioAdvisorAction.REDUZIEREN -> "Verkaufen (Teilverkauf)"
    PortfolioAdvisorAction.VERKAUFEN -> "Verkaufen"
    PortfolioAdvisorAction.NEU_AUFNEHMEN -> "Neu aufnehmen"
    PortfolioAdvisorAction.NICHT_AUFNEHMEN -> "Nicht aufnehmen"
    PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> "Keine belastbare Bewertung"
}
