package de.tobias.investmentradar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ScoreBreakdownCard(item: InvestmentItem, modifier: Modifier = Modifier) {
    val coverageText = item.coverage?.let { "$it %" } ?: "Nicht verfügbar"
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Analyse V2", fontWeight = FontWeight.Black)
                Text("Datenabdeckung $coverageText", style = MaterialTheme.typography.labelMedium)
            }
            ScoreLine("Qualität", item.scoreQuality)
            ScoreLine("Bewertung", item.scoreValuation)
            ScoreLine("Wachstum", item.scoreGrowth)
            ScoreLine("Momentum", item.scoreMomentum)
            ScoreLine("Risiko", item.scoreRisk)
            if (item.recommendationReasons.isNotEmpty()) {
                Text(
                    item.recommendationReasons.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScoreLine(label: String, score: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                score?.let { "$it/100" } ?: "Nicht verfügbar",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (score != null) {
            LinearProgressIndicator(
                progress = { score.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
