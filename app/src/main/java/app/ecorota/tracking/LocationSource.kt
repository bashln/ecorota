package app.ecorota.tracking

/** The two location streams tracking needs. */
enum class LocationProfile(
    val intervalMs: Long,
    val minDistanceMeters: Float,
) {
    /** Low-power fixes frequent enough to confirm movement without a large delay. */
    MONITOR(intervalMs = 30_000L, minDistanceMeters = 0f),

    /** Higher resolution while a trip is scored, including while stopped. */
    RECORDING(intervalMs = 3_000L, minDistanceMeters = 0f),
}

/**
 * Location provider seam. The play flavor uses the Fused Location Provider and
 * the libre flavor uses the platform LocationManager, so a de-Googled build has
 * no proprietary location code on the classpath.
 */
interface LocationSource {
    fun hasPermission(): Boolean

    fun start(profile: LocationProfile): Boolean

    fun stop()
}
