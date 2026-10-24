package app.ecorota.tracking

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Daily housekeeping: apply the retention window and keep the tile cache
 * bounded. Both are local-only operations and neither is urgent, so this runs
 * on WorkManager's schedule instead of at app start.
 */
class TripMaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dependencies = TrackingGraph.get()
        if (dependencies == null) {
            Log.w(TAG, "Maintenance skipped: repositories are not installed in this process")
            return Result.success()
        }

        return try {
            val settings = dependencies.settingsRepository.current()
            RetentionPolicy.cutoffMs(System.currentTimeMillis(), settings.dataRetentionDays)
                ?.let { cutoff ->
                    val removed = dependencies.tripRepository.deleteTripsEndingBefore(cutoff)
                    if (removed > 0) Log.i(TAG, "Retention removed $removed trips")
                }

            val freed = TileCachePruner.prune(File(applicationContext.cacheDir, TILE_CACHE_DIRECTORY))
            if (freed > 0) Log.i(TAG, "Pruned $freed bytes from the tile cache")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Trip maintenance failed", error)
            // A periodic worker that returns failure is cancelled for good, which
            // would silently disable retention and cache pruning.
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "ecorota-trip-maintenance"
        private const val TAG = "TripMaintenance"

        /** osmdroid's default layout below the app cache directory. */
        private const val TILE_CACHE_DIRECTORY = "osmdroid/tiles"
    }
}
