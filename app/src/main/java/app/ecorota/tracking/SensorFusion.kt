package app.ecorota.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/** Peak vehicle-frame acceleration since the previous [SensorFusion.consume]. */
data class SensorPeaks(
    /**
     * Signed peak of the longitudinal axis in m/s², keeping the sign of the
     * largest sample so braking stays negative. Null when no usable sample
     * arrived, or when there is no GPS heading yet.
     */
    val forwardMps2: Double?,
    /** Peak lateral acceleration magnitude in m/s². */
    val lateralMps2: Double?,
)

/**
 * Vehicle-frame acceleration from the accelerometer alone.
 *
 * A magnetometer would need an extra sensor round trip and drifts inside a
 * metal cabin, so yaw is never measured: the mount is assumed fixed and the
 * projector only compensates tilt. Peaks are collected between location fixes
 * and drained by the service, which feeds them to the analyzer.
 *
 * Callbacks and [consume] both run on the main thread, so the peak store needs
 * no locking.
 */
class SensorFusion(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val projector = AccelerationProjector()
    private val peaks = PeakTracker()
    private val rotationGate = RotationGate()

    @Volatile
    private var headingDeg: Double? = null

    private var running = false
    private var accelerometerRegistered = false
    private var gyroscopeRegistered = false

    /** False on a device without an accelerometer; tracking then relies on GPS speed. */
    val available: Boolean get() = accelerometer != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> onAcceleration(event.values, event.timestamp)
                Sensor.TYPE_GYROSCOPE -> rotationGate.onRotation(
                    rateRadPerSec = sqrt(
                        event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2],
                    ).toDouble(),
                    timestampNanos = event.timestamp,
                )
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (running) return
        val manager = sensorManager ?: return
        val accel = accelerometer ?: return
        headingDeg = null
        projector.reset()
        peaks.reset()
        rotationGate.reset()
        accelerometerRegistered =
            manager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        gyroscopeRegistered = gyroscope?.let {
            manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: false
        running = accelerometerRegistered || gyroscopeRegistered
    }

    fun stop() {
        if (!accelerometerRegistered && !gyroscopeRegistered) return
        running = false
        sensorManager?.unregisterListener(listener)
        accelerometerRegistered = false
        gyroscopeRegistered = false
        peaks.reset()
        headingDeg = null
    }

    /** Keeps the last course over ground as the vehicle heading; null is ignored. */
    fun setHeading(bearingDeg: Double?) {
        val bearing = bearingDeg ?: return
        if (bearing.isNaN() || bearing.isInfinite()) return
        this.headingDeg = bearing
    }

    /**
     * Drains the peaks measured since the last call. Both values are null until
     * GPS reports a bearing, which is exactly when the analyzer should fall
     * back to acceleration derived from location speed.
     */
    fun consume(): SensorPeaks {
        val drained = peaks.consume()
        return if (headingDeg == null) SensorPeaks(null, null) else drained
    }

    private fun onAcceleration(values: FloatArray, timestampNanos: Long) {
        if (!running) return
        if (!rotationGate.accepts(timestampNanos)) return
        val projected = projector.project(values[0], values[1], values[2]) ?: return
        peaks.record(projected)
    }
}

/** Acceleration in the vehicle frame: forward positive while speeding up. */
internal data class AxisAcceleration(
    val forwardMps2: Double,
    val lateralMps2: Double,
)

/**
 * Projects device-frame acceleration on the vehicle axes.
 *
 * Gravity is tracked with a low-pass filter and removed, then the remainder is
 * flattened onto the plane perpendicular to gravity, so a tilted holder does
 * not leak its own weight into the longitudinal axis.
 *
 * Yaw is unobservable without a magnetometer, so the forward axis is assumed
 * from the mount: device -Z when the screen faces the driver, device +Y when
 * the phone lies flat with its top pointing at the windshield. The first
 * candidate that is not nearly vertical wins; if neither is usable the sample
 * is dropped and the analyzer falls back to GPS.
 */
internal class AccelerationProjector(
    private val gravityAlpha: Float = GRAVITY_ALPHA,
) {
    private val gravity = FloatArray(3)
    private var primed = false

    fun reset() {
        gravity.fill(0f)
        primed = false
    }

    fun project(x: Float, y: Float, z: Float): AxisAcceleration? {
        if (!primed) {
            gravity[0] = x
            gravity[1] = y
            gravity[2] = z
            primed = true
            return null
        }

        gravity[0] = gravityAlpha * gravity[0] + (1f - gravityAlpha) * x
        gravity[1] = gravityAlpha * gravity[1] + (1f - gravityAlpha) * y
        gravity[2] = gravityAlpha * gravity[2] + (1f - gravityAlpha) * z

        val upLength = sqrt(
            gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2],
        )
        if (upLength < MIN_GRAVITY_MPS2) return null

        val upX = gravity[0] / upLength
        val upY = gravity[1] / upLength
        val upZ = gravity[2] / upLength

        var linearX = x - gravity[0]
        var linearY = y - gravity[1]
        var linearZ = z - gravity[2]
        val vertical = linearX * upX + linearY * upY + linearZ * upZ
        linearX -= vertical * upX
        linearY -= vertical * upY
        linearZ -= vertical * upZ

        val forward = horizontalAxis(upX, upY, upZ) ?: return null
        val rightX = forward[1] * upZ - forward[2] * upY
        val rightY = forward[2] * upX - forward[0] * upZ
        val rightZ = forward[0] * upY - forward[1] * upX

        return AxisAcceleration(
            forwardMps2 = (linearX * forward[0] + linearY * forward[1] + linearZ * forward[2])
                .coerceIn(-MAX_ACCELERATION_MPS2, MAX_ACCELERATION_MPS2)
                .toDouble(),
            lateralMps2 = abs(linearX * rightX + linearY * rightY + linearZ * rightZ)
                .coerceIn(0f, MAX_ACCELERATION_MPS2)
                .toDouble(),
        )
    }

    private fun horizontalAxis(upX: Float, upY: Float, upZ: Float): FloatArray? {
        for (candidate in FORWARD_CANDIDATES) {
            val projection = candidate[0] * upX + candidate[1] * upY + candidate[2] * upZ
            var axisX = candidate[0] - projection * upX
            var axisY = candidate[1] - projection * upY
            var axisZ = candidate[2] - projection * upZ
            val length = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
            if (length < MIN_AXIS_ALIGNMENT) continue
            axisX /= length
            axisY /= length
            axisZ /= length
            return floatArrayOf(axisX, axisY, axisZ)
        }
        return null
    }

    private companion object {
        const val GRAVITY_ALPHA = 0.95f
        const val MIN_GRAVITY_MPS2 = 1.0f
        const val MIN_AXIS_ALIGNMENT = 0.5f
        const val MAX_ACCELERATION_MPS2 = 12f

        val FORWARD_CANDIDATES = arrayOf(
            // Screen faces the driver: away from the user is the windshield.
            floatArrayOf(0f, 0f, -1f),
            // Lying flat, top of the phone pointing forward.
            floatArrayOf(0f, 1f, 0f),
        )
    }
}

/** Keeps the extreme of each axis between drains. */
internal class PeakTracker {
    private var forward: Double? = null
    private var lateral: Double? = null

    fun record(sample: AxisAcceleration) {
        forward = extreme(forward, sample.forwardMps2)
        lateral = extreme(lateral, sample.lateralMps2)
    }

    fun consume(): SensorPeaks {
        val drainedForward = forward
        val drainedLateral = lateral
        forward = null
        lateral = null
        return SensorPeaks(drainedForward, drainedLateral)
    }

    fun reset() {
        forward = null
        lateral = null
    }

    private fun extreme(current: Double?, candidate: Double): Double {
        val existing = current ?: return candidate
        return if (abs(candidate) > abs(existing)) candidate else existing
    }
}

/**
 * Drops acceleration samples while the phone itself is being turned or
 * handled: the gravity estimate is meaningless during that movement, and the
 * resulting numbers would look like driving events.
 */
internal class RotationGate(
    private val maxAngularRateRadPerSec: Double = MAX_ANGULAR_RATE,
    private val holdOffNanos: Long = HOLD_OFF_NANOS,
) {
    private var quietUntilNanos = 0L

    fun onRotation(rateRadPerSec: Double, timestampNanos: Long) {
        if (rateRadPerSec.isNaN() || rateRadPerSec < 0.0) return
        if (rateRadPerSec >= maxAngularRateRadPerSec) {
            quietUntilNanos = timestampNanos + holdOffNanos
        }
    }

    fun accepts(timestampNanos: Long): Boolean = timestampNanos >= quietUntilNanos

    fun reset() {
        quietUntilNanos = 0L
    }

    companion object {
        const val MAX_ANGULAR_RATE = 2.5
        const val HOLD_OFF_NANOS = 500_000_000L
    }
}
