package app.ecorota.core.detection

/** Tunables for the low-power automatic trip start/stop detector. */
data class AutoTripConfig(
    val startSpeedKmh: Double = 20.0,
    val startConfirmMs: Long = 15_000,
    val stopSpeedKmh: Double = 4.0,
    val stopConfirmMs: Long = 150_000,
)

/**
 * Decides when a drive begins and ends from a sparse (about one fix per minute)
 * location stream. Keeping this pure makes the battery-friendly monitor easy
 * to test and keeps the foreground service thin.
 */
class TripDetector(
    private val config: AutoTripConfig = AutoTripConfig(),
) {
    enum class State { PARKED, DRIVING }

    sealed interface Signal {
        data object StartTrip : Signal
        data object StopTrip : Signal
    }

    var state: State = State.PARKED
        private set

    private var candidateSinceMs: Long? = null

    fun onFix(speedKmh: Double, timestampMs: Long): Signal? {
        val qualifies = when (state) {
            State.PARKED -> speedKmh >= config.startSpeedKmh
            State.DRIVING -> speedKmh <= config.stopSpeedKmh
        }

        if (!qualifies) {
            candidateSinceMs = null
            return null
        }

        val since = candidateSinceMs ?: timestampMs.also { candidateSinceMs = it }
        val required = when (state) {
            State.PARKED -> config.startConfirmMs
            State.DRIVING -> config.stopConfirmMs
        }
        if (timestampMs - since < required) return null

        candidateSinceMs = null
        return when (state) {
            State.PARKED -> {
                state = State.DRIVING
                Signal.StartTrip
            }
            State.DRIVING -> {
                state = State.PARKED
                Signal.StopTrip
            }
        }
    }

    fun reset() {
        state = State.PARKED
        candidateSinceMs = null
    }
}
