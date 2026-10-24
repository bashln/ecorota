package app.ecorota.data.local

import app.ecorota.core.model.PolylineCodec
import app.ecorota.core.model.RoutePoint
import app.ecorota.core.model.SpeedSample

/**
 * Column codecs for the trip route and its speed samples.
 *
 * Both formats are plain text so a trip row stays a single row and the values
 * remain readable in `adb` or a SQLite browser. Decoding never throws: a
 * malformed token is dropped, because losing one sample is better than losing
 * the whole trip from the history screen.
 */
object TripCodecs {

    private const val SPEED_SERIES_SEPARATOR = ';'
    private const val SPEED_SAMPLE_SEPARATOR = ':'

    /** Encodes samples as `t:speed;t:speed`. */
    fun encodeSpeedSeries(samples: List<SpeedSample>): String =
        samples.joinToString(separator = SPEED_SERIES_SEPARATOR.toString()) { sample ->
            "${sample.timestampMs}$SPEED_SAMPLE_SEPARATOR${sample.speedKmh}"
        }

    fun decodeSpeedSeries(raw: String): List<SpeedSample> {
        if (raw.isEmpty()) return emptyList()
        return raw.split(SPEED_SERIES_SEPARATOR).mapNotNull { token ->
            val parts = token.trim().split(SPEED_SAMPLE_SEPARATOR, limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val timestampMs = parts[0].trim().toLongOrNull() ?: return@mapNotNull null
            val speedKmh = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            if (!speedKmh.isFinite()) return@mapNotNull null
            SpeedSample(timestampMs = timestampMs, speedKmh = speedKmh)
        }
    }

    /**
     * Rebuilds the route from its two columns.
     *
     * The polyline carries geometry only, so a trip written by [TripMappers]
     * has one speed sample per route point and the original timestamps come
     * back exactly. When the two counts disagree (older or hand-edited rows)
     * timestamps are spread evenly across the trip and each point takes the
     * speed sample at the equivalent position, which keeps the shape and the
     * speed profile usable instead of dropping the route.
     */
    fun decodeRoute(
        routePolyline: String,
        speedSeries: String,
        startedAtMs: Long,
        endedAtMs: Long,
    ): List<RoutePoint> {
        val points = PolylineCodec.decode(routePolyline)
        if (points.isEmpty()) return emptyList()

        val samples = decodeSpeedSeries(speedSeries)
        if (samples.size == points.size) {
            return points.mapIndexed { index, point ->
                val sample = samples[index]
                RoutePoint(
                    timestampMs = sample.timestampMs,
                    point = point,
                    speedKmh = sample.speedKmh,
                )
            }
        }

        val lastIndex = points.lastIndex
        val denominator = lastIndex.coerceAtLeast(1)
        val span = (endedAtMs - startedAtMs).coerceAtLeast(0L)
        return points.mapIndexed { index, point ->
            val speedKmh = if (samples.isEmpty()) {
                0.0
            } else {
                samples[index * samples.lastIndex / denominator].speedKmh
            }
            RoutePoint(
                timestampMs = startedAtMs + span * index / denominator,
                point = point,
                speedKmh = speedKmh,
            )
        }
    }

    /** Speed series derived from route points, the only speed data a trip record keeps. */
    fun speedSeriesOf(route: List<RoutePoint>): String =
        encodeSpeedSeries(route.map { SpeedSample(timestampMs = it.timestampMs, speedKmh = it.speedKmh) })
}
