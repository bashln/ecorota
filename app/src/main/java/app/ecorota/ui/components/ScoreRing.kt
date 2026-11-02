package app.ecorota.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ecorota.R
import app.ecorota.ui.theme.ScoreBand
import app.ecorota.ui.theme.scoreColor

/**
 * Circular 0..100 score gauge. A null score renders the empty track, which is
 * what the home screen shows before the first trip.
 */
@Composable
fun ScoreRing(
    score: Int?,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 208.dp,
    strokeWidth: Dp = 16.dp,
) {
    val fraction = ((score ?: 0).coerceIn(0, 100)) / 100f
    val animation = motionMillis(650)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = animation),
        label = "score",
    )
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)
    val progressColor = scoreColor(score)
    val fontScale = LocalDensity.current.fontScale
    val effectiveSize = size * fontScale.coerceIn(1f, 1.25f)
    val textDampener = if (fontScale > 1.3f) 1.3f / fontScale else 1f
    val baseNumberStyle = if (size < 160.dp) {
        MaterialTheme.typography.displayMedium
    } else {
        MaterialTheme.typography.displayLarge
    }
    val numberStyle = baseNumberStyle.copy(
        fontSize = baseNumberStyle.fontSize * textDampener,
        lineHeight = baseNumberStyle.lineHeight * textDampener,
    )
    val baseLabelStyle = if (size < 160.dp) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelLarge
    }
    val labelStyle = baseLabelStyle.copy(
        fontSize = baseLabelStyle.fontSize * textDampener,
        lineHeight = baseLabelStyle.lineHeight * textDampener,
    )

    Box(
        modifier = modifier
            .size(effectiveSize)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(effectiveSize)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0.001f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            modifier = Modifier.padding(strokeWidth + 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = score?.toString() ?: stringResource(R.string.common_no_value),
                style = numberStyle,
                color = if (score == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    progressColor
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Localized band name, or null when there is no score yet. */
@Composable
fun scoreBandLabel(score: Int?): String? {
    val band = ScoreBand.from(score) ?: return null
    return stringResource(
        when (band) {
            ScoreBand.EXCELLENT -> R.string.score_band_excellent
            ScoreBand.GOOD -> R.string.score_band_good
            ScoreBand.FAIR -> R.string.score_band_fair
            ScoreBand.POOR -> R.string.score_band_poor
            ScoreBand.BAD -> R.string.score_band_bad
        },
    )
}
