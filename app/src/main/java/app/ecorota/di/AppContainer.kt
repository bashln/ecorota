package app.ecorota.di

import android.content.Context
import app.ecorota.BuildConfig
import app.ecorota.billing.BillingProvider
import app.ecorota.billing.createBillingProvider
import app.ecorota.data.EntitlementRepository
import app.ecorota.data.LocalPremiumRepository
import app.ecorota.data.PremiumRepository
import app.ecorota.data.PremiumSource
import app.ecorota.data.SettingsRepository
import app.ecorota.data.TripRepository
import app.ecorota.data.VehicleRepository
import app.ecorota.data.local.EcoRotaDatabase
import app.ecorota.data.repository.RoomTripRepository
import app.ecorota.data.repository.RoomVehicleRepository
import app.ecorota.data.settings.DataStoreSettingsRepository
import app.ecorota.export.DefaultTripExporter
import app.ecorota.export.TripExporter
import app.ecorota.tracking.MaintenanceScheduler
import app.ecorota.tracking.TrackingController
import app.ecorota.tracking.TrackingControllerImpl
import app.ecorota.tracking.TrackingDependencies
import app.ecorota.tracking.TrackingGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual object graph. One instance lives in [app.ecorota.EcoRotaApp] and is
 * shared by the activity and the tracking service, so both use the same
 * database and the same entitlement state.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Scope for application-lifetime work such as the community premium grant. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: EcoRotaDatabase by lazy { EcoRotaDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext)
    }

    val tripRepository: TripRepository by lazy {
        RoomTripRepository(database)
    }

    val vehicleRepository: VehicleRepository by lazy {
        RoomVehicleRepository(database)
    }

    val premiumRepository: PremiumRepository by lazy {
        LocalPremiumRepository(appContext)
    }

    val entitlementRepository: EntitlementRepository get() = premiumRepository

    val billingProvider: BillingProvider by lazy {
        createBillingProvider(appContext, premiumRepository)
    }

    val trackingController: TrackingController by lazy {
        TrackingControllerImpl(
            context = appContext,
            tripRepository = tripRepository,
            vehicleRepository = vehicleRepository,
            settingsRepository = settingsRepository,
            applicationScope = applicationScope,
        )
    }

    val tripExporter: TripExporter by lazy { DefaultTripExporter() }

    init {
        // Android instantiates the service and the worker itself, so they cannot
        // receive constructor dependencies. Publish the graph before any of them
        // can start, including a sticky restart after a process kill.
        TrackingGraph.install(
            TrackingDependencies(
                tripRepository = tripRepository,
                vehicleRepository = vehicleRepository,
                settingsRepository = settingsRepository,
                applicationScope = applicationScope,
            ),
        )
        MaintenanceScheduler(appContext).schedule()
        if (BuildConfig.BILLING_ENABLED) {
            // Restores a purchase that completed while the app was not running
            // and retries an acknowledgement that failed earlier.
            applicationScope.launch { billingProvider.restorePurchase() }
        } else {
            // The community build has no billing SDK. Premium is a local grant.
            applicationScope.launch {
                premiumRepository.setPremium(true, PremiumSource.COMMUNITY_BUILD)
            }
        }
        applicationScope.launch {
            if (settingsRepository.current().autoDetectionEnabled) {
                trackingController.refresh()
            }
        }
    }
}
