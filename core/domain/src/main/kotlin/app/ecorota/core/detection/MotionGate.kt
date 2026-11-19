package app.ecorota.core.detection

/** One low-power motion reading: |linear acceleration| on the sensor clock. */
data class MotionSample(
    val timestampNanos: Long,
    val magnitudeMps2: Double,
)

/** Tunables for the motion gate. Defaults assume roughly a 12 Hz accelerometer. */
data class MotionGateConfig(
    val motionThresholdMps2: Double = 0.45,
    val confirmSamples: Int = 12,
    val quietSamples: Int = 90,
    val cooldownMs: Long = 8_000,
    val maxSampleGapMs: Long = 1_000,
)

/** Level, not edge, so callers can apply it idempotently. */
enum class LocationDemand { OFF, ON }

/**
 * Decides from a cheap motion stream when accurate location should run, so a
 * stationary phone requests no fixes.
 */
class MotionGate(private val config: MotionGateConfig = MotionGateConfig()) {

    private enum class Phase { STILL, SUSPECT, ACTIVE }

    private var phase = Phase.STILL
    private var confirmCount = 0
    private var quietCount = 0
    private var activeSinceNanos = 0L
    private var lastTimestampNanos: Long? = null

    fun onSample(s: MotionSample): LocationDemand {
        if (!s.magnitudeMps2.isFinite() || s.magnitudeMps2 < 0.0) return demand()

        val last = lastTimestampNanos
        if (last != null && s.timestampNanos - last > config.maxSampleGapMs * 1_000_000L) {
            reset()
            lastTimestampNanos = s.timestampNanos
            return LocationDemand.OFF
        }
        lastTimestampNanos = s.timestampNanos

        val moving = s.magnitudeMps2 >= config.motionThresholdMps2
        when (phase) {
            Phase.STILL -> {
                if (!moving) {
                    confirmCount = 0
                } else if (++confirmCount >= config.confirmSamples) {
                    phase = Phase.SUSPECT
                    confirmCount = 0
                    quietCount = 0
                }
            }
            Phase.SUSPECT -> {
                if (moving) {
                    phase = Phase.ACTIVE
                    activeSinceNanos = s.timestampNanos
                    confirmCount = 0
                    quietCount = 0
                } else {
                    phase = Phase.STILL
                    confirmCount = 0
                }
            }
            Phase.ACTIVE -> {
                if (moving) {
                    quietCount = 0
                } else if (++quietCount >= config.quietSamples &&
                    s.timestampNanos - activeSinceNanos >= config.cooldownMs * 1_000_000L
                ) {
                    phase = Phase.STILL
                    quietCount = 0
                }
            }
        }
        return demand()
    }

    fun demand(): LocationDemand =
        if (phase == Phase.ACTIVE) LocationDemand.ON else LocationDemand.OFF

    fun reset() {
        phase = Phase.STILL
        confirmCount = 0
        quietCount = 0
        activeSinceNanos = 0L
        lastTimestampNanos = null
    }
}
