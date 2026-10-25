package app.ecorota.data.local

import app.ecorota.core.model.TripListItem

/**
 * History row. It selects only the columns the list renders, so opening the
 * history never loads routes or events.
 */
data class TripListProjection(
    val id: String,
    val vehicleId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceMeters: Double,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val score: Int,
    val eventCount: Int,
    val avoidableLiters: Double,
    val avoidableCost: Double,
    val currency: String,
    val manualStart: Boolean,
) {
    fun toTripListItem(): TripListItem = TripListItem(
        id = id,
        vehicleId = vehicleId,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        distanceMeters = distanceMeters,
        averageSpeedKmh = averageSpeedKmh,
        maxSpeedKmh = maxSpeedKmh,
        score = score,
        eventCount = eventCount,
        avoidableLiters = avoidableLiters,
        avoidableCost = avoidableCost,
        currency = currency,
        manualStart = manualStart,
    )
}
