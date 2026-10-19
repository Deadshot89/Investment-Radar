package de.tobias.investmentradar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.style.TextOverflow
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
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 560.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MEIN DEPOT", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (summary.currentValueComplete) formatLiveMoney(summary.currentValue) else "Nicht vollständig berechenbar",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        )
                        if (!summary.currentValueComplete && summary.currentValue > 0.0) {
                            Text(
                                "Berechenbarer Teil: ${formatLiveMoney(summary.currentValue)} · ${summary.calculablePositionCount} von ${summary.positionCount} Positionen",
                                color = muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(onClick = onOpenPortfolio, modifier = Modifier.fillMaxWidth()) {
                            Text("Öffnen", maxLines = 1, softWrap = false)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("MEIN DEPOT", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (summary.currentValueComplete) formatLiveMoney(summary.currentValue) else "Nicht vollständig berechenbar",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineMedium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            )
                            if (!summary.currentValueComplete && summary.currentValue > 0.0) {
                                Text(
                                    "Berechenbarer Teil: ${formatLiveMoney(summary.currentValue)} · ${summary.calculablePositionCount} von ${summary.positionCount} Positionen",
                                    color = muted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Button(onClick = onOpenPortfolio) {
                            Text("Öffnen", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            LivePortfolioValue("Einstand", if (summary.performanceComplete) formatLiveMoney(summary.costBasis) else "Nicht vollständig erfasst", muted)
            val shownProfitLoss = if (summary.performanceComplete) summary.profitLoss else summary.partialProfitLoss
            val shownProfitLossPct = if (summary.performanceComplete) summary.profitLossPct else summary.partialProfitLossPct
            LivePortfolioValue(
                "Gewinn / Verlust",
                if (shownProfitLoss != null && shownProfitLossPct != null) {
                    "${formatLiveSignedMoney(shownProfitLoss)} · ${formatLiveSignedPercent(shownProfitLossPct)}" +
                        if (!summary.performanceComplete) " · ${summary.performancePositionCount} von ${summary.positionCount}" else ""
                } else "Nicht berechenbar",
                if ((shownProfitLoss ?: 0.0) < 0.0) Color(0xFFFF6577) else accent
            )
            LivePortfolioValue("Positionen", summary.positionCount.toString(), muted)
            LivePortfolioValue(
                "Größte Position",
                if (largestName != null && summary.largestWeightPct != null) "$largestName · ${formatLivePercent(summary.largestWeightPct)}" else "–",
                muted
            )

            HorizontalDivider()
            summary.positions.forEach { row ->
                val name = itemById[row.itemId]?.name ?: customById[row.itemId]?.name ?: row.itemId
                val isFixedIncome = customById[row.itemId]?.type.equals("Festzins", ignoreCase = true)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (maxWidth < 560.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            Text(formatLiveMoney(row.currentValue), fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))
                            if (!isFixedIncome && row.currentPriceEur != null) {
                                Text("Kurs: ${formatLiveMoney(row.currentPriceEur)}", color = muted,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                            }
                            Text(formatLivePercent(row.weightPct), color = muted,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                softWrap = false, overflow = TextOverflow.Ellipsis)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatLiveMoney(row.currentValue), fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                                if (!isFixedIncome && row.currentPriceEur != null) {
                                    Text("Kurs: ${formatLiveMoney(row.currentPriceEur)}", color = muted,
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                                }
                                Text(formatLivePercent(row.weightPct), color = muted,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePortfolioValue(label: String, value: String, valueColor: Color) {
    ResponsiveFinanceValueRow(
        label = label,
        value = value,
        valueColor = valueColor,
        labelColor = Color(0xFF91A1B7)
    )
}

private fun formatLiveMoney(value: Double): String = String.format(Locale.GERMANY, "%.2f €", value)
private fun formatLiveSignedMoney(value: Double): String = String.format(Locale.GERMANY, "%+.2f €", value)
private fun formatLiveSignedPercent(value: Double): String = String.format(Locale.GERMANY, "%+.2f %%", value)
private fun formatLivePercent(value: Double): String = String.format(Locale.GERMANY, "%.1f %%", value)
