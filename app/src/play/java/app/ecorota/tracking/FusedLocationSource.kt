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
 * Auto-detection asks for low-power fixes about every 30 seconds. Recording
 * trades battery for resolution and keeps receiving fixes while stationary so
 * the trip detector can confirm that the car has parked.
 *
 * Google Play services is used when present. On de-Googled devices, common for
 * F-Droid users, the class falls back to the platform LocationManager so the
 * libre build can still record trips.
 */
class FusedLocationSource(
    context: Context,
    private val onFix: (Location) -> Unit,
) : LocationSource {

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

    override fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts [profile] after removing any previous request. Returns false when
     * permission is missing, no provider is enabled, or the provider refused
     * the request.
     */
    override fun start(profile: LocationProfile): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Location permission is missing; not requesting updates")
            return false
        }
        stop()
        return if (fusedAvailable) startFused(profile) else startPlatform(profile)
    }

    @SuppressLint("MissingPermission") // Checked by hasPermission above.
    private fun startFused(profile: LocationProfile): Boolean {
        // Low power answers about once every few minutes with a city-level
        // accuracy and no usable speed, which is why auto-detection never
        // noticed a drive. Both profiles now ask for balanced power, which
        // still defers to the low-power radio indoors but honours the interval.
        val builder = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            profile.intervalMs,
        )
            .setMinUpdateDistanceMeters(profile.minDistanceMeters)
            .setWaitForAccurateLocation(false)
        if (profile == LocationProfile.RECORDING) {
            builder.setMaxUpdateDelayMillis(10_000L)
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
    private fun startPlatform(profile: LocationProfile): Boolean {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return false
        val provider = selectPlatformProvider(manager) ?: run {
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
     * GPS first: the trip detector compares speed against thresholds and only
     * GPS reports one. The network provider is the fallback while GPS is off.
     */
    private fun selectPlatformProvider(
        manager: LocationManager,
    ): String? {
        val network = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val gps = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return when {
            hasFine && gps -> LocationManager.GPS_PROVIDER
            network -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    override fun stop() {
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
