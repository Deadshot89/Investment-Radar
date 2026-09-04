package de.tobias.investmentradar

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
