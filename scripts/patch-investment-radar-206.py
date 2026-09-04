from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) Live dashboard: augment the small dashboard list with verified BUY candidates
# from the large Radar universe. Radar failure is intentionally non-fatal.
replace_once(
    "android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt",
    '''            runCatching {
                val dashboard = ApiClient.loadDashboard()
                val application = getApplication<Application>()
                promoteCustomPortfolioAssets(application, dashboard.items)
                val customQuotes = _customItems.value.map { custom ->
                    async {
                        runCatching { ApiClient.loadCustomQuote(custom) }
                            .getOrElse { custom.fallbackItem(it.message ?: "Kursdaten fehlen", custom.manualPriceEur) }
                    }
                }.awaitAll()
                dashboard.copy(items = (dashboard.items + customQuotes).distinctBy { it.id })
            }''',
    '''            runCatching {
                val dashboardDeferred = async { ApiClient.loadDashboard() }
                val radarBuyDeferred = async {
                    runCatching { ApiClient.loadRadarPage(
                        RadarQuery(
                            recommendation = "BUY",
                            sort = "SCORE_DESC",
                            page = 1,
                            pageSize = 20,
                            tradeRepublicVerified = true
                        )
                    ) }.getOrNull()
                }
                val dashboard = dashboardDeferred.await()
                val radarBuyItems = radarBuyDeferred.await()?.items.orEmpty()
                    .filter { it.purchaseEligible }
                    .map { it.asInvestmentItem() }
                val application = getApplication<Application>()
                promoteCustomPortfolioAssets(application, (radarBuyItems + dashboard.items).distinctBy { it.id })
                val customQuotes = _customItems.value.map { custom ->
                    async {
                        runCatching { ApiClient.loadCustomQuote(custom) }
                            .getOrElse { custom.fallbackItem(it.message ?: "Kursdaten fehlen", custom.manualPriceEur) }
                    }
                }.awaitAll()
                dashboard.copy(
                    topPickId = radarBuyItems.firstOrNull()?.id ?: dashboard.topPickId,
                    items = (radarBuyItems + dashboard.items + customQuotes).distinctBy { it.id }
                )
            }'''
)

# 2) Make WARTEN visually secondary and use its own purple accent.
replace_once(
    "android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt",
    '''        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkMetricCard("MARKT", data.marketLight.uppercase(), RadarYellow, Modifier.weight(1f))
                DarkMetricCard("BUDGET", "$budget €", RadarBlue, Modifier.weight(1f), onClick = onEditBudget)
                DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", RadarGreen, Modifier.weight(1f), valueStyle = if (top == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
            }
        }''',
    '''        item {
            val signalAccent = if (top != null) RadarGreen else RadarPurple
            val signalStyle = if (top == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkMetricCard("MARKT", data.marketLight.uppercase(), RadarYellow, Modifier.weight(1f))
                DarkMetricCard("BUDGET", "$budget €", RadarBlue, Modifier.weight(1f), onClick = onEditBudget)
                DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", signalAccent, Modifier.weight(1f), valueStyle = signalStyle)
            }
        }'''
)

# 3) Replace the Live forecast component with fully German scenario labels and
# explicit scenario colors. The internal model field names remain unchanged.
Path("android/app/src/main/java/de/tobias/investmentradar/LiveForecastSummary.kt").write_text('''package de.tobias.investmentradar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

private val LiveForecastBear = Color(0xFFFF6577)
private val LiveForecastBase = Color(0xFF4DE6FF)
private val LiveForecastBull = Color(0xFF2EE59D)

@Composable
fun LiveForecastSummary(
    item: InvestmentItem,
    compact: Boolean = false
) {
    val forecast = remember(item) { ForecastEngine.forecast(item) }
    val point = forecast.points.firstOrNull { it.horizon == ForecastHorizon.TWELVE_MONTHS } ?: return
    val coverage = forecast.coveragePct
    val targetSuffix = point.targetPriceEur
        ?.let { " · Ziel ${String.format(Locale.GERMANY, "%.2f €", it)}" }
        .orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 5.dp)) {
        Text(
            "12M Prognose · ${point.direction} · ${liveForecastPercent(point.expectedChangePct)}$targetSuffix",
            color = LiveForecastBase,
            fontWeight = FontWeight.Black,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Schwach ${liveForecastScenario(point.bearTargetPriceEur, point.bearChangePct)}", color = LiveForecastBear, style = MaterialTheme.typography.bodySmall)
            Text("Erwartet ${liveForecastScenario(point.targetPriceEur, point.expectedChangePct)}", color = LiveForecastBase, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text("Stark ${liveForecastScenario(point.bullTargetPriceEur, point.bullChangePct)}", color = LiveForecastBull, style = MaterialTheme.typography.bodySmall)
        }
        point.reasons.take(if (compact) 1 else 2).forEachIndexed { index, reason ->
            Text(
                if (index == 0) "Warum? $reason" else "• $reason",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            when {
                coverage == null -> "Datenlage unbekannt · höhere Unsicherheit"
                coverage < 70 -> "Datenlage $coverage % · höhere Unsicherheit"
                else -> "Datenlage $coverage %"
            },
            color = if (coverage == null || coverage < 70) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun liveForecastScenario(targetPriceEur: Double?, changePct: Double): String =
    targetPriceEur?.let { String.format(Locale.GERMANY, "%.2f €", it) }
        ?: liveForecastPercent(changePct)

private fun liveForecastPercent(value: Double): String = String.format(Locale.GERMANY, "%+.1f %%", value)
''', encoding="utf-8")

# 4) Detail view must use the same German labels.
replace_once(
    "android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt",
    '''                    DetailValueRow("Basis", detailForecastScenario(point.targetPriceEur, point.expectedChangePct))
                    DetailValueRow("Bear", detailForecastScenario(point.bearTargetPriceEur, point.bearChangePct))
                    DetailValueRow("Bull", detailForecastScenario(point.bullTargetPriceEur, point.bullChangePct))''',
    '''                    DetailValueRow("Erwartet", detailForecastScenario(point.targetPriceEur, point.expectedChangePct))
                    DetailValueRow("Schwach", detailForecastScenario(point.bearTargetPriceEur, point.bearChangePct))
                    DetailValueRow("Stark", detailForecastScenario(point.bullTargetPriceEur, point.bullChangePct))'''
)

# 5) Remove the remaining user-visible English bear terminology from explanations.
replace_once(
    "android/app/src/main/java/de/tobias/investmentradar/ForecastEngine.kt",
    'if (item.risk >= 4) result += "Das hohe Risikoprofil sorgt für eine breite Prognosespanne."',
    'if (item.risk >= 4) result += "Das hohe Risikoprofil sorgt für eine breite Prognosespanne."'
)
replace_once(
    "android/app/src/main/java/de/tobias/investmentradar/ForecastEngine.kt",
    'if (debt > 2.0) drift -= ((debt - 2.0) * 1.2).coerceAtMost(4.0)',
    'if (debt > 2.0) drift -= ((debt - 2.0) * 1.2).coerceAtMost(4.0)'
)
replace_once(
    "android/app/src/main/java/de/tobias/investmentradar/ForecastEngine.kt",
    'if (it > 2.0) result += "Die erhöhte Verschuldung verbreitert das Bear-Szenario."',
    'if (it > 2.0) result += "Die erhöhte Verschuldung verbreitert das schwache Szenario."'
)

print("Investment Radar 2.0.6 source patch applied")
