package app.ecorota.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Animator scale is the only cross-platform signal Android gives us for
 * "remove animations" and it is what accessibility users actually set.
 */
@Composable
fun motionScale(): Float {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
    }
}

@Composable
fun motionMillis(baseMillis: Int): Int = (baseMillis * motionScale()).toInt()
