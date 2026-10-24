package app.ecorota.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import app.ecorota.EcoRotaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores automatic trip detection after a reboot. Android kills services on
 * reboot, but the user's opt-in is persisted, so the monitor is re-armed only
 * when it was enabled and the all-the-time location permission still exists.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val container = (appContext as? EcoRotaApp)?.container ?: return@launch
                val enabled = container.settingsRepository.current().autoDetectionEnabled
                if (enabled && TrackingControllerImpl.canRunAutoDetection(appContext)) {
                    ContextCompat.startForegroundService(
                        appContext,
                        Intent(appContext, TripTrackingService::class.java)
                            .setAction(TripTrackingService.ACTION_START_MONITOR),
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "Could not restore auto-detection after boot", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
