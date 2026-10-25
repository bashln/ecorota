package app.ecorota.core.model

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A WGS-84 coordinate. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** Spherical geometry helpers used by the analyzer and the map layer. */
object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Great-circle distance in meters between two coordinates. */
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val sinLat = sin(deltaLat / 2)
        val sinLon = sin(deltaLon / 2)
        val a = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing in degrees [0, 360) from [from] to [to]. */
    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Smallest absolute difference between two bearings, in degrees. */
    fun bearingDeltaDegrees(from: Double, to: Double): Double {
        val delta = abs(to - from) % 360.0
        return if (delta > 180.0) 360.0 - delta else delta
    }
}
