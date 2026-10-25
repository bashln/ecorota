package app.ecorota.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.ecorota.R
import app.ecorota.core.analysis.DrivingTip

data class TipText(val title: String, val body: String)

/** Maps the data-driven tip identifier to localized copy. */
@Composable
fun tipText(tip: DrivingTip): TipText = when (tip) {
    DrivingTip.SMOOTH_ACCELERATION -> TipText(
        title = stringResource(R.string.tip_smooth_acceleration_title),
        body = stringResource(R.string.tip_smooth_acceleration_body),
    )
    DrivingTip.ANTICIPATE_BRAKING -> TipText(
        title = stringResource(R.string.tip_anticipate_braking_title),
        body = stringResource(R.string.tip_anticipate_braking_body),
    )
    DrivingTip.EASE_THROUGH_CORNERS -> TipText(
        title = stringResource(R.string.tip_ease_through_corners_title),
        body = stringResource(R.string.tip_ease_through_corners_body),
    )
    DrivingTip.SLOW_HIGHWAY_SPEED -> TipText(
        title = stringResource(R.string.tip_slow_highway_speed_title),
        body = stringResource(R.string.tip_slow_highway_speed_body),
    )
    DrivingTip.AVOID_IDLING -> TipText(
        title = stringResource(R.string.tip_avoid_idling_title),
        body = stringResource(R.string.tip_avoid_idling_body),
    )
    DrivingTip.KEEP_STEADY_PACE -> TipText(
        title = stringResource(R.string.tip_keep_steady_pace_title),
        body = stringResource(R.string.tip_keep_steady_pace_body),
    )
}
