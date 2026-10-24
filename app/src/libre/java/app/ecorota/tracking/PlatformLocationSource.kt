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

/**
 * Platform location provider for the community build. It has no Google Play
 * services dependency; monitoring prefers the network provider because it is
 * cheap, and recording prefers GPS because the scoring engine needs speed and
 * heading resolution.
 */
class PlatformLocationSource(
    context: Context,
    private val onFix: (Location) -> Unit,
) : LocationSource {

    private val appContext = context.applicationContext
    private val manager: LocationManager? =
        appContext.getSystemService(LocationManager::class.java)

    private var activeProvider: String? = null

    private val listener = LocationListener { location -> onFix(location) }

    override fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // Checked by hasPermission.
    override fun start(profile: LocationProfile): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Location permission is missing; not requesting updates")
            return false
        }
        val locationManager = manager ?: return false
        stop()

        val hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val provider = when {
            profile == LocationProfile.MONITOR && network -> LocationManager.NETWORK_PROVIDER
            hasFine && gps -> LocationManager.GPS_PROVIDER
            network -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: run {
            Log.w(TAG, "No platform location provider is enabled")
            return false
        }

        return try {
            locationManager.requestLocationUpdates(
                provider,
                profile.intervalMs,
                profile.minDistanceMeters,
                listener,
                Looper.getMainLooper(),
            )
            activeProvider = provider
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Platform location updates rejected", error)
            false
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Platform provider rejected the request", error)
            false
        }
    }

    override fun stop() {
        val provider = activeProvider ?: return
        activeProvider = null
        try {
            manager?.removeUpdates(listener)
        } catch (error: SecurityException) {
            Log.w(TAG, "Could not remove platform location updates for $provider", error)
        }
    }

    private companion object {
        const val TAG = "PlatformLocationSource"
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
