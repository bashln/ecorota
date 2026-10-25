package app.ecorota.tracking

/** The two location streams tracking needs. */
enum class LocationProfile(
    val intervalMs: Long,
    val minDistanceMeters: Float,
) {
    /** Cheap fixes used only to notice that a drive started or ended. */
    MONITOR(intervalMs = 60_000L, minDistanceMeters = 150f),

    /** Higher resolution while a trip is being scored. */
    RECORDING(intervalMs = 3_000L, minDistanceMeters = 5f),
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
