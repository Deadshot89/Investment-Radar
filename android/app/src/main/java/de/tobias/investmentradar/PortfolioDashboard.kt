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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onRemoveCustom: (String) -> Unit
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("PORTFOLIO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Mein Depot", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
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
                PortfolioDashboardValue("Einstand", portfolioMoney(metrics.investedCostBasis))
                PortfolioDashboardValue("Positionen", metrics.heldPositionCount.toString())
                PortfolioDashboardValue(
                    "Größte Position",
                    if (largestName != null && metrics.largestWeightPct != null) {
                        "$largestName · ${portfolioPercent(metrics.largestWeightPct)}"
                    } else {
                        "Nicht verfügbar"
                    }
                )
                HorizontalDivider()
                val profit = metrics.totalProfitLoss
                val profitPct = metrics.totalProfitLossPct
                PortfolioDashboardValue(
                    "Gewinn / Verlust",
                    if (profit != null && profitPct != null) {
                        "${portfolioSignedMoney(profit)} · ${portfolioSignedPercent(profitPct)}"
                    } else {
                        "Nicht vollständig berechenbar"
                    }
                )
                if (!metrics.currentValueComplete) {
                    Text(
                        "${metrics.missingPriceCount} Position(en) ohne verwertbaren Kurs – Gesamtperformance unvollständig.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
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

            PortfolioDashboardCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item?.name ?: row.itemId, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(item?.ticker?.takeIf { it.isNotBlank() }, item?.type?.takeIf { it.isNotBlank() }).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (row.active) "AKTIV" else "GESCHLOSSEN",
                        color = if (row.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Black
                    )
                }

                HorizontalDivider()
                PortfolioDashboardValue("Aktueller Wert", row.currentValue?.let(::portfolioMoney) ?: "Kurs fehlt")
                PortfolioDashboardValue("Einstand", portfolioMoney(row.investedCostBasis))
                PortfolioDashboardValue("Realisierter G/V", portfolioSignedMoney(row.realizedProfitLoss))
                PortfolioDashboardValue(
                    "Gesamt G/V",
                    if (row.totalProfitLoss != null && row.totalProfitLossPct != null) {
                        "${portfolioSignedMoney(row.totalProfitLoss)} · ${portfolioSignedPercent(row.totalProfitLossPct)}"
                    } else if (!row.active) {
                        portfolioSignedMoney(row.realizedProfitLoss)
                    } else {
                        "Nicht berechenbar"
                    }
                )
                PortfolioDashboardValue("Gewichtung", row.weightPct?.let(::portfolioPercent) ?: if (row.active) "Unvollständig" else "–")

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

                Button(onClick = { onOpenDetail(row.itemId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Details")
                }
                if (item != null) {
                    OutlinedButton(onClick = { onEdit(item) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Transaktionen verwalten")
                    }
                    OutlinedButton(onClick = { TradeRepublicNavigator.open(context, item) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Trade Republic öffnen")
                    }
                }
                if (custom != null) {
                    OutlinedButton(onClick = { onEditCustom(custom) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Stammdaten bearbeiten")
                    }
                    TextButton(onClick = { onRemoveCustom(row.itemId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Eigenen Wert löschen", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { onRemove(row.itemId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Aus Portfolio entfernen", color = MaterialTheme.colorScheme.error)
                    }
                }

                if (row.active && !row.hasUsablePrice) {
                    Text("Kurs fehlt – Depotwert und Gesamtperformance werden deshalb als Teilwert angezeigt.", color = MaterialTheme.colorScheme.error)
                }
                position?.let {
                    Text(
                        "${it.purchases.size} Käufe · ${it.sales.size} Verkäufe",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioDashboardCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
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
