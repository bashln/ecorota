package app.ecorota.core.analysis

import app.ecorota.core.model.ScoreBreakdown
import app.ecorota.core.model.ScoreCategory

/**
 * Tips are identifiers, not text: the UI maps each one to a localized string.
 * Selection stays data-driven so the app can explain why a tip appeared.
 */
enum class DrivingTip {
    SMOOTH_ACCELERATION,
    ANTICIPATE_BRAKING,
    EASE_THROUGH_CORNERS,
    SLOW_HIGHWAY_SPEED,
    AVOID_IDLING,
    KEEP_STEADY_PACE,
}

data class SelectedTip(
    val tip: DrivingTip,
    val category: ScoreCategory?,
    /** Points this category removed from the trip score. */
    val lostPoints: Double,
)

object DrivingTips {

    private val thresholds = mapOf(
        ScoreCategory.ACCELERATION to 6.0,
        ScoreCategory.BRAKING to 6.0,
        ScoreCategory.CORNERING to 4.0,
        ScoreCategory.SPEED to 4.0,
        ScoreCategory.IDLING to 4.0,
    )

    private val tipByCategory = mapOf(
        ScoreCategory.ACCELERATION to DrivingTip.SMOOTH_ACCELERATION,
        ScoreCategory.BRAKING to DrivingTip.ANTICIPATE_BRAKING,
        ScoreCategory.CORNERING to DrivingTip.EASE_THROUGH_CORNERS,
        ScoreCategory.SPEED to DrivingTip.SLOW_HIGHWAY_SPEED,
        ScoreCategory.IDLING to DrivingTip.AVOID_IDLING,
    )

    fun forTrip(breakdown: ScoreBreakdown, limit: Int = 3): List<SelectedTip> {
        val selected = breakdown.biggestLosses()
            .filter { component ->
                val threshold = thresholds[component.category] ?: 0.0
                component.lostPoints >= threshold
            }
            .map { component ->
                SelectedTip(
                    tip = tipByCategory.getValue(component.category),
                    category = component.category,
                    lostPoints = component.lostPoints,
                )
            }
            .take(limit)

        if (selected.isNotEmpty()) return selected
        return listOf(
            SelectedTip(
                tip = DrivingTip.KEEP_STEADY_PACE,
                category = null,
                lostPoints = 0.0,
            ),
        )
    }
}
