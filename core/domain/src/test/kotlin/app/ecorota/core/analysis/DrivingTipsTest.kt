package app.ecorota.core.analysis

import app.ecorota.core.model.ScoreBreakdown
import app.ecorota.core.model.ScoreCategory
import app.ecorota.core.model.ScoreComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingTipsTest {

    @Test
    fun `aggressive trips get tips ordered by lost points`() {
        val tips = DrivingTips.forTrip(
            breakdown(
                ScoreCategory.ACCELERATION to 8.0,
                ScoreCategory.BRAKING to 12.0,
                ScoreCategory.CORNERING to 5.0,
                ScoreCategory.SPEED to 1.0,
                ScoreCategory.IDLING to 0.0,
            ),
        )

        assertEquals(
            listOf(
                DrivingTip.ANTICIPATE_BRAKING,
                DrivingTip.SMOOTH_ACCELERATION,
                DrivingTip.EASE_THROUGH_CORNERS,
            ),
            tips.map { it.tip },
        )
        assertEquals(
            listOf(ScoreCategory.BRAKING, ScoreCategory.ACCELERATION, ScoreCategory.CORNERING),
            tips.map { it.category },
        )
        assertEquals(listOf(12.0, 8.0, 5.0), tips.map { it.lostPoints })
        assertEquals(tips.map { it.lostPoints }.sortedDescending(), tips.map { it.lostPoints })
    }

    @Test
    fun `a clean trip yields the steady pace tip`() {
        val tips = DrivingTips.forTrip(
            breakdown(
                ScoreCategory.ACCELERATION to 1.0,
                ScoreCategory.BRAKING to 1.0,
                ScoreCategory.CORNERING to 1.0,
                ScoreCategory.SPEED to 0.0,
                ScoreCategory.IDLING to 0.0,
            ),
        )

        val tip = tips.single()
        assertEquals(DrivingTip.KEEP_STEADY_PACE, tip.tip)
        assertNull(tip.category)
        assertEquals(0.0, tip.lostPoints, 0.0)
    }

    @Test
    fun `the limit caps the number of tips`() {
        val breakdown = breakdown(
            ScoreCategory.ACCELERATION to 9.0,
            ScoreCategory.BRAKING to 8.0,
            ScoreCategory.CORNERING to 7.0,
            ScoreCategory.SPEED to 6.0,
            ScoreCategory.IDLING to 5.0,
        )

        val tips = DrivingTips.forTrip(breakdown, limit = 2)

        assertEquals(2, tips.size)
        assertEquals(listOf(DrivingTip.SMOOTH_ACCELERATION, DrivingTip.ANTICIPATE_BRAKING), tips.map { it.tip })
        assertEquals(5, DrivingTips.forTrip(breakdown, limit = 9).size)
    }

    @Test
    fun `a category exactly at its threshold is included`() {
        val tips = DrivingTips.forTrip(
            breakdown(
                ScoreCategory.ACCELERATION to 5.9,
                ScoreCategory.BRAKING to 6.0,
                ScoreCategory.CORNERING to 4.0,
                ScoreCategory.SPEED to 4.0,
                ScoreCategory.IDLING to 4.0,
            ),
        )

        assertEquals(
            listOf(
                DrivingTip.ANTICIPATE_BRAKING,
                DrivingTip.EASE_THROUGH_CORNERS,
                DrivingTip.SLOW_HIGHWAY_SPEED,
            ),
            tips.map { it.tip },
        )
        assertTrue(tips.all { it.lostPoints >= 4.0 })
    }

    @Test
    fun `fewer than the limit returns only the qualifying tips`() {
        val tips = DrivingTips.forTrip(
            breakdown(
                ScoreCategory.ACCELERATION to 0.0,
                ScoreCategory.BRAKING to 7.0,
                ScoreCategory.CORNERING to 0.0,
                ScoreCategory.SPEED to 0.0,
                ScoreCategory.IDLING to 0.0,
            ),
        )

        assertEquals(DrivingTip.ANTICIPATE_BRAKING, tips.single().tip)
        assertEquals(ScoreCategory.BRAKING, tips.single().category)
    }

    private fun breakdown(vararg losses: Pair<ScoreCategory, Double>): ScoreBreakdown {
        val components = losses.map { (category, lostPoints) ->
            ScoreComponent(
                category = category,
                score = (100.0 - lostPoints * 5.0).toInt().coerceIn(0, 100),
                weight = 0.2,
                eventCount = 0,
                weightedEventCount = 0.0,
                lostPoints = lostPoints,
            )
        }
        return ScoreBreakdown(
            components = components,
            behaviorScore = 50,
            fuelScore = 100,
            fuelWeight = 0.2,
            overallScore = 60,
        )
    }
}
