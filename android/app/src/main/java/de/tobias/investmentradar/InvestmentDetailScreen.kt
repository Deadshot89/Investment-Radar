package de.tobias.investmentradar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun InvestmentDetailScreen(
    item: InvestmentItem?,
    customItem: CustomInvestment?,
    position: PortfolioPosition?,
    personalRecommendation: PersonalRecommendation?,
    isWatchlisted: Boolean,
    onBack: () -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onEditPosition: (InvestmentItem) -> Unit,
    onOpenPortfolio: () -> Unit
) {
    val context = LocalContext.current
    val effectiveItem = detailItem(item, customItem)

    if (effectiveItem == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = onBack) { Text("← Zurück") }
                DetailCard {
                    Text("Wertpapier nicht mehr verfügbar", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("Der Eintrag ist nicht mehr im aktuellen Radar oder in deinen eigenen Werten enthalten.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    val freshness = DataFreshness.summarize(effectiveItem)
    val analysisAvailable = listOf(
        effectiveItem.scoreTotal,
        effectiveItem.scoreQuality,
        effectiveItem.scoreValuation,
        effectiveItem.scoreGrowth,
        effectiveItem.scoreMomentum,
        effectiveItem.scoreRisk
    ).any { it != null }
    val momentum = effectiveItem.momentum
    val fundamentals = effectiveItem.fundamentals
    val forecast = ForecastEngine.forecast(effectiveItem)
    val trend = detailTrend(momentum)
    val comparablePrice = effectiveItem.priceEur
        ?: effectiveItem.price?.takeIf { effectiveItem.currency.isBlank() || effectiveItem.currency.equals("EUR", ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Zurück") }
            DetailCard {
                Text(effectiveItem.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    listOf(effectiveItem.ticker, effectiveItem.isin.takeIf { it.isNotBlank() }, effectiveItem.type)
                        .filterNotNull()
                        .joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(detailPrice(effectiveItem), fontWeight = FontWeight.Bold)
                    Text(
                        effectiveItem.percentChange?.let { detailSignedPercent(it) } ?: "Tag Nicht verfügbar",
                        color = when {
                            effectiveItem.percentChange == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            effectiveItem.percentChange >= 0.0 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider()
                DetailValueRow("Empfehlung", RecommendationPresentation.label(effectiveItem))
                DetailValueRow("Gesamtscore", effectiveItem.scoreTotal?.let { "$it/100" } ?: "Nicht verfügbar")
            }
        }

        item {
            DetailCard {
                Text("PROGNOSE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Text("1 Monat · 3 Monate · 6 Monate · 12 Monate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(
                    "Eine modellbasierte Einschätzung aus Qualität, Bewertung, Wachstum, Momentum, Risiko und Datenabdeckung – kein garantierter Zielkurs.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                forecast.points.forEachIndexed { index, point ->
                    if (index > 0) HorizontalDivider()
                    Text("${point.horizon.label} · ${point.direction}", fontWeight = FontWeight.Black)
                    DetailValueRow("Erwartet", detailForecastScenario(point.targetPriceEur, point.expectedChangePct))
                    DetailValueRow("Schwach", detailForecastScenario(point.bearTargetPriceEur, point.bearChangePct))
                    DetailValueRow("Stark", detailForecastScenario(point.bullTargetPriceEur, point.bullChangePct))
                    Text("Warum diese Prognose?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    point.reasons.take(2).forEach { reason ->
                        Text("• $reason", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        personalRecommendation?.let { personal ->
            item {
                DetailCard {
                    Text("Deine Einordnung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    DetailValueRow("Monatskauf", "${personal.allocationEur} €")
                    DetailValueRow("Depotgewicht", String.format(Locale.GERMANY, "%.1f %%", personal.currentWeightPct))
                    DetailValueRow("Konzentration", personal.concentrationLabel)
                    Text(personal.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            if (analysisAvailable) {
                ScoreBreakdownCard(effectiveItem)
                Spacer(Modifier.height(8.dp))
                DetailCard {
                    Text("Analysefaktoren", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    DetailValueRow("Quality (Qualität)", detailScore(effectiveItem.scoreQuality))
                    DetailValueRow("Valuation (Bewertung)", detailScore(effectiveItem.scoreValuation))
                    DetailValueRow("Growth (Wachstum)", detailScore(effectiveItem.scoreGrowth))
                    DetailValueRow("Momentum", detailScore(effectiveItem.scoreMomentum))
                    DetailValueRow("Risk (Risiko)", detailScore(effectiveItem.scoreRisk))
                    DetailValueRow("Coverage (Datenabdeckung)", effectiveItem.coverage?.let { "$it %" } ?: "Nicht verfügbar")
                }
            } else {
                DetailCard {
                    Text("Analyse V2", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(
                        if (customItem != null) "Analyse V2 für diesen eigenen Wert nicht verfügbar" else "Analyse V2 nicht verfügbar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            DetailCard {
                Text("Datenstatus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                DetailValueRow("Status", freshness.label)
                DetailValueRow("Coverage", freshness.coverage?.let { "$it %" } ?: "Nicht verfügbar")
                freshness.analysisAsOf?.let { DetailValueRow("Analyse-Stand", detailTimestamp(it)) }
                when (freshness.status) {
                    FreshnessStatus.CURRENT -> Text("Die für die Analyse verwendeten Daten liegen innerhalb der Frischegrenzen.", color = MaterialTheme.colorScheme.primary)
                    FreshnessStatus.CACHED -> Text("Mindestens ein Analysebaustein kommt aus einem noch verwendbaren Cache.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FreshnessStatus.PARTIAL -> Text("Mindestens ein erwarteter Analysebaustein oder ausreichende Datenabdeckung fehlt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FreshnessStatus.STALE -> Text("Mindestens ein verwendeter Analysebaustein ist älter als sieben Tage.", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            DetailCard {
                Text("Momentum", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                val momentumRows = listOf(
                    "1D" to momentum?.d1,
                    "1M" to momentum?.m1,
                    "3M" to momentum?.m3,
                    "6M" to momentum?.m6,
                    "12M" to momentum?.m12
                ).filter { it.second != null }
                if (momentumRows.isEmpty()) {
                    Text("Nicht verfügbar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    momentumRows.forEach { (period, value) ->
                        DetailValueRow(period, detailSignedPercent(value!!))
                    }
                }
                HorizontalDivider()
                DetailValueRow("Trend", trend)
            }
        }

        item {
            DetailCard {
                Text("Fundamentaldaten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                if (!fundamentals.hasDetailMetrics()) {
                    Text("Fundamentaldaten nicht verfügbar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    fundamentals?.pe?.let { DetailValueRow("KGV / P/E", detailNumber(it)) }
                    fundamentals?.priceToSales?.let { DetailValueRow("KUV / P/S", detailNumber(it)) }
                    fundamentals?.evToEbitda?.let { DetailValueRow("EV/EBITDA", detailNumber(it)) }
                    fundamentals?.freeCashFlowYield?.let { DetailValueRow("Free-Cashflow-Rendite", detailRatioPercent(it)) }
                    fundamentals?.revenueGrowth?.let { DetailValueRow("Umsatzwachstum", detailRatioPercent(it)) }
                    fundamentals?.epsGrowth?.let { DetailValueRow("Gewinnwachstum", detailRatioPercent(it)) }
                    fundamentals?.operatingMargin?.let { DetailValueRow("Operative Marge", detailRatioPercent(it)) }
                    fundamentals?.netMargin?.let { DetailValueRow("Nettomarge", detailRatioPercent(it)) }
                    fundamentals?.roe?.let { DetailValueRow("ROE", detailRatioPercent(it)) }
                    fundamentals?.roic?.let { DetailValueRow("ROIC", detailRatioPercent(it)) }
                    fundamentals?.debtToEquity?.let { DetailValueRow("Debt/Equity", detailNumber(it)) }
                }
            }
        }

        if (effectiveItem.recommendationReasons.isNotEmpty()) {
            item {
                DetailCard {
                    Text("Warum diese Empfehlung?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    effectiveItem.recommendationReasons.take(5).forEach { reason ->
                        Text("• $reason", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        position?.let { portfolioPosition ->
            item {
                DetailCard {
                    Text("Deine Position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    DetailValueRow("Bestand", detailShares(portfolioPosition.shares))
                    DetailValueRow("Investiert", detailMoney(portfolioPosition.investedAmount))
                    DetailValueRow("Ø Einstand", portfolioPosition.averageBuyPrice()?.let(::detailMoney) ?: "Nicht verfügbar")
                    DetailValueRow("Aktueller Wert", portfolioPosition.currentValue(comparablePrice)?.let(::detailMoney) ?: "Nicht verfügbar")
                    DetailValueRow("Realisierter G/V", detailSignedMoney(portfolioPosition.realizedProfitLoss()))
                    DetailValueRow("Gesamt G/V", portfolioPosition.totalProfitLoss(comparablePrice)?.let(::detailSignedMoney) ?: "Nicht verfügbar")
                }
            }
        }

        item {
            DetailCard {
                Text("Datenquellen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                DetailValueRow("Kurs", freshness.quoteSource ?: "Nicht verfügbar")
                DetailValueRow("Historie / Momentum", freshness.historySource ?: "Nicht verfügbar")
                momentum?.asOf?.takeIf { it.isNotBlank() }?.let { DetailValueRow("Historie Stand", detailTimestamp(it)) }
                DetailValueRow("Fundamentaldaten", freshness.fundamentalSource ?: "Nicht verfügbar")
                fundamentals?.asOf?.takeIf { it.isNotBlank() }?.let { DetailValueRow("Fundamental Stand", detailTimestamp(it)) }
            }
        }

        item {
            DetailCard {
                OutlinedButton(
                    onClick = { onToggleWatchlist(effectiveItem.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isWatchlisted) "Von Watchlist entfernen" else "Zur Watchlist") }

                Button(
                    onClick = { onEditPosition(effectiveItem) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (position != null) "Transaktionen verwalten" else "Kauf erfassen") }

                if (position != null) {
                    OutlinedButton(onClick = onOpenPortfolio, modifier = Modifier.fillMaxWidth()) { Text("Portfolio öffnen") }
                }

                Button(
                    onClick = { TradeRepublicNavigator.open(context, effectiveItem) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Trade Republic öffnen") }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun DetailValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

private fun detailItem(item: InvestmentItem?, customItem: CustomInvestment?): InvestmentItem? = when {
    item != null && customItem != null -> item.copy(
        type = customItem.type,
        name = customItem.name,
        ticker = customItem.ticker,
        isin = customItem.isin,
        tradeRepublicName = customItem.name,
        risk = customItem.risk
    )
    item != null -> item
    customItem != null -> customItem.fallbackItem()
    else -> null
}

private fun detailTrend(momentum: MomentumSnapshot?): String {
    val m3 = momentum?.m3
    val m6 = momentum?.m6
    return when {
        m3 != null && m6 != null && m3 > 0.0 && m6 > 0.0 -> "Positiver Trend"
        m3 != null && m6 != null && m3 < 0.0 && m6 < 0.0 -> "Negativer Trend"
        m3 != null || m6 != null -> "Gemischter Trend"
        else -> "Nicht verfügbar"
    }
}

private fun FundamentalSnapshot?.hasDetailMetrics(): Boolean {
    val f = this ?: return false
    return listOf(
        f.pe,
        f.priceToSales,
        f.evToEbitda,
        f.freeCashFlowYield,
        f.revenueGrowth,
        f.epsGrowth,
        f.operatingMargin,
        f.netMargin,
        f.roe,
        f.roic,
        f.debtToEquity
    ).any { it != null }
}

private fun detailScore(value: Int?): String = value?.let { "$it/100" } ?: "Nicht verfügbar"

private fun detailPrice(item: InvestmentItem): String {
    val value = item.price?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: "Nicht verfügbar"
    return if (item.price == null) "Kurs $value" else "Kurs $value ${item.currency}".trim()
}

private fun detailNumber(value: Double): String = String.format(Locale.GERMANY, "%.2f", value)
private fun detailRatioPercent(value: Double): String = String.format(Locale.GERMANY, "%+.2f %%", value * 100.0)
private fun detailSignedPercent(value: Double): String = String.format(Locale.GERMANY, "%+.2f %%", value)
private fun detailMoney(value: Double): String = String.format(Locale.GERMANY, "%.2f €", value)
private fun detailSignedMoney(value: Double): String = String.format(Locale.GERMANY, "%+.2f €", value)
private fun detailShares(value: Double): String = String.format(Locale.GERMANY, "%.6f", value).trimEnd('0').trimEnd(',')
private fun detailTimestamp(value: String): String = value.replace('T', ' ').removeSuffix("Z")

private fun detailForecastScenario(targetPriceEur: Double?, changePct: Double): String =
    targetPriceEur?.let { String.format(Locale.GERMANY, "%.2f € (%+.1f %%)", it, changePct) }
        ?: String.format(Locale.GERMANY, "%+.1f %% · Zielpreis nicht verfügbar", changePct)
