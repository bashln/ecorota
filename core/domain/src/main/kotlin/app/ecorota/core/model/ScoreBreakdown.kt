package app.ecorota.core.model

enum class ScoreCategory {
    ACCELERATION,
    BRAKING,
    CORNERING,
    SPEED,
    IDLING,
}

/**
 * One explainable part of the trip score.
 *
 * [score] is a 0..100 sub-score. [lostPoints] is how many points that category
 * removed from the 100 point behavior score (`(100 - score) * weight`), so the
 * UI can show exactly which behavior cost the most.
 */
data class ScoreComponent(
    val category: ScoreCategory,
    val score: Int,
    val weight: Double,
    val eventCount: Int,
    val weightedEventCount: Double,
    val lostPoints: Double,
)

data class ScoreBreakdown(
    val components: List<ScoreComponent>,
    val behaviorScore: Int,
    val fuelScore: Int,
    val fuelWeight: Double,
    val overallScore: Int,
) {
    fun component(category: ScoreCategory): ScoreComponent? =
        components.firstOrNull { it.category == category }

    /** Components ordered by how many points they removed. */
    fun biggestLosses(): List<ScoreComponent> =
        components.sortedByDescending { it.lostPoints }
}
