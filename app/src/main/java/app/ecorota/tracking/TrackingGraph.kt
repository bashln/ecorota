package app.ecorota.tracking

import app.ecorota.data.SettingsRepository
import app.ecorota.data.TripRepository
import app.ecorota.data.VehicleRepository
import kotlinx.coroutines.CoroutineScope

/** Repositories and the app-lifetime scope the tracking service and worker need. */
class TrackingDependencies(
    val tripRepository: TripRepository,
    val vehicleRepository: VehicleRepository,
    val settingsRepository: SettingsRepository,
    val applicationScope: CoroutineScope,
)

/**
 * Process-wide hand-off for the repositories used by components that Android
 * instantiates itself: [TripTrackingService] and [TripMaintenanceWorker].
 *
 * [TrackingControllerImpl] installs the graph when the UI builds the
 * controller, which covers every flow a user can trigger. A process that
 * starts without the UI (sticky service restart, periodic work after a kill)
 * finds an empty graph and those components stop instead of guessing.
 */
object TrackingGraph {
    @Volatile
    private var dependencies: TrackingDependencies? = null

    fun install(dependencies: TrackingDependencies) {
        this.dependencies = dependencies
    }

    fun get(): TrackingDependencies? = dependencies
}
