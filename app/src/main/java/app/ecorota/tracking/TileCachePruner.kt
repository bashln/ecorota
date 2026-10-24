package app.ecorota.tracking

import java.io.File

/**
 * Keeps the map tile cache bounded. Tiles are disposable, but deleting all of
 * them on every run would make the map re-download what the user already paid
 * for in bandwidth, so only the oldest files go and only when the cache has
 * outgrown the budget.
 */
internal object TileCachePruner {
    const val MAX_BYTES = 50L * 1024 * 1024
    private const val TARGET_RATIO = 0.8

    /** Deletes the least recently modified tiles until the cache fits. Returns bytes freed. */
    fun prune(directory: File, maxBytes: Long = MAX_BYTES): Long {
        if (!directory.isDirectory) return 0
        val files = directory.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return 0

        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return 0

        val target = (maxBytes * TARGET_RATIO).toLong()
        var freed = 0L
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= target) break
            val length = file.length()
            if (file.delete()) {
                total -= length
                freed += length
            }
        }
        return freed
    }
}
