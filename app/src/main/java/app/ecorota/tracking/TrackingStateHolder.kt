package app.ecorota.tracking

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Live trip state shared by the process.
 *
 * The foreground service is the only writer; the UI observes it through
 * [TrackingController]. It lives outside the service because the service can
 * outlive the activity that started it, and the screen must still show the trip
 * in progress when it comes back.
 */
object TrackingStateHolder {
    private val mutableState = MutableStateFlow(LiveTripState())
    private val mutableMonitoring = MutableStateFlow(false)
    // Replays the last notice: the tracker can post one while no screen is
    // collecting (a trip that finishes in the background, for example).
    private val mutableNotices = MutableSharedFlow<TrackingNotice>(
        replay = 1,
        extraBufferCapacity = 3,
    )

    val state: StateFlow<LiveTripState> = mutableState.asStateFlow()

    /** True while the low-power auto-detection service is listening. */
    val monitoring: StateFlow<Boolean> = mutableMonitoring.asStateFlow()

    val notices: SharedFlow<TrackingNotice> = mutableNotices.asSharedFlow()

    fun publish(state: LiveTripState) {
        mutableState.value = state
    }

    fun update(transform: (LiveTripState) -> LiveTripState) {
        mutableState.update(transform)
    }

    fun setMonitoring(value: Boolean) {
        mutableMonitoring.value = value
    }

    fun postNotice(notice: TrackingNotice) {
        mutableNotices.tryEmit(notice)
    }

    /** Drops the replayed notice after the UI has shown it once. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearNotices() = mutableNotices.resetReplayCache()

    fun reset() {
        mutableState.value = LiveTripState()
        mutableMonitoring.value = false
    }
}
