package app.ecorota.tracking

/** Decides whether a finished trip is worth persisting. */
internal object TripRecordingPolicy {
    const val MIN_DISTANCE_METERS = 200.0
    const val MIN_DURATION_MS = 60_000L

    /**
     * Trips shorter than this are noise: a parking manoeuvre, a fix taken while
     * idling, or a user who tapped record by mistake. They are dropped instead
     * of polluting the history with a zero score.
     */
    fun shouldDiscard(distanceMeters: Double, durationMs: Long): Boolean =
        distanceMeters < MIN_DISTANCE_METERS || durationMs < MIN_DURATION_MS
}
