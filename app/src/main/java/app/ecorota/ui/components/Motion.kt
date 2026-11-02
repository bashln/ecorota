package app.ecorota.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

fun Context.animatorDurationScale(): Float = runCatching {
    Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
}.getOrDefault(1f)

fun Context.isReducedMotion(): Boolean = animatorDurationScale() <= 0f

/**
 * Animator scale is the only cross-platform signal Android gives us for
 * "remove animations" and it is what accessibility users actually set.
 * Refreshed on resume so toggling Remove Animations in system settings takes
 * effect without restarting the process.
 */
@Composable
fun motionScale(): Float {
    val context = LocalContext.current
    var scale by remember(context) { mutableFloatStateOf(context.animatorDurationScale()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scale = context.animatorDurationScale()
    }
    return scale
}

/**
 * Compose already scales non-zero tween durations by MotionDurationScale, so
 * multiplying by [motionScale] would apply the factor twice. Returning 0 when
 * animations are disabled lets callers snap immediately without double-scaling.
 */
@Composable
fun motionMillis(baseMillis: Int): Int = if (motionScale() <= 0f) 0 else baseMillis
