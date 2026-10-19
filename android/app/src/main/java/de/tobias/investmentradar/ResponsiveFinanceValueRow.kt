package de.tobias.investmentradar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Responsive financial field: never word-wrap amounts, percentages or field labels.
 *
 * On phones, label and value have their own full-width lines. On large screens,
 * both stay on one row. Long values remain horizontally scrollable rather than
 * losing digits to ellipses. The field labels are ellipsized only on large layouts.
 */
@Composable
fun ResponsiveFinanceValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 560.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    color = labelColor,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                )
                Text(
                    text = value,
                    color = valueColor,
                    fontWeight = FontWeight.Bold,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = label,
                    color = labelColor,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = value,
                    color = valueColor,
                    fontWeight = FontWeight.Bold,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}
