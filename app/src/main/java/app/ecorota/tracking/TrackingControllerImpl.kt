package app.ecorota.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.ecorota.data.SettingsRepository
import app.ecorota.data.TripRepository
import app.ecorota.data.VehicleRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Binds tracking to the rest of the app.
 *
 * The UI only talks to this class: it decides which service action to send,
 * keeps the auto-detection preference in sync, and reads the shared live
 * state. Building it also publishes the repositories the service needs, so a
 * UI that can start a trip always leaves the service able to finish it.
 */
class TrackingControllerImpl(
    context: Context,
    tripRepository: TripRepository,
    vehicleRepository: VehicleRepository,
    private val settingsRepository: SettingsRepository,
) : TrackingController {

    private val appContext = context.applicationContext

    init {
        TrackingGraph.install(
            TrackingDependencies(
                tripRepository = tripRepository,
                vehicleRepository = vehicleRepository,
                settingsRepository = settingsRepository,
            ),
        )
    }

    override val state: StateFlow<LiveTripState> get() = TrackingStateHolder.state

    override val monitoring: StateFlow<Boolean> get() = TrackingStateHolder.monitoring

    override suspend fun startRecording(manual: Boolean): Boolean {
        if (!canCheckLocationPermission(appContext)) return false
        dispatch(TripTrackingService.ACTION_START_RECORDING, manual)
        return true
    }

    override suspend fun stopRecording(): Boolean {
        dispatch(TripTrackingService.ACTION_STOP_RECORDING)
        return true
    }

    override suspend fun setAutoDetectionEnabled(enabled: Boolean) {
        settingsRepository.update { it.copy(autoDetectionEnabled = enabled) }
        if (enabled && canCheckLocationPermission(appContext)) {
            dispatch(TripTrackingService.ACTION_START_MONITOR)
        } else {
            dispatch(TripTrackingService.ACTION_STOP_ALL)
        }
    }

    override suspend fun refresh() {
        val enabled = settingsRepository.current().autoDetectionEnabled
        if (enabled && canCheckLocationPermission(appContext)) {
            dispatch(TripTrackingService.ACTION_START_MONITOR)
        } else {
            dispatch(TripTrackingService.ACTION_STOP_ALL)
        }
    }

    private fun dispatch(action: String, manual: Boolean = false) {
        val intent = Intent(appContext, TripTrackingService::class.java).setAction(action)
        if (action == TripTrackingService.ACTION_START_RECORDING) {
            intent.putExtra(TripTrackingService.EXTRA_MANUAL, manual)
        }
        try {
            ContextCompat.startForegroundService(appContext, intent)
        } catch (error: IllegalStateException) {
            // Background start restrictions: the foreground service cannot be
            // launched until the app is visible again.
            Log.w(TAG, "Could not start tracking for $action", error)
        } catch (error: SecurityException) {
            Log.w(TAG, "Missing permission to start tracking for $action", error)
        }
    }

    companion object {
        private const val TAG = "TrackingController"

        fun canCheckLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
