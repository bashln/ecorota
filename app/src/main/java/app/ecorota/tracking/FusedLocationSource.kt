package app.ecorota.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
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

    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }
    private var active = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(onFix)
        }
    }

    fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts [profile] after removing any previous request. Returns false when
     * permission is missing or the provider refused the request.
     */
    @SuppressLint("MissingPermission") // Checked by hasPermission above.
    fun start(profile: Profile): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Location permission is missing; not requesting updates")
            return false
        }
        stop()

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
                callback,
            )
            active = true
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Location updates rejected: permission revoked", error)
            false
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Location provider is not available on this device", error)
            false
        }
    }

    fun stop() {
        if (!active) return
        active = false
        try {
            client.removeLocationUpdates(callback)
        } catch (error: SecurityException) {
            Log.w(TAG, "Could not remove location updates", error)
        }
    }

    private companion object {
        const val TAG = "FusedLocationSource"
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
