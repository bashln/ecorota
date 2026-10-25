package app.ecorota.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ecorota.R
import app.ecorota.ui.format.DateFormats
import java.util.Locale

/**
 * Seven day trend: bars for avoidable cost, line for the average score. Days
 * without trips have no bar and break the score line instead of pretending to
 * be a zero score.
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    locale: Locale,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val barColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxCost = points.maxOf { it.avoidableCost }.takeIf { it > 0.0 } ?: 1.0

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics { this.contentDescription = contentDescription },
        ) {
            val cellWidth = size.width / points.size
            val barWidth = cellWidth * 0.46f
            val barAreaHeight = size.height * 0.45f
            val barBottom = size.height
            drawLine(
                color = gridColor,
                start = Offset(0f, barBottom - barAreaHeight),
                end = Offset(size.width, barBottom - barAreaHeight),
                strokeWidth = 1.dp.toPx(),
            )

            points.forEachIndexed { index, point ->
                val left = cellWidth * index + (cellWidth - barWidth) / 2f
                if (point.avoidableCost > 0.0) {
                    val barHeight = (point.avoidableCost / maxCost * barAreaHeight).toFloat()
                    drawRoundRect(
                        color = if (index == points.lastIndex) barColor else barColor.copy(alpha = 0.55f),
                        topLeft = Offset(left, barBottom - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }

            val scoreTop = size.height * 0.06f
            val scoreAreaHeight = size.height * 0.40f
            var previous: Offset? = null
            points.forEachIndexed { index, point ->
                val score = point.averageScore
                if (score == null || point.tripCount == 0) {
                    previous = null
                    return@forEachIndexed
                }
                val x = cellWidth * index + cellWidth / 2f
                val y = scoreTop + ((100.0 - score) / 100.0 * scoreAreaHeight).toFloat()
                val current = Offset(x, y)
                val start = previous
                if (start != null) {
                    drawLine(
                        color = lineColor,
                        start = start,
                        end = current,
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = current)
                previous = current
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    text = DateFormats.shortDay(point.epochDay, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(color = barColor.copy(alpha = 0.55f))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.history_chart_legend_cost),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            LegendDot(color = lineColor)
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.history_chart_legend_score),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Spacer(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}
