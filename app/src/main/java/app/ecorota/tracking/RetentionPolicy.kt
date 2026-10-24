package app.ecorota.tracking

/** Turns the retention preference into the cutoff used to delete old trips. */
internal object RetentionPolicy {
    const val MILLIS_PER_DAY = 86_400_000L

    /** Null when trips are kept forever. */
    fun cutoffMs(nowMs: Long, retentionDays: Int): Long? =
        if (retentionDays <= 0) null else nowMs - retentionDays * MILLIS_PER_DAY
}
