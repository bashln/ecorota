package app.ecorota.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * The two location streams tracking needs.
 *
 * Auto-detection only asks for a fix about once a minute and after 150 m of
 * displacement, which is what makes the always-on monitor cheap. Recording
 * trades battery for resolution.
 *
 * Google Play services is used when present. On de-Googled devices, common for
 * F-Droid users, the class falls back to the platform LocationManager so the
 * libre build can still record trips.
 */
class FusedLocationSource(
    context: Context,
    private val onFix: (Location) -> Unit,
) {
    enum class Profile(
        val priority: Int,
        val intervalMs: Long,
        val minDistanceMeters: Float,
        val maxDelayMs: Long,
    ) {
        MONITOR(
            priority = Priority.PRIORITY_LOW_POWER,
            intervalMs = 60_000L,
            minDistanceMeters = 150f,
            maxDelayMs = 0L,
        ),
        RECORDING(
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs = 3_000L,
            minDistanceMeters = 5f,
            maxDelayMs = 10_000L,
        ),
    }

    private enum class ActiveSource { NONE, FUSED, PLATFORM }

    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    private val fusedAvailable: Boolean by lazy {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
    }

    private var activeSource = ActiveSource.NONE

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(onFix)
        }
    }

    private val platformListener = LocationListener { location -> onFix(location) }

    fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts [profile] after removing any previous request. Returns false when
     * permission is missing, no provider is enabled, or the provider refused
     * the request.
     */
    fun start(profile: Profile): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Location permission is missing; not requesting updates")
            return false
        }
        stop()
        return if (fusedAvailable) startFused(profile) else startPlatform(profile)
    }

    @SuppressLint("MissingPermission") // Checked by hasPermission above.
    private fun startFused(profile: Profile): Boolean {
        val builder = LocationRequest.Builder(profile.priority, profile.intervalMs)
            .setMinUpdateDistanceMeters(profile.minDistanceMeters)
            .setWaitForAccurateLocation(false)
        if (profile.maxDelayMs > 0L) {
            builder.setMaxUpdateDelayMillis(profile.maxDelayMs)
        }

        return try {
            client.requestLocationUpdates(
                builder.build(),
                ContextCompat.getMainExecutor(appContext),
                fusedCallback,
            )
            activeSource = ActiveSource.FUSED
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Location updates rejected: permission revoked", error)
            false
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Fused location provider is not available", error)
            false
        }
    }

    @SuppressLint("MissingPermission") // Checked by hasPermission above.
    private fun startPlatform(profile: Profile): Boolean {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return false
        val provider = selectPlatformProvider(manager, profile) ?: run {
            Log.w(TAG, "No platform location provider is enabled")
            return false
        }
        return try {
            manager.requestLocationUpdates(
                provider,
                profile.intervalMs,
                profile.minDistanceMeters,
                platformListener,
                Looper.getMainLooper(),
            )
            activeSource = ActiveSource.PLATFORM
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Platform location updates rejected", error)
            false
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Platform provider rejected the request", error)
            false
        }
    }

    /**
     * Monitoring prefers the network provider because it is cheap and does not
     * need a sky view. Recording prefers GPS and falls back to network indoors.
     */
    private fun selectPlatformProvider(
        manager: LocationManager,
        profile: Profile,
    ): String? {
        val network = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val gps = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return when {
            profile == Profile.MONITOR && network -> LocationManager.NETWORK_PROVIDER
            hasFine && gps -> LocationManager.GPS_PROVIDER
            network -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    fun stop() {
        when (activeSource) {
            ActiveSource.FUSED -> try {
                client.removeLocationUpdates(fusedCallback)
            } catch (error: SecurityException) {
                Log.w(TAG, "Could not remove fused location updates", error)
            }
            ActiveSource.PLATFORM -> try {
                appContext.getSystemService(LocationManager::class.java)
                    ?.removeUpdates(platformListener)
            } catch (error: SecurityException) {
                Log.w(TAG, "Could not remove platform location updates", error)
            }
            ActiveSource.NONE -> Unit
        }
        activeSource = ActiveSource.NONE
    }

    private companion object {
        const val TAG = "FusedLocationSource"
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
