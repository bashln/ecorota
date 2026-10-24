package app.ecorota.tracking

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Registers the daily maintenance pass. Call once from application start; the
 * unique work name and [ExistingPeriodicWorkPolicy.KEEP] make repeated calls
 * harmless.
 */
class MaintenanceScheduler(private val context: Context) {

    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<TripMaintenanceWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        try {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TripMaintenanceWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } catch (error: IllegalStateException) {
            // WorkManager is initialised on demand by the Application; without
            // that configuration there is nothing to enqueue.
            Log.w(TAG, "WorkManager is not initialised; maintenance not scheduled", error)
        }
    }

    private companion object {
        const val TAG = "MaintenanceScheduler"
    }
}
