package de.tobias.investmentradar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    var selectedRegion by remember { mutableStateOf<String?>(null) }
    var verifiedOnly by remember { mutableStateOf(false) }
    var requestedPage by remember { mutableIntStateOf(1) }
    var radarPage by remember { mutableStateOf<RadarPage?>(null) }
    var loaded by remember { mutableStateOf<List<RadarSummaryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var remoteDetailId by remember { mutableStateOf<String?>(null) }
    var remoteDetail by remember { mutableStateOf<RadarSummaryItem?>(null) }
    var detailLoading by remember { mutableStateOf(false) }

    val queryKey = listOf(
        filters.query.trim(), filters.recommendation.name, filters.type.name, filters.dataQuality.name,
        filters.risk.name, filters.sort.name, selectedRegion.orEmpty(), verifiedOnly.toString(), requestedPage.toString()
    ).joinToString("|")

    LaunchedEffect(queryKey) {
        if (filters.query.isNotBlank()) delay(300)
        loading = true
        error = null
        val query = RadarQuery(
            query = filters.query,
            type = when (filters.type) {
                RadarTypeFilter.STOCK -> "AKTIE"
                RadarTypeFilter.ETF -> "ETF"
                else -> null
            },
            region = selectedRegion,
            recommendation = when (filters.recommendation) {
                RadarRecommendationFilter.BUY -> "BUY"
                RadarRecommendationFilter.WATCH -> "WATCH"
                RadarRecommendationFilter.NO_BUY -> "NO_BUY"
                RadarRecommendationFilter.REVIEW -> "REVIEW"
                else -> null
            },
            qualityTier = when (filters.dataQuality) {
                RadarDataQualityFilter.FULL -> "A"
                RadarDataQualityFilter.REDUCED -> "B"
                RadarDataQualityFilter.INSUFFICIENT -> "C"
                else -> null
            },
            riskMax = when (filters.risk) {
                RadarRiskFilter.LOW -> 2
                RadarRiskFilter.MEDIUM -> 3
                RadarRiskFilter.HIGH -> 5
                else -> null
            },
            sort = when (filters.sort) {
                RadarSortMode.MOMENTUM_6M -> "MOMENTUM_DESC"
                RadarSortMode.NAME -> "NAME"
                else -> "SCORE_DESC"
            },
            page = requestedPage,
            pageSize = 40,
            tradeRepublicVerified = verifiedOnly
        )
        runCatching { ApiClient.loadRadarPage(query) }
            .onSuccess { page ->
                radarPage = page
                loaded = if (page.page <= 1) page.items else (loaded + page.items).distinctBy { it.id }
            }
            .onFailure { failure ->
                error = failure.message ?: "Radar konnte nicht geladen werden"
                if (loaded.isEmpty()) {
                    loaded = items.filter { !it.portfolioOnly }.map { it.toRadarFallback() }
                }
            }
        loading = false
    }

    LaunchedEffect(remoteDetailId) {
        val id = remoteDetailId ?: return@LaunchedEffect
        if (items.any { it.id == id }) return@LaunchedEffect
        detailLoading = true
        remoteDetail = runCatching { ApiClient.loadRadarDetail(id) }.getOrNull()
        detailLoading = false
    }

    val clientVisible = loaded.filter { summary ->
        when (filters.holding) {
            RadarHoldingFilter.HELD -> summary.id in holdingIds
            RadarHoldingFilter.NOT_HELD -> summary.id !in holdingIds
            else -> true
        }
    }.filter { !filters.watchlistOnly || it.id in watchlistIds }

    val topOpportunities = loaded.filter { it.purchaseEligible }.sortedByDescending { it.scoreTotal ?: -1 }.take(5)
    val momentum = loaded.sortedByDescending { it.scoreMomentum ?: -1 }.take(5)
    val valuation = loaded.sortedByDescending { it.scoreValuation ?: -1 }.take(5)
    val quality = loaded.filter { it.type != "ETF" }.sortedByDescending { it.scoreQuality ?: -1 }.take(5)
    val etfs = loaded.filter { it.type == "ETF" }.sortedByDescending { it.scoreTotal ?: -1 }.take(5)
    val newInRadar = loaded.filter { it.tradeRepublicEligible == null }.take(5)
    val complements = loaded.filter { it.id !in holdingIds && it.purchaseEligible }.take(5)

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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("RADAR 2.0", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("${radarPage?.universeTotal ?: loaded.size} Werte im Analyseuniversum", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        "Serverseitige Analyse · Ergebnisse werden seitenweise geladen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = filters.query,
                        onValueChange = { filters = filters.copy(query = it); requestedPage = 1 },
                        label = { Text("Name, Ticker oder ISIN suchen") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilterGroup("SORTIERUNG") {
                        listOf(RadarSortMode.SCORE, RadarSortMode.MOMENTUM_6M, RadarSortMode.NAME).forEach { option ->
                            FilterChip(
                                selected = filters.sort == option,
                                onClick = { filters = filters.copy(sort = option); requestedPage = 1 },
                                label = { Text(option.sortLabel()) }
                            )
                        }
                    }
                    FilterGroup("EMPFEHLUNG") {
                        RadarRecommendationFilter.entries.forEach { option ->
                            FilterChip(selected = filters.recommendation == option, onClick = { filters = filters.copy(recommendation = option); requestedPage = 1 }, label = { Text(option.recommendationLabel()) })
                        }
                    }
                    FilterGroup("TYP") {
                        RadarTypeFilter.entries.forEach { option ->
                            FilterChip(selected = filters.type == option, onClick = { filters = filters.copy(type = option); requestedPage = 1 }, label = { Text(option.typeLabel()) })
                        }
                    }
                    radarPage?.facets?.regions?.take(8)?.takeIf { it.isNotEmpty() }?.let { regions ->
                        FilterGroup("REGION") {
                            FilterChip(selected = selectedRegion == null, onClick = { selectedRegion = null; requestedPage = 1 }, label = { Text("Alle") })
                            regions.forEach { facet ->
                                FilterChip(selected = selectedRegion == facet.value, onClick = { selectedRegion = facet.value; requestedPage = 1 }, label = { Text("${facet.value} (${facet.count})") })
                            }
                        }
                    }
                    FilterGroup("RISIKO") {
                        RadarRiskFilter.entries.forEach { option ->
                            FilterChip(selected = filters.risk == option, onClick = { filters = filters.copy(risk = option); requestedPage = 1 }, label = { Text(option.riskLabel()) })
                        }
                    }
                    FilterGroup("DATENQUALITÄT") {
                        RadarDataQualityFilter.entries.forEach { option ->
                            FilterChip(selected = filters.dataQuality == option, onClick = { filters = filters.copy(dataQuality = option); requestedPage = 1 }, label = { Text(option.dataLabel()) })
                        }
                    }
                    FilterGroup("DEPOT & HANDELBARKEIT") {
                        RadarHoldingFilter.entries.forEach { option ->
                            FilterChip(selected = filters.holding == option, onClick = { filters = filters.copy(holding = option) }, label = { Text(option.holdingLabel()) })
                        }
                        FilterChip(selected = filters.watchlistOnly, onClick = { filters = filters.copy(watchlistOnly = !filters.watchlistOnly) }, label = { Text("Watchlist (${watchlistIds.size})") })
                        FilterChip(selected = verifiedOnly, onClick = { verifiedOnly = !verifiedOnly; requestedPage = 1 }, label = { Text("TR geprüft") })
                    }
                    Text(
                        "${radarPage?.total ?: clientVisible.size} Treffer · ${radarPage?.tradeRepublicVerifiedCount ?: 0} TR-geprüft · ${radarPage?.tradeRepublicUnverifiedCount ?: 0} Prüfung offen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { DiscoverySection("Top Chancen", topOpportunities) { remoteDetailId = it.id; if (items.any { x -> x.id == it.id }) onOpenDetail(it.id) } }
        item { DiscoverySection("Neu im Radar", newInRadar) { remoteDetailId = it.id; if (items.any { x -> x.id == it.id }) onOpenDetail(it.id) } }
        item { DiscoverySection("Starkes Momentum", momentum) { remoteDetailId = it.id; if (items.any { x -> x.id == it.id }) onOpenDetail(it.id) } }
        item { DiscoverySection("Attraktive Bewertung", valuation) { remoteDetailId = it.id; if (items.any { x -> x.id == it.id }) onOpenDetail(it.id) } }
        item { DiscoverySection("Qualitätsaktien", quality) { remoteDetailId = it.id; if (items.any { x -> x.id == it.id }) onOpenDetail(it.id) } }
        item { DiscoverySection("ETFs", etfs) { remoteDetailId = it.id; if (items.any { x -> x.id == it.id }) onOpenDetail(it.id) } }
        item { DiscoverySection("Depot-Ergänzungen", complements) { remoteDetailId = it.id; if (items.any { x -> x.id == it.id }) onOpenDetail(it.id) } }

        if (error != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Radar-Verbindung gestört", fontWeight = FontWeight.Black)
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Die bereits geladenen Werte bleiben sichtbar.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (loading && loaded.isEmpty()) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }

        items(clientVisible, key = { it.id }) { summary ->
            val investment = summary.asInvestmentItem()
            RadarResultCardV2(
                summary = summary,
                personal = personalById[summary.id],
                isHeld = summary.id in holdingIds,
                isWatchlisted = summary.id in watchlistIds,
                onToggleWatchlist = { onToggleWatchlist(summary.id) },
                onBought = { onBought(investment) },
                onEditInvestment = { onEditInvestment(investment) },
                onOpenDetail = {
                    if (items.any { it.id == summary.id }) onOpenDetail(summary.id)
                    else remoteDetailId = summary.id
                },
                onOpenTradeRepublic = { TradeRepublicNavigator.open(context, investment) }
            )
        }

        if (radarPage?.hasMore == true) {
            item {
                Button(
                    onClick = { requestedPage = (radarPage?.page ?: requestedPage) + 1 },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) "Lade weitere Werte…" else "Weitere 40 Ergebnisse laden") }
            }
        }
    }

    if (remoteDetailId != null && items.none { it.id == remoteDetailId }) {
        val detail = remoteDetail
        AlertDialog(
            onDismissRequest = { remoteDetailId = null; remoteDetail = null },
            title = { Text(detail?.name ?: "Detailanalyse") },
            text = {
                if (detailLoading) CircularProgressIndicator()
                else if (detail == null) Text("Detailanalyse konnte noch nicht geladen werden.")
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${detail.ticker} · ${detail.type} · ${detail.country.ifBlank { detail.region }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Score ${detail.scoreTotal ?: "–"} · Daten ${detail.coverage ?: 0} % · Risiko ${detail.risk}/5", fontWeight = FontWeight.Bold)
                    Text("Signal: ${detail.recommendation}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    Text(detail.tradeRepublicStatusLabel(), style = MaterialTheme.typography.bodySmall)
                    detail.recommendationReasons.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    detail.dataError?.takeIf { it.isNotBlank() }?.let { Text("Datenhinweis: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { remoteDetailId = null; remoteDetail = null }) { Text("Schließen") } }
        )
    }
}

@Composable
private fun DiscoverySection(title: String, entries: List<RadarSummaryItem>, onOpen: (RadarSummaryItem) -> Unit) {
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { "$title-${it.id}" }) { item ->
                Card(onClick = { onOpen(item) }, modifier = Modifier.width(190.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, maxLines = 2, fontWeight = FontWeight.Bold)
                        Text(item.ticker, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Score ${item.scoreTotal ?: "–"} · ${item.recommendation}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
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
private fun RadarResultCardV2(
    summary: RadarSummaryItem,
    personal: PersonalRecommendation?,
    isHeld: Boolean,
    isWatchlisted: Boolean,
    onToggleWatchlist: () -> Unit,
    onBought: () -> Unit,
    onEditInvestment: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenTradeRepublic: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(summary.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text("${summary.ticker} · ${summary.isin.ifBlank { "ISIN offen" }} · ${summary.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (summary.region.isNotBlank() || summary.sector.isNotBlank()) Text(listOf(summary.region, summary.sector).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                Text(summary.recommendation, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Score ${summary.scoreTotal ?: "–"}")
                Text("Coverage ${summary.coverage?.let { "$it %" } ?: "–"}")
                Text("Risiko ${summary.risk}/5")
            }
            summary.percentChange?.let { Text("Tag ${formatRadarPercent(it)}", color = if (it >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            Text(summary.tradeRepublicStatusLabel(), style = MaterialTheme.typography.bodySmall, color = if (summary.tradeRepublicEligible == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            personal?.let { Text("Monatskauf ${it.allocationEur} € · Depot ${String.format(Locale.GERMANY, "%.1f", it.currentWeightPct)} % · ${it.concentrationLabel}", style = MaterialTheme.typography.bodySmall) }
            summary.recommendationReasons.take(2).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) { Text("Details") }
            OutlinedButton(onClick = onOpenTradeRepublic, modifier = Modifier.fillMaxWidth()) { Text("Trade Republic öffnen") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleWatchlist, modifier = Modifier.weight(1f)) { Text(if (isWatchlisted) "Watchlist −" else "Watchlist +") }
                if (isHeld) OutlinedButton(onClick = onEditInvestment, modifier = Modifier.weight(1f)) { Text("Transaktionen") }
                else OutlinedButton(onClick = onBought, modifier = Modifier.weight(1f)) { Text("Kauf erfassen") }
            }
        }
    }
}

private fun RadarSummaryItem.tradeRepublicStatusLabel(): String = when {
    tradeRepublicEligible == true && purchaseEligible -> "Trade Republic geprüft · als Kaufkandidat zugelassen"
    tradeRepublicEligible == true -> "Trade Republic geprüft"
    tradeRepublicEligible == false -> "Nicht als Trade-Republic-Kandidat freigegeben"
    else -> "Trade-Republic-Handelbarkeit noch nicht bestätigt · keine automatische Kaufzuteilung"
}

private fun InvestmentItem.toRadarFallback(): RadarSummaryItem = RadarSummaryItem(
    id = id, type = type, name = name, ticker = ticker, isin = isin, tradeRepublicName = tradeRepublicName,
    region = "", country = "", sector = "", industry = "", marketCapBucket = "",
    tradeRepublicEligible = true, dataQualityTier = if ((coverage ?: 0) >= 70) "A" else "B", risk = risk,
    price = price, priceEur = priceEur, currency = currency, percentChange = percentChange,
    scoreTotal = scoreTotal, scoreQuality = scoreQuality, scoreValuation = scoreValuation, scoreGrowth = scoreGrowth,
    scoreMomentum = scoreMomentum, scoreRisk = scoreRisk, coverage = coverage, recommendation = recommendation,
    recommendationReasons = recommendationReasons, purchaseEligible = recommendation == "BUY" && !portfolioOnly,
    dataSource = dataSource, dataDelayed = dataDelayed, dataError = dataError, analysisAsOf = analysisAsOf,
    momentum = momentum, fundamentals = fundamentals
)

private fun RadarSortMode.sortLabel(): String = when (this) {
    RadarSortMode.SCORE -> "Beste Chancen"
    RadarSortMode.ALLOCATION -> "Budget"
    RadarSortMode.MOMENTUM_6M -> "Momentum"
    RadarSortMode.DAY_ASC -> "Tag aufsteigend"
    RadarSortMode.DAY_DESC -> "Tag absteigend"
    RadarSortMode.NAME -> "Name"
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
    RadarDataQualityFilter.FULL -> "A"
    RadarDataQualityFilter.REDUCED -> "B"
    RadarDataQualityFilter.INSUFFICIENT -> "C"
}

private fun RadarRiskFilter.riskLabel(): String = when (this) {
    RadarRiskFilter.ALL -> "Alle"
    RadarRiskFilter.LOW -> "Niedrig"
    RadarRiskFilter.MEDIUM -> "Bis mittel"
    RadarRiskFilter.HIGH -> "Alle Risiken"
}

private fun formatRadarPercent(value: Double): String = String.format(Locale.GERMANY, "%+.1f %%", value)