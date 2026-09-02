package de.tobias.investmentradar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun RadarScreenV2(
    items: List<InvestmentItem>,
    holdingIds: Set<String>,
    watchlistIds: Set<String>,
    personalById: Map<String, PersonalRecommendation>,
    onToggleWatchlist: (String) -> Unit,
    onBought: (InvestmentItem) -> Unit,
    onEditInvestment: (InvestmentItem) -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val context = LocalContext.current
    var filters by remember { mutableStateOf(RadarFilterState()) }
    val visibleItems = remember(items, filters, holdingIds, watchlistIds, personalById) {
        RadarFilterEngine.apply(
            items = items,
            state = filters,
            holdingIds = holdingIds,
            watchlistIds = watchlistIds,
            allocationById = personalById.mapValues { it.value.allocationEur }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("RADAR", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("${items.size} Werte · Analyse V2", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    OutlinedTextField(
                        value = filters.query,
                        onValueChange = { filters = filters.copy(query = it) },
                        label = { Text("Suchen nach Name, Ticker, ISIN oder Typ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    FilterGroup("EMPFEHLUNG") {
                        RadarRecommendationFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filters.recommendation == option,
                                onClick = { filters = filters.copy(recommendation = option) },
                                label = { Text(option.recommendationLabel()) }
                            )
                        }
                    }
                    FilterGroup("TYP") {
                        RadarTypeFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filters.type == option,
                                onClick = { filters = filters.copy(type = option) },
                                label = { Text(option.typeLabel()) }
                            )
                        }
                    }
                    FilterGroup("DEPOT") {
                        RadarHoldingFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filters.holding == option,
                                onClick = { filters = filters.copy(holding = option) },
                                label = { Text(option.holdingLabel()) }
                            )
                        }
                        FilterChip(
                            selected = filters.watchlistOnly,
                            onClick = { filters = filters.copy(watchlistOnly = !filters.watchlistOnly) },
                            label = { Text("Watchlist (${watchlistIds.size})") }
                        )
                    }
                    FilterGroup("DATENQUALITÄT") {
                        RadarDataQualityFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filters.dataQuality == option,
                                onClick = { filters = filters.copy(dataQuality = option) },
                                label = { Text(option.dataLabel()) }
                            )
                        }
                    }
                    FilterGroup("RISIKO") {
                        RadarRiskFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filters.risk == option,
                                onClick = { filters = filters.copy(risk = option) },
                                label = { Text(option.riskLabel()) }
                            )
                        }
                    }
                    FilterGroup("SORTIERUNG") {
                        RadarSortMode.entries.forEach { option ->
                            FilterChip(
                                selected = filters.sort == option,
                                onClick = { filters = filters.copy(sort = option) },
                                label = { Text(option.sortLabel()) }
                            )
                        }
                    }
                    Text("${visibleItems.size} Treffer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (visibleItems.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Keine Treffer", fontWeight = FontWeight.Black)
                        Text("Passe Suche oder Filter an.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        items(visibleItems, key = { it.id }) { item ->
            RadarResultCard(
                item = item,
                personal = personalById[item.id],
                isHeld = item.id in holdingIds,
                isWatchlisted = item.id in watchlistIds,
                onToggleWatchlist = { onToggleWatchlist(item.id) },
                onBought = { onBought(item) },
                onEditInvestment = { onEditInvestment(item) },
                onOpenDetail = { onOpenDetail(item.id) },
                onOpenTradeRepublic = { TradeRepublicNavigator.open(context, item) }
            )
        }
    }
}

@Composable
private fun FilterGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { content() } }
        }
    }
}

@Composable
private fun RadarResultCard(
    item: InvestmentItem,
    personal: PersonalRecommendation?,
    isHeld: Boolean,
    isWatchlisted: Boolean,
    onToggleWatchlist: () -> Unit,
    onBought: () -> Unit,
    onEditInvestment: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenTradeRepublic: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text("${item.ticker} · ${item.isin.ifBlank { "keine ISIN" }} · ${item.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(RecommendationPresentation.label(item), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Score ${item.scoreTotal?.toString() ?: "–"}")
                Text("Coverage ${item.coverage?.let { "$it %" } ?: "–"}")
                Text("Risiko ${item.risk}/5")
            }
            item.percentChange?.let { Text("Tag ${formatRadarPercent(it)}", color = if (it >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            item.momentum?.m6?.let { Text("6M ${formatRadarPercent(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            personal?.let {
                Text(
                    "Monatskauf ${it.allocationEur} € · Depot ${String.format(Locale.GERMANY, "%.1f", it.currentWeightPct)} % · ${it.concentrationLabel}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            RecommendationPresentation.topReasons(item).take(2).forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) { Text("Details") }
            OutlinedButton(onClick = onOpenTradeRepublic, modifier = Modifier.fillMaxWidth()) { Text("Trade Republic öffnen") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleWatchlist, modifier = Modifier.weight(1f)) {
                    Text(if (isWatchlisted) "Watchlist −" else "Watchlist +")
                }
                if (isHeld) {
                    OutlinedButton(onClick = onEditInvestment, modifier = Modifier.weight(1f)) { Text("Transaktionen") }
                } else {
                    OutlinedButton(onClick = onBought, modifier = Modifier.weight(1f)) { Text("Kauf erfassen") }
                }
            }
        }
    }
}

private fun RadarRecommendationFilter.recommendationLabel(): String = when (this) {
    RadarRecommendationFilter.ALL -> "Alle"
    RadarRecommendationFilter.BUY -> "Kaufen"
    RadarRecommendationFilter.WATCH -> "Beobachten"
    RadarRecommendationFilter.NO_BUY -> "Nicht kaufen"
    RadarRecommendationFilter.REVIEW -> "Prüfen"
}

private fun RadarTypeFilter.typeLabel(): String = when (this) {
    RadarTypeFilter.ALL -> "Alle"
    RadarTypeFilter.STOCK -> "Aktie"
    RadarTypeFilter.ETF -> "ETF"
}

private fun RadarHoldingFilter.holdingLabel(): String = when (this) {
    RadarHoldingFilter.ALL -> "Alle"
    RadarHoldingFilter.HELD -> "Im Depot"
    RadarHoldingFilter.NOT_HELD -> "Nicht im Depot"
}

private fun RadarDataQualityFilter.dataLabel(): String = when (this) {
    RadarDataQualityFilter.ALL -> "Alle"
    RadarDataQualityFilter.FULL -> "Vollständig"
    RadarDataQualityFilter.REDUCED -> "Reduziert"
    RadarDataQualityFilter.INSUFFICIENT -> "Unzureichend"
}

private fun RadarRiskFilter.riskLabel(): String = when (this) {
    RadarRiskFilter.ALL -> "Alle"
    RadarRiskFilter.LOW -> "Niedrig"
    RadarRiskFilter.MEDIUM -> "Mittel"
    RadarRiskFilter.HIGH -> "Hoch"
}

private fun RadarSortMode.sortLabel(): String = when (this) {
    RadarSortMode.SCORE -> "Score"
    RadarSortMode.ALLOCATION -> "Monatskauf"
    RadarSortMode.MOMENTUM_6M -> "6M"
    RadarSortMode.DAY_ASC -> "Tag ↑"
    RadarSortMode.DAY_DESC -> "Tag ↓"
    RadarSortMode.NAME -> "Name"
}

private fun formatRadarPercent(value: Double): String = String.format(Locale.GERMANY, "%+.1f %%", value)
