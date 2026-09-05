package de.tobias.investmentradar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

@Composable
fun PortfolioDashboard(
    items: List<InvestmentItem>,
    positions: Map<String, PortfolioPosition>,
    customItems: List<CustomInvestment>,
    personalById: Map<String, PersonalRecommendation>,
    onOpenDetail: (String) -> Unit,
    onEdit: (InvestmentItem) -> Unit,
    onRemove: (String) -> Unit,
    onAddCustom: () -> Unit,
    onEditCustom: (CustomInvestment) -> Unit,
    onRemoveCustom: (String) -> Unit,
    vm: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val metrics = remember(items, positions, customItems) {
        PortfolioMetrics.calculate(items, positions, customItems)
    }
    val itemById = remember(items) { items.associateBy { it.id } }
    val customById = remember(customItems) { customItems.associateBy { it.id } }
    val largestName = metrics.largestPositionId?.let { id ->
        itemById[id]?.name ?: customById[id]?.name ?: id
    }
    val costBasisComplete = metrics.positions.filter { it.active }.all { it.costBasisKnown }
    var trackedSharesDialogItemId by remember { mutableStateOf<String?>(null) }
    var showSavingsPlans by remember { mutableStateOf(false) }

    if (showSavingsPlans) {
        SavingsPlansScreen(
            items = items,
            onBack = { showSavingsPlans = false },
            vm = vm
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("PORTFOLIO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Mein Depot", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = { showSavingsPlans = true }) { Text("Sparpläne") }
                Button(onClick = onAddCustom) { Text("Wert hinzufügen") }
            }
        }

        item {
            PortfolioDashboardCard {
                Text(
                    if (metrics.currentValueComplete) "Depotwert" else "Teilwert",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(portfolioMoney(metrics.calculableCurrentValue), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                PortfolioDashboardValue("Einstand", if (costBasisComplete) portfolioMoney(metrics.investedCostBasis) else "Nicht vollständig erfasst")
                PortfolioDashboardValue("Positionen", metrics.heldPositionCount.toString())
                PortfolioDashboardValue(
                    "Größte Position",
                    if (largestName != null && metrics.largestWeightPct != null) "$largestName · ${portfolioPercent(metrics.largestWeightPct)}" else "Noch keine aktive Position"
                )
                HorizontalDivider()
                val profit = metrics.totalProfitLoss
                val profitPct = metrics.totalProfitLossPct
                PortfolioDashboardValue(
                    "Gewinn / Verlust",
                    if (profit != null && profitPct != null) "${portfolioSignedMoney(profit)} · ${portfolioSignedPercent(profitPct)}" else "Nicht vollständig berechenbar"
                )
                if (!costBasisComplete) {
                    Text("Für einzelne Depotwerte fehlen Einstandsdaten. Depotwert und Gewichtung bleiben korrekt; fehlende Performance wird nicht erfunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!metrics.currentValueComplete) {
                    Text("${metrics.missingPriceCount} Position(en) ohne verwertbaren Kurs – Gesamtperformance unvollständig.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (metrics.positions.isEmpty()) {
            item {
                PortfolioDashboardCard {
                    Text("Noch keine Positionen", fontWeight = FontWeight.Black)
                    Text("Füge eine Aktie oder einen ETF hinzu oder erfasse einen Kauf im Radar.")
                }
            }
        }

        items(metrics.positions, key = { it.itemId }) { row ->
            val item = itemById[row.itemId] ?: customById[row.itemId]?.fallbackItem()
            val custom = customById[row.itemId]
            val personal = personalById[row.itemId]
            val position = positions[row.itemId]
            val importedSnapshot = position?.snapshotValueEur != null && position.purchases.isEmpty() && position.sales.isEmpty()
            val liveTrackedValue = importedSnapshot &&
                position?.trackedShares != null &&
                row.hasUsablePrice

            PortfolioDashboardCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item?.name ?: row.itemId, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(item?.ticker?.takeIf { it.isNotBlank() }, item?.type?.takeIf { it.isNotBlank() }).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (row.active) "AKTIV" else "GESCHLOSSEN", color = if (row.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Black)
                }

                HorizontalDivider()
                PortfolioDashboardValue("Aktueller Wert", row.currentValue?.let(::portfolioMoney) ?: "Kurs fehlt")
                if (importedSnapshot) {
                    PortfolioDashboardValue(
                        "Wertbasis",
                        if (liveTrackedValue) "Live-Kurs × Stückzahl" else "Importierter Depotwert"
                    )
                }
                PortfolioDashboardValue("Einstand", if (row.costBasisKnown) portfolioMoney(row.investedCostBasis) else "Nicht erfasst")
                PortfolioDashboardValue("Realisierter G/V", if (row.costBasisKnown) portfolioSignedMoney(row.realizedProfitLoss) else "Nicht erfasst")
                PortfolioDashboardValue(
                    "Gesamt G/V",
                    when {
                        !row.costBasisKnown -> "Nicht erfasst"
                        row.totalProfitLoss != null && row.totalProfitLossPct != null -> "${portfolioSignedMoney(row.totalProfitLoss)} · ${portfolioSignedPercent(row.totalProfitLossPct)}"
                        !row.active -> portfolioSignedMoney(row.realizedProfitLoss)
                        else -> "Nicht berechenbar"
                    }
                )
                PortfolioDashboardValue("Gewichtung", row.weightPct?.let(::portfolioPercent) ?: if (row.active) "Unvollständig" else "–")
                if (importedSnapshot) {
                    PortfolioDashboardValue(
                        "Stückzahl",
                        position?.trackedShares?.let(::portfolioShares) ?: "Nicht erfasst"
                    )
                }

                if (item != null) {
                    HorizontalDivider()
                    PortfolioDashboardValue("Empfehlung", RecommendationPresentation.label(item))
                    PortfolioDashboardValue("Score", RecommendationPresentation.scoreText(item.scoreTotal))
                }
                personal?.let {
                    PortfolioDashboardValue("Monatskauf", "${it.allocationEur} €")
                    PortfolioDashboardValue("Konzentration", it.concentrationLabel)
                    Text(it.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (importedSnapshot) {
                    OutlinedButton(onClick = { trackedSharesDialogItemId = row.itemId }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (position?.trackedShares == null) "Stückzahl ergänzen" else "Stückzahl ändern")
                    }
                    if (position?.trackedShares == null) {
                        Text(
                            "Nach Eingabe der Stückzahl folgt der Depotwert dem aktuellen Kurs. Der importierte Einstand bleibt für Gewinn/Verlust erhalten.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Button(onClick = { onOpenDetail(row.itemId) }, modifier = Modifier.fillMaxWidth()) { Text("Details") }
                if (item != null) {
                    OutlinedButton(onClick = { onEdit(item) }, modifier = Modifier.fillMaxWidth()) { Text("Transaktionen verwalten") }
                    OutlinedButton(onClick = { TradeRepublicNavigator.open(context, item) }, modifier = Modifier.fillMaxWidth()) { Text("Trade Republic öffnen") }
                }
                if (custom != null) {
                    OutlinedButton(onClick = { onEditCustom(custom) }, modifier = Modifier.fillMaxWidth()) { Text("Stammdaten bearbeiten") }
                    TextButton(onClick = { onRemoveCustom(row.itemId) }, modifier = Modifier.fillMaxWidth()) { Text("Eigenen Wert löschen", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { onRemove(row.itemId) }, modifier = Modifier.fillMaxWidth()) { Text("Aus Portfolio entfernen", color = MaterialTheme.colorScheme.error) }
                }

                if (row.active && !row.hasUsablePrice) {
                    Text("Kurs fehlt – Depotwert und Gesamtperformance werden deshalb als Teilwert angezeigt.", color = MaterialTheme.colorScheme.error)
                }
                position?.let {
                    Text(
                        when {
                            importedSnapshot && liveTrackedValue && row.costBasisKnown -> "Live-Tracking aktiv · Einstand importiert"
                            importedSnapshot && liveTrackedValue -> "Live-Tracking aktiv · Einstand fehlt"
                            importedSnapshot && row.costBasisKnown -> "Depotwert und Einstand importiert"
                            importedSnapshot -> "Depotwert importiert · Einstand fehlt"
                            else -> "${it.purchases.size} Käufe · ${it.sales.size} Verkäufe"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
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
        val parsed = input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
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
