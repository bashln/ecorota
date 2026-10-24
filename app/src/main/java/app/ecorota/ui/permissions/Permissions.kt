package app.ecorota.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.ecorota.R

@Stable
data class AppPermissionsState(
    val hasFineLocation: Boolean,
    val hasBackgroundLocation: Boolean,
    val hasNotifications: Boolean,
    val requestForegroundLocation: () -> Unit,
    val requestOnboardingAccess: () -> Unit,
)

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Recording needs a precise fix; coarse-only access is not enough. */
fun Context.hasFineLocationPermission(): Boolean =
    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

fun Context.hasBackgroundLocationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        hasPermission(Manifest.permission.POST_NOTIFICATIONS)

/**
 * Current permission state plus the launchers that change it. The state is
 * recomputed on resume so a grant made in system settings is picked up without
 * restarting the app.
 */
@Composable
fun rememberAppPermissions(): AppPermissionsState {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }

    val foreground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { revision++ }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }

    return remember(revision, context) {
        AppPermissionsState(
            hasFineLocation = context.hasFineLocationPermission(),
            hasBackgroundLocation = context.hasBackgroundLocationPermission(),
            hasNotifications = context.hasNotificationPermission(),
            requestForegroundLocation = {
                foreground.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
            requestOnboardingAccess = {
                val permissions = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions += Manifest.permission.POST_NOTIFICATIONS
                }
                foreground.launch(permissions.toTypedArray())
            },
        )
    }
}

fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/**
 * Opt-in for automatic trip detection.
 *
 * Background location is only requested after the caller shows the disclosure
 * dialog below, which is an Android 10+ requirement and a privacy promise of
 * this app. On Android 11+ the system may answer without a dialog; in that case
 * the user gets instructions for "Allow all the time" instead of a dead switch.
 */
@Composable
fun rememberAutoDetectionOptIn(onEnable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var showBackgroundDisclosure by remember { mutableStateOf(false) }
    var showSettingsHint by remember { mutableStateOf(false) }
    var showLocationDenied by remember { mutableStateOf(false) }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onEnable()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showSettingsHint = true
        } else {
            showLocationDenied = true
        }
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            showBackgroundDisclosure = true
        } else {
            showLocationDenied = true
        }
    }

    val requestEnable: () -> Unit = {
        if (context.hasFineLocationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                showBackgroundDisclosure = true
            } else {
                onEnable()
            }
        } else {
            foregroundLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    if (showBackgroundDisclosure) {
        AlertDialog(
            onDismissRequest = { showBackgroundDisclosure = false },
            title = { Text(stringResource(R.string.auto_detection_background_title)) },
            text = { Text(stringResource(R.string.auto_detection_background_disclosure)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackgroundDisclosure = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (context.hasBackgroundLocationPermission()) {
                                onEnable()
                            } else {
                                backgroundLauncher.launch(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                )
                            }
                        } else {
                            onEnable()
                        }
                    },
                ) {
                    Text(stringResource(R.string.auto_detection_background_allow))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBackgroundDisclosure = false },
                ) {
                    Text(stringResource(R.string.common_not_now))
                }
            },
        )
    }

    if (showSettingsHint) {
        AlertDialog(
            onDismissRequest = { showSettingsHint = false },
            title = { Text(stringResource(R.string.auto_detection_settings_title)) },
            text = { Text(stringResource(R.string.auto_detection_settings_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSettingsHint = false
                        openAppSettings(context)
                    },
                ) {
                    Text(stringResource(R.string.common_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsHint = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }

    if (showLocationDenied) {
        AlertDialog(
            onDismissRequest = { showLocationDenied = false },
            title = { Text(stringResource(R.string.location_permission_denied_title)) },
            text = { Text(stringResource(R.string.location_permission_denied_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLocationDenied = false
                        openAppSettings(context)
                    },
                ) {
                    Text(stringResource(R.string.common_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDenied = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }

    return requestEnable
}
