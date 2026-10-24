package app.ecorota.core.model

/** One fused-location fix, optionally enriched with gravity-corrected sensor data. */
data class TelemetrySample(
    val timestampMs: Long,
    val point: GeoPoint,
    /** Ground speed in meters per second. */
    val speedMps: Double,
    /** Course over ground in degrees, when the fix reports one. */
    val bearingDeg: Double? = null,
    val accuracyMeters: Double = 10.0,
    /** Forward (longitudinal) acceleration in m/s², positive while speeding up. */
    val accelForwardMps2: Double? = null,
    /** Lateral acceleration magnitude in m/s², produced by cornering. */
    val accelLateralMps2: Double? = null,
)

data class SpeedSample(
    val timestampMs: Long,
    val speedKmh: Double,
)

/** A point in the route that also carries the speed at that moment. */
data class RoutePoint(
    val timestampMs: Long,
    val point: GeoPoint,
    val speedKmh: Double,
)
