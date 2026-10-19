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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

@Composable
fun PortfolioDashboard(
    items: List<InvestmentItem>,
    positions: Map<String, PortfolioPosition>,
    customItems: List<CustomInvestment>,
    advisorPlan: PortfolioAdvisorPlan,
    showSavingsPlans: Boolean,
    onShowSavingsPlansChange: (Boolean) -> Unit,
    onOpenDetail: (String) -> Unit,
    onEdit: (InvestmentItem) -> Unit,
    onRemove: (String) -> Unit,
    onMarkSold: (String) -> Unit,
    onReactivate: (String) -> Unit,
    onUpdateDepot: () -> Unit,
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
    val advisorById = remember(advisorPlan) { advisorPlan.candidates.associateBy { it.itemId } }
    val allocationById = remember(advisorPlan) { advisorPlan.allocations.associateBy { it.itemId } }
    val customById = remember(customItems) { customItems.associateBy { it.id } }
    val largestName = metrics.largestPositionId?.let { id ->
        itemById[id]?.name ?: customById[id]?.name ?: id
    }
    val costBasisComplete = metrics.positions.filter { it.active }.all { it.costBasisKnown }
    val soldMarkedRows = remember(metrics.positions, positions) {
        metrics.positions.filter { row ->
            positions[row.itemId]?.lifecycleStatus == PortfolioLifecycleStatus.SOLD_MARKED
        }
    }
    val regularRows = remember(metrics.positions, positions) {
        metrics.positions.filterNot { row ->
            positions[row.itemId]?.lifecycleStatus == PortfolioLifecycleStatus.SOLD_MARKED
        }
    }
    var trackedSharesDialogItemId by remember { mutableStateOf<String?>(null) }
    var pendingSoldItemId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteItemId by remember { mutableStateOf<String?>(null) }

    if (showSavingsPlans) {
        SavingsPlansScreen(
            items = items,
            onBack = { onShowSavingsPlansChange(false) },
            vm = vm
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("portfolioDashboardList"),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("PORTFOLIO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Mein Depot", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = { onShowSavingsPlansChange(true) }) { Text("Sparpläne") }
                Button(onClick = onAddCustom) { Text("Wert hinzufügen") }
            }
        }

        item {
            OutlinedButton(onClick = onUpdateDepot, modifier = Modifier.fillMaxWidth()) {
                Text("Depot aktualisieren", fontWeight = FontWeight.Bold)
            }
        }

        item {
            PortfolioDashboardCard {
                Text("Was soll ich jetzt tun?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                val invested = advisorPlan.allocations.sumOf { it.amountEur }
                val shifted = advisorPlan.reallocations.sumOf { it.amountEur }
                Text("$invested € investieren · $shifted € Umschichten · ${advisorPlan.cashEur} € Cash halten", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                advisorPlan.allocations.take(4).forEach { allocation ->
                    val name = itemById[allocation.itemId]?.name ?: customById[allocation.itemId]?.name ?: allocation.itemId
                    PortfolioDashboardValue(name, "+${allocation.amountEur} € · ${portfolioAdvisorActionLabel(allocation.action)}")
                }
                advisorPlan.reallocations.take(3).forEach { suggestion ->
                    val from = itemById[suggestion.fromItemId]?.name ?: customById[suggestion.fromItemId]?.name ?: suggestion.fromItemId
                    val to = itemById[suggestion.toItemId]?.name ?: customById[suggestion.toItemId]?.name ?: suggestion.toItemId
                    Text("Umschichten: ${suggestion.amountEur} € · $from → $to", style = MaterialTheme.typography.bodySmall)
                }
                advisorPlan.savingsPlanConflicts.take(3).forEach { conflict ->
                    val name = itemById[conflict.itemId]?.name ?: customById[conflict.itemId]?.name ?: conflict.itemId
                    Text("Sparplan prüfen: $name · ${conflict.monthlySavingsEur} € · ${portfolioAdvisorActionLabel(conflict.action)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (advisorPlan.allocations.isEmpty()) Text("Für neues Kapital ist aktuell Cash halten die belastbarere Entscheidung.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(
                    if (metrics.currentValueComplete) portfolioMoney(metrics.calculableCurrentValue) else "Nicht vollständig berechenbar",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                if (!metrics.currentValueComplete && metrics.calculableCurrentValue > 0.0) {
                    PortfolioDashboardValue(
                        "Berechenbarer Teil",
                        "${portfolioMoney(metrics.calculableCurrentValue)} · ${metrics.calculablePositionCount} von ${metrics.heldPositionCount} Positionen"
                    )
                }
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
                    when {
                        profit != null && profitPct != null -> "${portfolioSignedMoney(profit)} · ${portfolioSignedPercent(profitPct)}"
                        metrics.partialProfitLoss != null && metrics.partialProfitLossPct != null ->
                            "${portfolioSignedMoney(metrics.partialProfitLoss)} · ${portfolioSignedPercent(metrics.partialProfitLossPct)} · ${metrics.performancePositionCount} von ${metrics.heldPositionCount}"
                        else -> "Nicht vollständig berechenbar"
                    }
                )
                if (!costBasisComplete) {
                    Text("Für einzelne Depotwerte fehlen Einstandsdaten. Depotwert und Gewichtung bleiben korrekt; fehlende Performance wird nicht erfunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (metrics.positions.isEmpty()) {
            item {
                PortfolioDashboardCard {
                    Text("Noch keine Positionen", fontWeight = FontWeight.Black)
                    Text("Füge eine Aktie, einen ETF oder einen Festzins-Wert hinzu oder erfasse einen Kauf im Radar.")
                }
            }
        }

        items(regularRows, key = { it.itemId }) { row ->
            val canonicalItem = itemById[row.itemId]
            val item = canonicalItem ?: customById[row.itemId]?.fallbackItem()
            val custom = customById[row.itemId]
            val isFixedIncome = custom?.type.equals("Festzins", ignoreCase = true)
            val advisor = advisorById[row.itemId]
            val allocation = allocationById[row.itemId]
            val position = positions[row.itemId]
            val importedSnapshot = position?.snapshotValueEur != null && position.purchases.isEmpty() && position.sales.isEmpty()
            val hasTrackedShares = position?.trackedShares?.let { it.isFinite() && it > 0.0 } == true
            val liveTrackedValue = importedSnapshot && hasTrackedShares && row.hasUsablePrice
            val currentMarketPrice = row.currentPriceEur
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
                    Text(if (row.active) "AKTIV" else "GESCHLOSSEN", color = if (row.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Black)
                }

                HorizontalDivider()
                when {
                    isFixedIncome -> PortfolioDashboardValue(
                        "Anlagewert",
                        row.currentValue?.let(::portfolioMoney) ?: "Anlagebetrag fehlt"
                    )
                    importedSnapshot && !hasTrackedShares -> {
                        PortfolioDashboardValue("Aktueller Depotwert", "Nicht berechenbar · $currentValueMissingReason")
                        position?.snapshotValueEur?.takeIf { it.isFinite() && it >= 0.0 }?.let { snapshotValue ->
                            PortfolioDashboardValue(
                                "Letzter bestätigter Trade-Republic-Wert",
                                portfolioSnapshotValue(snapshotValue, position.snapshotCapturedAt)
                            )
                        }
                        PortfolioDashboardValue(
                            "Aktueller Marktpreis",
                            currentMarketPrice?.let(::portfolioMoney) ?: "Kurs nicht verfügbar"
                        )
                    }
                    else -> PortfolioDashboardValue(
                        "Aktueller Wert",
                        row.currentValue?.let(::portfolioMoney) ?: currentValueMissingReason
                    )
                }
                if (!isFixedIncome && !(importedSnapshot && !hasTrackedShares)) {
                    PortfolioDashboardValue("Aktueller Kurs", row.currentPriceEur?.let(::portfolioMoney) ?: "Kurs fehlt")
                }
                if (isFixedIncome) {
                    custom?.fixedMaturityValueEur?.let { maturityValue ->
                        PortfolioDashboardValue("Auszahlung bei Fälligkeit", portfolioMoney(maturityValue))
                        custom.fixedPrincipalEur?.takeIf { it > 0.0 }?.let { principal ->
                            val expectedReturn = maturityValue - principal
                            val expectedReturnPct = expectedReturn / principal * 100.0
                            PortfolioDashboardValue(
                                "Erwarteter Ertrag",
                                "${portfolioSignedMoney(expectedReturn)} · ${portfolioSignedPercent(expectedReturnPct)}"
                            )
                        }
                    }
                    custom?.maturityLabel?.takeIf { it.isNotBlank() }?.let {
                        PortfolioDashboardValue("Fälligkeit", it)
                    }
                }
                if (importedSnapshot && !isFixedIncome && hasTrackedShares) {
                    PortfolioDashboardValue("Wertbasis", if (liveTrackedValue) "Live-Kurs × Stückzahl" else "Stückzahl vorhanden · Kurs fehlt")
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
                if (importedSnapshot && !isFixedIncome) {
                    PortfolioDashboardValue("Stückzahl", position?.trackedShares?.let(::portfolioShares) ?: "Nicht erfasst")
                }

                if (item != null && !isFixedIncome) {
                    HorizontalDivider()
                    PortfolioDashboardValue("Empfehlung", RecommendationPresentation.label(item))
                    PortfolioDashboardValue("Score", RecommendationPresentation.scoreText(item.scoreTotal))
                }
                if (!isFixedIncome) advisor?.let {
                    PortfolioDashboardValue("Berater", portfolioAdvisorActionLabel(it.action))
                    PortfolioDashboardValue("Zusatz diesen Monat", "${allocation?.amountEur ?: 0} €")
                    PortfolioDashboardValue("Konfidenz", "${it.advisor.confidencePct} %")
                    it.advisor.reasons.take(2).forEach { reason ->
                        Text("• $reason", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (importedSnapshot && !isFixedIncome) {
                    OutlinedButton(onClick = { trackedSharesDialogItemId = row.itemId }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (position?.trackedShares == null) "Stückzahl ergänzen" else "Stückzahl ändern")
                    }
                    if (position?.trackedShares == null) {
                        Text(
                            "Nach Eingabe der Stückzahl folgt der Depotwert dem aktuellen Kurs. Der bestätigte Trade-Republic-Wert bleibt als Snapshot nachvollziehbar.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (!isFixedIncome) {
                    Button(onClick = { onOpenDetail(row.itemId) }, modifier = Modifier.fillMaxWidth()) { Text("Details") }
                    if (item != null) {
                        OutlinedButton(onClick = { onEdit(item) }, modifier = Modifier.fillMaxWidth()) { Text("Transaktionen verwalten") }
                        OutlinedButton(onClick = { TradeRepublicNavigator.open(context, item) }, modifier = Modifier.fillMaxWidth()) { Text("Trade Republic öffnen") }
                    }
                    if (row.active) {
                        OutlinedButton(
                            onClick = { pendingSoldItemId = row.itemId },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Als verkauft markieren") }
                    }
                }
                if (custom != null) {
                    OutlinedButton(onClick = { onEditCustom(custom) }, modifier = Modifier.fillMaxWidth()) { Text("Stammdaten bearbeiten") }
                    TextButton(onClick = { pendingDeleteItemId = row.itemId }, modifier = Modifier.fillMaxWidth()) { Text("Eigenen Wert löschen", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { pendingDeleteItemId = row.itemId }, modifier = Modifier.fillMaxWidth()) { Text("Aus Portfolio entfernen", color = MaterialTheme.colorScheme.error) }
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
                val item = itemById[row.itemId] ?: customById[row.itemId]?.fallbackItem()
                val custom = customById[row.itemId]
                val position = positions[row.itemId]
                val currentMarketPrice = row.currentPriceEur

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
                    OutlinedButton(
                        onClick = { onReactivate(row.itemId) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Wieder aktiv setzen") }
                    if (custom != null) {
                        OutlinedButton(onClick = { onEditCustom(custom) }, modifier = Modifier.fillMaxWidth()) { Text("Stammdaten bearbeiten") }
                        TextButton(onClick = { pendingDeleteItemId = row.itemId }, modifier = Modifier.fillMaxWidth()) { Text("Eigenen Wert löschen", color = MaterialTheme.colorScheme.error) }
                    } else {
                        TextButton(onClick = { pendingDeleteItemId = row.itemId }, modifier = Modifier.fillMaxWidth()) { Text("Aus Portfolio entfernen", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    pendingSoldItemId?.let { itemId ->
        val name = itemById[itemId]?.name ?: customById[itemId]?.name ?: itemId
        AlertDialog(
            onDismissRequest = { pendingSoldItemId = null },
            title = { Text("Position als verkauft markieren?") },
            text = {
                Text("$name wird aus dem aktiven Depot entfernt und unter 'Verkaufte Positionen' angezeigt. Es wird kein Verkauf, Erlös oder Cash gebucht. Die bisherige Historie bleibt erhalten.")
            },
            confirmButton = {
                Button(onClick = {
                    onMarkSold(itemId)
                    pendingSoldItemId = null
                }) { Text("Als verkauft markieren") }
            },
            dismissButton = { TextButton(onClick = { pendingSoldItemId = null }) { Text("Abbrechen") } }
        )
    }

    pendingDeleteItemId?.let { itemId ->
        val name = itemById[itemId]?.name ?: customById[itemId]?.name ?: itemId
        val isCustom = customById.containsKey(itemId)
        AlertDialog(
            onDismissRequest = { pendingDeleteItemId = null },
            title = { Text(if (isCustom) "Eigenen Wert endgültig löschen?" else "Depotposition endgültig entfernen?") },
            text = {
                Text("$name wird aus dem Depot entfernt. Dabei werden die lokal gespeicherten Käufe, Verkäufe und Depot-Snapshots dieser Position gelöscht. Es werden keine Verkaufserlöse erzeugt. Dieser Schritt ist nicht rückgängig zu machen.")
            },
            confirmButton = {
                Button(onClick = {
                    if (isCustom) onRemoveCustom(itemId) else onRemove(itemId)
                    pendingDeleteItemId = null
                }) { Text("Endgültig löschen") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteItemId = null }) { Text("Abbrechen") } }
        )
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
