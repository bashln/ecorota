package app.ecorota.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.ecorota.core.detection.MotionSample
import kotlin.math.sqrt

interface MotionSampler {
    /** False when the device has no accelerometer; nothing is registered then. */
    fun start(): Boolean
    fun stop()
}

fun createMotionSampler(
    context: Context,
    onSample: (MotionSample) -> Unit,
): MotionSampler = AndroidMotionSampler(context, onSample)

private class AndroidMotionSampler(
    context: Context,
    private val onSample: (MotionSample) -> Unit,
) : MotionSampler {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val projector = AccelerationProjector()
    private val rotationGate = RotationGate()

    private var accelerometerRegistered = false
    private var gyroscopeRegistered = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> onAcceleration(event.values, event.timestamp)
                Sensor.TYPE_GYROSCOPE -> onRotation(event.values, event.timestamp)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun start(): Boolean {
        val manager = sensorManager
        val accel = accelerometer
        if (manager == null || accel == null) return false

        projector.reset()
        rotationGate.reset()
        accelerometerRegistered = manager.registerListener(
            listener,
            accel,
            SensorManager.SENSOR_DELAY_NORMAL,
        )
        gyroscopeRegistered = gyroscope?.let {
            manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: false
        return accelerometerRegistered
    }

    override fun stop() {
        if (!accelerometerRegistered && !gyroscopeRegistered) return
        sensorManager?.unregisterListener(listener)
        accelerometerRegistered = false
        gyroscopeRegistered = false
        projector.reset()
        rotationGate.reset()
    }

    private fun onRotation(values: FloatArray, timestampNanos: Long) {
        if (values.size < 3) return
        val x = values[0]
        val y = values[1]
        val z = values[2]
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
        rotationGate.onRotation(
            rateRadPerSec = sqrt(x * x + y * y + z * z).toDouble(),
            timestampNanos = timestampNanos,
        )
    }

    private fun onAcceleration(values: FloatArray, timestampNanos: Long) {
        if (values.size < 3) return
        val x = values[0]
        val y = values[1]
        val z = values[2]
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
        if (!rotationGate.accepts(timestampNanos)) return

        val magnitude = projector.horizontalMagnitude(x, y, z) ?: return
        onSample(MotionSample(timestampNanos = timestampNanos, magnitudeMps2 = magnitude))
    }
}
