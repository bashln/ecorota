package app.ecorota.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.ecorota.data.SettingsRepository
import app.ecorota.data.TripRepository
import app.ecorota.data.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
    applicationScope: CoroutineScope,
) : TrackingController {

    private val appContext = context.applicationContext

    init {
        TrackingGraph.install(
            TrackingDependencies(
                tripRepository = tripRepository,
                vehicleRepository = vehicleRepository,
                settingsRepository = settingsRepository,
                applicationScope = applicationScope,
            ),
        )
    }

    override val state: StateFlow<LiveTripState> get() = TrackingStateHolder.state

    override val monitoring: StateFlow<Boolean> get() = TrackingStateHolder.monitoring

    override suspend fun startRecording(manual: Boolean): Boolean {
        if (!canCheckLocationPermission(appContext)) return false
        dispatch(TripTrackingService.ACTION_START_RECORDING, manual)
        // The service owns permission and foreground-service checks. Wait for it
        // to publish the recording state so the UI does not claim success when
        // Android refused the background start.
        return withTimeoutOrNull(START_CONFIRM_TIMEOUT_MS) {
            state.first { it.recording }
        } != null
    }

    override suspend fun stopRecording(): Boolean {
        dispatch(TripTrackingService.ACTION_STOP_RECORDING)
        return true
    }

    override suspend fun setAutoDetectionEnabled(enabled: Boolean) {
        if (enabled && !canRunAutoDetection(appContext)) {
            // The switch only turns on after the disclosure and a granted
            // background permission. Revoked permission disables it again.
            settingsRepository.update { it.copy(autoDetectionEnabled = false) }
            stopMonitoringOnly()
            return
        }
        settingsRepository.update { it.copy(autoDetectionEnabled = enabled) }
        if (enabled) {
            dispatch(TripTrackingService.ACTION_START_MONITOR)
        } else {
            stopMonitoringOnly()
        }
    }

    override suspend fun refresh() {
        val enabled = settingsRepository.current().autoDetectionEnabled
        if (enabled && canRunAutoDetection(appContext)) {
            dispatch(TripTrackingService.ACTION_START_MONITOR)
            return
        }
        if (enabled) {
            settingsRepository.update { it.copy(autoDetectionEnabled = false) }
        }
        stopMonitoringOnly()
    }

    /**
     * Stops the low-power monitor without touching an active recording. A user
     * who turns auto-detection off during a manual trip still wants that trip.
     */
    private fun stopMonitoringOnly() {
        if (state.value.recording) {
            dispatch(TripTrackingService.ACTION_STOP_MONITOR)
        } else {
            appContext.stopService(Intent(appContext, TripTrackingService::class.java))
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
        private const val START_CONFIRM_TIMEOUT_MS = 4_000L

        fun canCheckLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        /**
         * Auto-detection runs while the app is not visible, so the user must
         * have granted all-the-time location on Android 10 and newer.
         */
        fun canRunAutoDetection(context: Context): Boolean =
            canCheckLocationPermission(context) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED)
    }
}
