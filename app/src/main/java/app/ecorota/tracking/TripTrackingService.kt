package app.ecorota.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import app.ecorota.R
import app.ecorota.core.detection.TripDetector
import app.ecorota.core.model.GeoMath
import app.ecorota.core.model.GeoPoint
import app.ecorota.core.model.ScoringVersion
import app.ecorota.core.model.TelemetrySample
import app.ecorota.core.model.TripAnalysis
import app.ecorota.core.model.TripRecord
import app.ecorota.core.model.VehicleProfile
import app.ecorota.core.scoring.ScoringConfig
import app.ecorota.core.scoring.TripAnalyzer
import app.ecorota.core.scoring.TripScorer
import java.util.UUID
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground owner of the location stream.
 *
 * It has two modes that never run together: monitoring, a low-power stream that
 * asks the detector whether a drive started, and recording, which feeds
 * locations and accelerometer peaks to [TripAnalyzer] and publishes the live
 * state. Everything the user sees is posted as a low-importance, ongoing
 * notification so Android keeps the process alive while driving.
 */
class TripTrackingService : Service() {

    private enum class Mode { IDLE, MONITORING, RECORDING }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Tracking coroutine failed", error)
        },
    )

    private lateinit var locationSource: FusedLocationSource
    private var sensorFusion: SensorFusion? = null
    private var analyzer: TripAnalyzer? = null
    private var detector: TripDetector? = null
    private var lastFix: Location? = null

    private var mode = Mode.IDLE
    private var automatic = false
    private var autoDetectionEnabled = false
    private var vehicle: VehicleProfile = VehicleProfile.default()
    private var scoringConfig: ScoringConfig = ScoringConfig()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationSource = FusedLocationSource(this) { location -> onFix(location) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent is the system restarting a sticky service after the
        // process was killed: the only thing worth resuming is monitoring.
        val action = intent?.action
        if (action == null) {
            resumeMonitoringAfterRestart()
            return START_STICKY
        }

        when (action) {
            ACTION_START_MONITOR -> beginMonitoring()
            ACTION_START_RECORDING -> beginRecording(intent.getBooleanExtra(EXTRA_MANUAL, false))
            ACTION_STOP_RECORDING -> when (mode) {
                Mode.RECORDING -> endRecording(resumeMonitoring = true)
                Mode.MONITORING -> startForegroundCompat(monitorNotification())
                Mode.IDLE -> stopForegroundAndSelf()
            }
            ACTION_STOP_MONITOR -> stopMonitoring()
            ACTION_STOP_ALL -> stopTracing()
            else -> if (mode == Mode.IDLE) {
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        releaseRecording()
        detector = null
        locationSource.stop()
        TrackingStateHolder.reset()
        super.onDestroy()
    }

    private fun resumeMonitoringAfterRestart() {
        val deps = TrackingGraph.get()
        if (deps == null) {
            Log.w(TAG, "Cannot resume monitoring: repositories are not installed")
            stopForegroundAndSelf()
            return
        }
        scope.launch {
            val settings = runCatching { deps.settingsRepository.current() }
                .onFailure { Log.e(TAG, "Could not read settings after restart", it) }
                .getOrNull()
            if (settings?.autoDetectionEnabled == true) {
                beginMonitoring()
            } else {
                stopForegroundAndSelf()
            }
        }
    }

    private fun beginMonitoring() {
        if (mode != Mode.IDLE) return
        if (!hasLocationPermission()) {
            Log.w(TAG, "Auto-detection needs location permission")
            stopForegroundAndSelf()
            return
        }
        if (!locationEnabled()) {
            Log.w(TAG, "Auto-detection skipped: location is turned off")
            stopForegroundAndSelf()
            return
        }
        val deps = TrackingGraph.get()
        if (deps == null) {
            Log.e(TAG, "Cannot monitor: repositories are not installed")
            stopForegroundAndSelf()
            return
        }

        mode = Mode.MONITORING
        startForegroundCompat(monitorNotification())
        scope.launch {
            val settings = runCatching { deps.settingsRepository.current() }
                .onFailure { Log.e(TAG, "Could not read settings before monitoring", it) }
                .getOrNull()
            if (mode != Mode.MONITORING) return@launch
            autoDetectionEnabled = settings?.autoDetectionEnabled ?: false
            if (!autoDetectionEnabled) {
                stopForegroundAndSelf()
                return@launch
            }
            if (!startMonitorUpdates()) stopForegroundAndSelf()
        }
    }

    private fun startMonitorUpdates(): Boolean {
        detector = TripDetector()
        if (!locationSource.start(FusedLocationSource.Profile.MONITOR)) {
            detector = null
            return false
        }
        TrackingStateHolder.setMonitoring(true)
        return true
    }

    private fun stopMonitoring() {
        if (mode == Mode.RECORDING) {
            stopMonitorUpdates()
            return
        }
        stopForegroundAndSelf()
    }

    private fun stopMonitorUpdates() {
        detector = null
        if (mode == Mode.MONITORING) locationSource.stop()
        TrackingStateHolder.setMonitoring(false)
    }

    private fun beginRecording(manual: Boolean) {
        if (mode == Mode.RECORDING) return
        if (!hasFineLocationPermission()) {
            Log.w(TAG, "Recording needs fine location permission")
            stopForegroundAndSelf()
            return
        }
        val deps = TrackingGraph.get()
        if (deps == null) {
            Log.e(TAG, "Cannot record: repositories are not installed")
            stopForegroundAndSelf()
            return
        }

        stopMonitorUpdates()
        mode = Mode.RECORDING
        automatic = !manual
        startForegroundCompat(recordingNotification(distanceKm = null, score = null))

        scope.launch {
            val settings = runCatching { deps.settingsRepository.current() }
                .onFailure { Log.e(TAG, "Could not read settings before recording", it) }
                .getOrNull()
            val defaultVehicle = runCatching { deps.vehicleRepository.defaultVehicle() }
                .onFailure { Log.e(TAG, "Could not read the default vehicle", it) }
                .getOrNull()
            if (mode != Mode.RECORDING) return@launch
            if (settings == null || defaultVehicle == null) {
                Log.e(TAG, "Recording aborted: settings or vehicle unavailable")
                stopForegroundAndSelf()
                return@launch
            }
            scoringConfig = settings.scoringConfig.sanitized()
            autoDetectionEnabled = settings.autoDetectionEnabled
            vehicle = defaultVehicle
            startRecordingUpdates()
        }
    }

    private fun startRecordingUpdates() {
        if (mode != Mode.RECORDING) return
        val startedAtMs = System.currentTimeMillis()
        analyzer = TripAnalyzer(scoringConfig)
        sensorFusion = (sensorFusion ?: SensorFusion(this)).also { it.start() }
        if (!locationSource.start(FusedLocationSource.Profile.RECORDING)) {
            Log.w(TAG, "Recording aborted: location updates could not start")
            releaseRecording()
            stopForegroundAndSelf()
            return
        }
        TrackingStateHolder.publish(
            LiveTripState(
                recording = true,
                automatic = automatic,
                startedAtMs = startedAtMs,
            ),
        )
        updateRecordingNotification(distanceKm = null, score = null)
    }

    private fun endRecording(resumeMonitoring: Boolean) {
        if (mode != Mode.RECORDING) {
            stopForegroundAndSelf()
            return
        }

        val analysis = analyzer?.finish()
        val wasAutomatic = automatic
        mode = Mode.IDLE
        releaseRecording()
        trackingStateIsIdle()

        val deps = TrackingGraph.get()
        if (analysis == null || deps == null) {
            afterTripFinished(resumeMonitoring)
            return
        }
        if (TripRecordingPolicy.shouldDiscard(analysis.distanceMeters, analysis.durationMs)) {
            Log.i(
                TAG,
                "Discarding short trip: ${analysis.distanceMeters} m in ${analysis.durationMs} ms",
            )
            afterTripFinished(resumeMonitoring)
            return
        }

        val vehicleSnapshot = vehicle
        val configSnapshot = scoringConfig
        scope.launch {
            try {
                val record = withContext(Dispatchers.Default) {
                    buildRecord(analysis, vehicleSnapshot, configSnapshot, manualStart = !wasAutomatic)
                }
                withContext(Dispatchers.IO) {
                    runCatching { deps.tripRepository.saveTrip(record) }
                        .onFailure {
                            Log.e(
                                TAG,
                                "Trip ${record.id} was scored but not saved: " +
                                    "${record.distanceMeters} m and ${record.events.size} events lost",
                                it,
                            )
                        }
                }
            } finally {
                afterTripFinished(resumeMonitoring)
            }
        }
    }

    private fun buildRecord(
        analysis: TripAnalysis,
        vehicle: VehicleProfile,
        config: ScoringConfig,
        manualStart: Boolean,
    ): TripRecord {
        val scored = TripScorer.score(analysis, vehicle, config)
        return TripRecord(
            id = UUID.randomUUID().toString(),
            vehicleId = vehicle.id,
            startedAtMs = analysis.startedAtMs,
            endedAtMs = analysis.endedAtMs,
            distanceMeters = analysis.distanceMeters,
            movingDurationMs = analysis.movingDurationMs,
            idleDurationMs = analysis.idleDurationMs,
            averageSpeedKmh = analysis.averageSpeedKmh,
            maxSpeedKmh = analysis.maxSpeedKmh,
            score = scored.score,
            breakdown = scored.breakdown,
            fuel = scored.fuel,
            events = scored.events,
            route = analysis.route,
            manualStart = manualStart,
            scoringVersion = ScoringVersion.CURRENT,
            createdAtMs = System.currentTimeMillis(),
        )
    }

    private fun afterTripFinished(resumeMonitoring: Boolean) {
        if (resumeMonitoring && autoDetectionEnabled && hasLocationPermission() && locationEnabled()) {
            beginMonitoring()
        } else {
            stopForegroundAndSelf()
        }
    }

    private fun stopTracing() {
        when (mode) {
            Mode.RECORDING -> endRecording(resumeMonitoring = false)
            Mode.MONITORING, Mode.IDLE -> stopMonitoring()
        }
    }

    private fun onFix(location: Location) {
        when (mode) {
            Mode.MONITORING -> onMonitorFix(location)
            Mode.RECORDING -> onRecordingFix(location)
            Mode.IDLE -> Unit
        }
        if (mode != Mode.IDLE) lastFix = location
    }

    private fun onMonitorFix(location: Location) {
        val currentDetector = detector ?: return
        val speedKmh = speedMpsOf(location) * 3.6
        when (currentDetector.onFix(speedKmh, location.time)) {
            TripDetector.Signal.StartTrip -> beginRecording(manual = false)
            TripDetector.Signal.StopTrip -> Unit
            null -> Unit
        }
    }

    private fun onRecordingFix(location: Location) {
        val currentAnalyzer = analyzer ?: return
        val fusion = sensorFusion
        val bearing = if (location.hasBearing()) location.bearing.toDouble() else null
        fusion?.setHeading(bearing)
        val peaks = fusion?.consume() ?: SensorPeaks(null, null)
        val speedMps = speedMpsOf(location)

        val sample = TelemetrySample(
            timestampMs = location.time,
            point = GeoPoint(location.latitude, location.longitude),
            speedMps = speedMps,
            bearingDeg = bearing,
            accuracyMeters = if (location.hasAccuracy()) {
                location.accuracy.toDouble()
            } else {
                UNKNOWN_ACCURACY_METERS
            },
            accelForwardMps2 = peaks.forwardMps2,
            accelLateralMps2 = peaks.lateralMps2,
        )
        val analysis = currentAnalyzer.onSample(sample)
        publishProgress(analysis)
    }

    private fun publishProgress(analysis: TripAnalysis) {
        val scored = TripScorer.score(analysis, vehicle, scoringConfig)
        TrackingStateHolder.update {
            it.copy(
                recording = true,
                automatic = automatic,
                startedAtMs = analysis.startedAtMs,
                distanceMeters = analysis.distanceMeters,
                durationMs = analysis.durationMs,
                currentSpeedKmh = analysis.lastSpeedKmh,
                score = scored.score,
                eventCount = scored.events.size,
                fuel = scored.fuel,
                breakdown = scored.breakdown,
                lastPoint = analysis.route.lastOrNull()?.point,
            )
        }
        updateRecordingNotification(distanceKm = analysis.distanceKm, score = scored.score)
    }

    /** Speed from the fix, or from the previous fix when the provider omits it. */
    private fun speedMpsOf(location: Location): Double {
        if (location.hasSpeed()) return location.speed.toDouble()
        val previous = lastFix ?: return 0.0
        val deltaMs = location.time - previous.time
        if (deltaMs <= 0L || deltaMs > MAX_DERIVED_SPEED_WINDOW_MS) return 0.0
        val distance = GeoMath.distanceMeters(
            GeoPoint(previous.latitude, previous.longitude),
            GeoPoint(location.latitude, location.longitude),
        )
        return distance / (deltaMs / 1000.0)
    }

    private fun releaseRecording() {
        analyzer = null
        sensorFusion?.stop()
        locationSource.stop()
        lastFix = null
    }

    private fun trackingStateIsIdle() {
        automatic = false
        TrackingStateHolder.publish(LiveTripState())
    }

    private fun stopForegroundAndSelf() {
        mode = Mode.IDLE
        detector = null
        releaseRecording()
        TrackingStateHolder.reset()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean = locationSource.hasPermission()

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun locationEnabled(): Boolean {
        val manager = getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.tracking_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (error: SecurityException) {
            // Thrown when the system refuses a location foreground service,
            // e.g. after the permission was revoked mid-trip.
            Log.e(TAG, "Foreground service was rejected; stopping tracking", error)
            stopForegroundAndSelf()
        }
    }

    private fun monitorNotification(): Notification =
        baseNotification()
            .setContentTitle(getString(R.string.tracking_notification_monitoring_title))
            .setContentText(getString(R.string.tracking_notification_monitoring_text))
            .build()

    private fun recordingNotification(distanceKm: Double?, score: Int?): Notification {
        val text = when {
            distanceKm == null -> getString(R.string.tracking_notification_recording_starting)
            score == null -> getString(R.string.tracking_notification_recording_text, distanceKm)
            else -> getString(
                R.string.tracking_notification_recording_with_score,
                distanceKm,
                score,
            )
        }
        return baseNotification()
            .setContentTitle(getString(R.string.tracking_notification_recording_title))
            .setContentText(text)
            .build()
    }

    private fun baseNotification(): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        openAppIntent()?.let(builder::setContentIntent)
        return builder
    }

    /**
     * The launcher intent is resolved through the package manager instead of a
     * class reference, so the service does not depend on the UI module's
     * activity type and keeps working when the launcher is renamed.
     */
    private fun openAppIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun updateRecordingNotification(distanceKm: Double?, score: Int?) {
        if (mode != Mode.RECORDING) return
        // A foreground notification is still shown without the runtime
        // permission; the updates are skipped because the system would drop
        // them anyway.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, recordingNotification(distanceKm, score))
    }

    companion object {
        private const val TAG = "TripTrackingService"
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 42
        private const val UNKNOWN_ACCURACY_METERS = 25.0
        private const val MAX_DERIVED_SPEED_WINDOW_MS = 10_000L

        const val ACTION_START_MONITOR = "app.ecorota.tracking.START_MONITOR"
        const val ACTION_START_RECORDING = "app.ecorota.tracking.START_RECORDING"
        const val ACTION_STOP_RECORDING = "app.ecorota.tracking.STOP_RECORDING"
        const val ACTION_STOP_MONITOR = "app.ecorota.tracking.STOP_MONITOR"
        const val ACTION_STOP_ALL = "app.ecorota.tracking.STOP_ALL"
        const val EXTRA_MANUAL = "app.ecorota.tracking.EXTRA_MANUAL"
    }
}
