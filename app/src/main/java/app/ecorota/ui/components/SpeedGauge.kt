package app.ecorota.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ecorota.core.model.SpeedUnit
import app.ecorota.ui.format.Formatters
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Segmented speedometer used by the onboarding intro (and available for a live
 * speed view later). The value is stored in km/h; [speedUnit] only changes the
 * number and label on screen.
 */
@Composable
fun SpeedGauge(
    speedKmh: Double,
    speedUnit: SpeedUnit,
    locale: Locale,
    contentDescription: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 260.dp,
    maxKmh: Double = 160.0,
) {
    val target = (speedKmh / maxKmh).coerceIn(0.0, 1.0).toFloat()
    val progress = remember { Animatable(0f) }
    val duration = motionMillis(900)
    LaunchedEffect(target, duration) {
        if (duration == 0) {
            progress.snapTo(target)
        } else {
            progress.animateTo(target, animationSpec = tween(duration))
        }
    }

    val fontScale = LocalDensity.current.fontScale
    val effectiveDiameter = diameter * fontScale.coerceIn(1f, 1.2f)
    val sizeRatio = (diameter.value / 260f).coerceIn(0.65f, 1.15f)
    val fontDampener = (if (fontScale > 1.25f) 1.25f / fontScale else 1f) * sizeRatio

    val tickCount = 48
    val startAngle = 135f
    val sweep = 270f
    val inactive = MaterialTheme.colorScheme.outline.copy(alpha = 0.80f)
    val active = MaterialTheme.colorScheme.primary
    val marker = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .size(effectiveDiameter)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(effectiveDiameter)) {
            val radius = size.minDimension / 2f
            drawCircle(
                color = inactive.copy(alpha = 0.35f),
                radius = radius - 2.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            repeat(tickCount) { index ->
                val fraction = index.toFloat() / (tickCount - 1)
                val angle = startAngle + sweep * fraction
                val radians = angle * PI.toFloat() / 180f
                val outer = radius - 6.dp.toPx()
                val inner = outer - 18.dp.toPx()
                val start = Offset(
                    x = center.x + cos(radians) * inner,
                    y = center.y + sin(radians) * inner,
                )
                val end = Offset(
                    x = center.x + cos(radians) * outer,
                    y = center.y + sin(radians) * outer,
                )
                val color = if (fraction <= progress.value) active else inactive
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val needleAngle = startAngle + sweep * progress.value
            val radians = needleAngle * PI.toFloat() / 180f
            val outer = radius - 2.dp.toPx()
            val inner = outer - 26.dp.toPx()
            drawLine(
                color = marker,
                start = Offset(
                    x = center.x + cos(radians) * inner,
                    y = center.y + sin(radians) * inner,
                ),
                end = Offset(
                    x = center.x + cos(radians) * outer,
                    y = center.y + sin(radians) * outer,
                ),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = Formatters.speedValue(speedKmh, speedUnit)
                    .roundToInt()
                    .toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp * fontDampener,
                    lineHeight = 76.sp * fontDampener,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = Formatters.speedUnitLabel(speedUnit),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * fontDampener,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
