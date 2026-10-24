package app.ecorota.core.scoring

import app.ecorota.core.model.DetectedEvent
import app.ecorota.core.model.DrivingEventType
import app.ecorota.core.model.GeoMath
import app.ecorota.core.model.GeoPoint
import app.ecorota.core.model.RoutePoint
import app.ecorota.core.model.SpeedSample
import app.ecorota.core.model.TelemetrySample
import app.ecorota.core.model.TripAnalysis
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Streaming analyzer that turns telemetry into events and aggregates.
 *
 * It is a deliberately plain state machine so it can be unit tested without
 * Android. The tracker calls [onSample] for every location fix and [finish]
 * when the trip ends. Events stay open until the condition that opened them
 * goes away, which is why a 1 Hz feed still produces useful durations.
 */
class TripAnalyzer(config: ScoringConfig = ScoringConfig()) {
    private val config: ScoringConfig = config.sanitized()

    private var startMs: Long? = null
    private var lastSampleMs: Long? = null
    private var lastPoint: GeoPoint? = null
    private var lastSpeedMps: Double = 0.0
    private var lastBearingDeg: Double? = null
    private var lastSpeedKmh: Double = 0.0

    private var distanceMeters = 0.0
    private var movingMs = 0L
    private var idleMs = 0L
    private var maxSpeedKmh = 0.0

    private val route = mutableListOf<RoutePoint>()
    private val speedSamples = mutableListOf<SpeedSample>()
    private val events = mutableListOf<DetectedEvent>()
    private val active = mutableMapOf<DrivingEventType, PendingEvent>()
    private val lastEmittedAt = mutableMapOf<DrivingEventType, Long>()

    private var idleStartMs: Long? = null
    private var idleStartPoint: GeoPoint? = null
    private var lastSpeedSampleMs = 0L

    val isStarted: Boolean get() = startMs != null

    fun onSample(sample: TelemetrySample): TripAnalysis {
        val timestamp = sample.timestampMs
        if (startMs == null) startMs = timestamp

        val previousMs = lastSampleMs
        if (previousMs != null && timestamp <= previousMs) return snapshot(timestamp)

        val dtMs = if (previousMs == null) 0L else timestamp - previousMs
        val dtSec = dtMs / 1000.0
        val boundedDt = dtMs.coerceAtMost(MAX_GAP_MS)
        val speedKmh = sample.speedMps * 3.6

        // A long gap means the app was suspended or lost the fix. Close what is
        // open at the last known sample instead of stretching it over the gap.
        if (previousMs != null && dtMs > MAX_GAP_MS) {
            closeIdle(previousMs)
            idleStartMs = null
            for (pending in active.values.toList()) {
                if (pending.lastUpdatedMs - pending.startedAtMs >= minimumDurationFor(pending.type)) {
                    emit(pending, pending.lastUpdatedMs)
                }
            }
            active.clear()
        }

        if (previousMs != null) {
            if (speedKmh >= config.idleSpeedThresholdKmh) {
                movingMs += boundedDt
            } else {
                idleMs += boundedDt
            }
        }

        accumulateDistance(sample, dtSec)
        updateIdle(timestamp, speedKmh, sample.point)
        updateRoute(timestamp, sample, speedKmh)
        updateSpeedSamples(timestamp, speedKmh)

        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        lastSpeedKmh = speedKmh

        val forward = sample.accelForwardMps2?.coerceIn(-MAX_ACCEL, MAX_ACCEL)
            ?: derivedForwardAcceleration(sample, dtSec)
        val lateral = sample.accelLateralMps2?.let { abs(it).coerceIn(0.0, MAX_ACCEL) }
            ?: derivedLateralAcceleration(sample, dtSec)

        if (forward != null) {
            // A sensor peak covers the time since the previous fix. Backdating the
            // event start by that window keeps short real maneuvers from being
            // discarded by the minimum-duration filter.
            val forwardWindowMs = if (sample.accelForwardMps2 != null) {
                boundedDt.coerceAtMost(MAX_SENSOR_WINDOW_MS)
            } else {
                0L
            }
            updateEvent(
                type = DrivingEventType.HARD_ACCELERATION,
                condition = forward >= config.hardAccelThresholdMps2,
                magnitude = abs(forward),
                timestamp = timestamp,
                sample = sample,
                windowMs = forwardWindowMs,
            )
            updateEvent(
                type = DrivingEventType.HARD_BRAKING,
                condition = forward <= -config.hardBrakeThresholdMps2,
                magnitude = abs(forward),
                timestamp = timestamp,
                sample = sample,
                windowMs = forwardWindowMs,
            )
        }
        if (lateral != null) {
            val lateralWindowMs = if (sample.accelLateralMps2 != null) {
                boundedDt.coerceAtMost(MAX_SENSOR_WINDOW_MS)
            } else {
                0L
            }
            updateEvent(
                type = DrivingEventType.SHARP_CORNERING,
                condition = lateral >= config.sharpCornerThresholdMps2,
                magnitude = lateral,
                timestamp = timestamp,
                sample = sample,
                windowMs = lateralWindowMs,
            )
        }
        updateOverspeed(timestamp, speedKmh, sample)

        lastSampleMs = timestamp
        lastPoint = sample.point
        lastSpeedMps = sample.speedMps
        lastBearingDeg = sample.bearingDeg
        return snapshot(timestamp)
    }

    /** Closes every open event and returns the final analysis. */
    fun finish(endedAtMs: Long? = null): TripAnalysis {
        val end = max(endedAtMs ?: lastSampleMs ?: startMs ?: 0L, lastSampleMs ?: 0L)
        closeIdle(end)
        val pendingEvents = active.values.toList()
        active.clear()
        for (pending in pendingEvents) {
            val minimum = minimumDurationFor(pending.type)
            if (pending.lastUpdatedMs - pending.startedAtMs >= minimum) {
                emit(pending, pending.lastUpdatedMs)
            }
        }
        return snapshot(end)
    }

    fun snapshot(): TripAnalysis = snapshot(lastSampleMs ?: startMs ?: 0L)

    private fun snapshot(nowMs: Long): TripAnalysis {
        val start = startMs ?: nowMs
        val end = max(nowMs, start)
        val inProgress = active.values
            .filter { it.lastUpdatedMs - it.startedAtMs >= minimumDurationFor(it.type) }
            .map { it.toDetected(it.lastUpdatedMs) }
        val allEvents = (events + inProgress).sortedBy { it.startedAtMs }
        return TripAnalysis(
            startedAtMs = start,
            endedAtMs = end,
            distanceMeters = distanceMeters,
            movingDurationMs = movingMs,
            idleDurationMs = idleMs,
            maxSpeedKmh = maxSpeedKmh,
            events = allEvents,
            route = route.toList(),
            speedSamples = speedSamples.toList(),
            lastSpeedKmh = lastSpeedKmh,
        )
    }

    private fun accumulateDistance(sample: TelemetrySample, dtSec: Double) {
        val previous = lastPoint ?: return
        if (sample.speedMps < MIN_MOVING_SPEED_MPS) return
        if (sample.accuracyMeters > config.maxAccuracyForDistanceMeters) return
        val distance = GeoMath.distanceMeters(previous, sample.point)
        // Rejects GPS jumps without discarding legitimate fast movement.
        val plausible = distance <= max(150.0, sample.speedMps * max(dtSec, 1.0) * 4.0 + 50.0)
        if (plausible) distanceMeters += distance
    }

    private fun updateRoute(timestamp: Long, sample: TelemetrySample, speedKmh: Double) {
        if (sample.accuracyMeters > MAX_ROUTE_ACCURACY_METERS) return
        val lastRoutePoint = route.lastOrNull()
        val shouldAdd = lastRoutePoint == null ||
            GeoMath.distanceMeters(lastRoutePoint.point, sample.point) >= ROUTE_MIN_SPACING_METERS
        if (shouldAdd) {
            route += RoutePoint(timestamp, sample.point, speedKmh)
        }
    }

    private fun updateSpeedSamples(timestamp: Long, speedKmh: Double) {
        if (speedSamples.isNotEmpty() && timestamp - lastSpeedSampleMs < config.speedSampleIntervalMs) {
            return
        }
        speedSamples += SpeedSample(timestamp, speedKmh)
        lastSpeedSampleMs = timestamp
    }

    private fun updateIdle(timestamp: Long, speedKmh: Double, point: GeoPoint) {
        if (speedKmh < config.idleSpeedThresholdKmh) {
            if (idleStartMs == null) {
                idleStartMs = timestamp
                idleStartPoint = point
            }
            return
        }
        val start = idleStartMs ?: return
        idleStartMs = null
        val totalIdle = timestamp - start
        if (totalIdle < config.idleMinDurationMs) return
        val avoidable = totalIdle - config.idleGraceMs
        if (avoidable <= 0) return
        emitIdle(start + config.idleGraceMs, timestamp, avoidable, idleStartPoint)
        idleStartPoint = null
    }

    private fun closeIdle(endMs: Long) {
        val start = idleStartMs ?: return
        idleStartMs = null
        val totalIdle = endMs - start
        if (totalIdle < config.idleMinDurationMs) return
        val avoidable = totalIdle - config.idleGraceMs
        if (avoidable > 0) emitIdle(start + config.idleGraceMs, endMs, avoidable, idleStartPoint)
    }

    private fun emitIdle(fromMs: Long, toMs: Long, durationMs: Long, point: GeoPoint?) {
        val seconds = durationMs / 1000.0
        events += DetectedEvent(
            type = DrivingEventType.IDLING,
            startedAtMs = fromMs,
            endedAtMs = toMs,
            peakValue = seconds,
            meanValue = seconds,
            startSpeedKmh = 0.0,
            endSpeedKmh = 0.0,
            location = point,
        )
        lastEmittedAt[DrivingEventType.IDLING] = toMs
    }

    private fun updateEvent(
        type: DrivingEventType,
        condition: Boolean,
        magnitude: Double,
        timestamp: Long,
        sample: TelemetrySample,
        windowMs: Long = 0L,
    ) {
        val pending = active[type]
        if (condition) {
            if (pending == null) {
                val lastAt = lastEmittedAt[type]
                val coolingDown = lastAt != null && timestamp - lastAt < config.eventCooldownMs
                if (!coolingDown) {
                    val speedKmh = sample.speedMps * 3.6
                    val earliestStart = startMs ?: timestamp
                    active[type] = PendingEvent(
                        type = type,
                        startedAtMs = (timestamp - windowMs).coerceAtLeast(earliestStart),
                        lastUpdatedMs = timestamp,
                        peak = magnitude,
                        valueSum = magnitude,
                        valueCount = 1,
                        startSpeedKmh = speedKmh,
                        endSpeedKmh = speedKmh,
                        startPoint = sample.point,
                    )
                }
            } else {
                if (magnitude > pending.peak) pending.peak = magnitude
                pending.valueSum += magnitude
                pending.valueCount += 1
                pending.lastUpdatedMs = timestamp
                pending.endSpeedKmh = sample.speedMps * 3.6
            }
            return
        }

        if (pending == null) return
        active.remove(type)
        if (pending.lastUpdatedMs - pending.startedAtMs >= minimumDurationFor(type)) {
            emit(pending, pending.lastUpdatedMs)
        }
    }

    private fun updateOverspeed(timestamp: Long, speedKmh: Double, sample: TelemetrySample) {
        val pending = active[DrivingEventType.OVERSPEED]
        val above = speedKmh >= config.overspeedThresholdKmh
        if (above) {
            if (pending == null) {
                val lastAt = lastEmittedAt[DrivingEventType.OVERSPEED]
                val coolingDown = lastAt != null && timestamp - lastAt < config.eventCooldownMs
                if (!coolingDown) {
                    active[DrivingEventType.OVERSPEED] = PendingEvent(
                        type = DrivingEventType.OVERSPEED,
                        startedAtMs = timestamp,
                        lastUpdatedMs = timestamp,
                        peak = speedKmh,
                        valueSum = speedKmh,
                        valueCount = 1,
                        startSpeedKmh = speedKmh,
                        endSpeedKmh = speedKmh,
                        startPoint = sample.point,
                    )
                }
            } else {
                if (speedKmh > pending.peak) pending.peak = speedKmh
                pending.valueSum += speedKmh
                pending.valueCount += 1
                pending.lastUpdatedMs = timestamp
                pending.endSpeedKmh = speedKmh
            }
            return
        }

        // Hysteresis avoids generating several events per km around the limit.
        if (pending != null && speedKmh <= config.overspeedThresholdKmh - config.overspeedHysteresisKmh) {
            active.remove(DrivingEventType.OVERSPEED)
            val duration = pending.lastUpdatedMs - pending.startedAtMs
            if (duration >= config.overspeedMinDurationMs) emit(pending, pending.lastUpdatedMs)
        }
    }

    private fun emit(pending: PendingEvent, closeAtMs: Long) {
        events += pending.toDetected(closeAtMs)
        lastEmittedAt[pending.type] = closeAtMs
    }

    private fun minimumDurationFor(type: DrivingEventType): Long = when (type) {
        DrivingEventType.OVERSPEED -> config.overspeedMinDurationMs
        DrivingEventType.IDLING -> config.idleMinDurationMs
        else -> config.eventMinDurationMs
    }

    private fun derivedForwardAcceleration(sample: TelemetrySample, dtSec: Double): Double? {
        if (dtSec < MIN_DERIVATIVE_WINDOW_SEC || dtSec > MAX_DERIVATIVE_WINDOW_SEC) return null
        return ((sample.speedMps - lastSpeedMps) / dtSec).coerceIn(-MAX_ACCEL, MAX_ACCEL)
    }

    private fun derivedLateralAcceleration(sample: TelemetrySample, dtSec: Double): Double? {
        if (dtSec < MIN_DERIVATIVE_WINDOW_SEC || dtSec > MAX_DERIVATIVE_WINDOW_SEC) return null
        if (sample.speedMps < MIN_SPEED_FOR_BEARING_CORNER_MPS) return null
        val previousBearing = lastBearingDeg ?: return null
        val currentBearing = sample.bearingDeg ?: return null
        val delta = GeoMath.bearingDeltaDegrees(previousBearing, currentBearing)
        val angularRateRad = Math.toRadians(delta) / dtSec
        return (sample.speedMps * abs(angularRateRad)).coerceIn(0.0, MAX_ACCEL)
    }

    private class PendingEvent(
        val type: DrivingEventType,
        val startedAtMs: Long,
        var lastUpdatedMs: Long,
        var peak: Double,
        var valueSum: Double,
        var valueCount: Int,
        val startSpeedKmh: Double,
        var endSpeedKmh: Double,
        val startPoint: GeoPoint,
    ) {
        fun toDetected(closeAtMs: Long): DetectedEvent {
            val mean = if (valueCount > 0) valueSum / valueCount else peak
            return DetectedEvent(
                type = type,
                startedAtMs = startedAtMs,
                endedAtMs = closeAtMs,
                peakValue = peak,
                meanValue = mean,
                startSpeedKmh = startSpeedKmh,
                endSpeedKmh = endSpeedKmh,
                location = startPoint,
            )
        }
    }

    private companion object {
        const val MAX_GAP_MS = 10_000L
        const val MAX_SENSOR_WINDOW_MS = 3_000L
        const val MIN_MOVING_SPEED_MPS = 1.0
        const val MIN_SPEED_FOR_BEARING_CORNER_MPS = 4.0
        const val MIN_DERIVATIVE_WINDOW_SEC = 0.4
        const val MAX_DERIVATIVE_WINDOW_SEC = 5.0
        const val MAX_ACCEL = 10.0
        const val MAX_ROUTE_ACCURACY_METERS = 100.0
        const val ROUTE_MIN_SPACING_METERS = 8.0
    }
}
