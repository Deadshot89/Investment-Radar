package de.tobias.investmentradar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun LivePortfolioCard(
    items: List<InvestmentItem>,
    positions: Map<String, PortfolioPosition>,
    customItems: List<CustomInvestment>,
    onOpenPortfolio: () -> Unit
) {
    val summary = remember(items, positions, customItems) {
        LivePortfolioSummary.build(items, positions, customItems)
    }
    val itemById = remember(items) { items.associateBy { it.id } }
    val customById = remember(customItems) { customItems.associateBy { it.id } }
    val largestName = summary.largestPositionId?.let { id -> itemById[id]?.name ?: customById[id]?.name ?: id }
    val accent = Color(0xFF2EE59D)
    val muted = Color(0xFF91A1B7)
    val surface = Color(0xFF101C2D)

    Card(
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(22.dp))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MEIN DEPOT", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    Text(formatLiveMoney(summary.currentValue), fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                }
                Button(onClick = onOpenPortfolio) { Text("Öffnen") }
            }

            LivePortfolioValue("Einstand", if (summary.performanceComplete) formatLiveMoney(summary.costBasis) else "Nicht vollständig erfasst", muted)
            LivePortfolioValue("Gewinn / Verlust", if (summary.performanceComplete) "${formatLiveSignedMoney(summary.profitLoss)} · ${formatLiveSignedPercent(summary.profitLossPct)}" else "Nicht vollständig berechenbar", if (summary.profitLoss < 0.0) Color(0xFFFF6577) else accent)
            LivePortfolioValue("Positionen", summary.positionCount.toString(), muted)
            LivePortfolioValue(
                "Größte Position",
                if (largestName != null && summary.largestWeightPct != null) "$largestName · ${formatLivePercent(summary.largestWeightPct)}" else "–",
                muted
            )

            HorizontalDivider()
            summary.positions.forEach { row ->
                val name = itemById[row.itemId]?.name ?: customById[row.itemId]?.name ?: row.itemId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatLiveMoney(row.currentValue), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text(formatLivePercent(row.weightPct), color = muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePortfolioValue(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF91A1B7))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

private fun formatLiveMoney(value: Double): String = String.format(Locale.GERMANY, "%.2f €", value)
private fun formatLiveSignedMoney(value: Double): String = String.format(Locale.GERMANY, "%+.2f €", value)
private fun formatLiveSignedPercent(value: Double): String = String.format(Locale.GERMANY, "%+.2f %%", value)
private fun formatLivePercent(value: Double): String = String.format(Locale.GERMANY, "%.1f %%", value)
