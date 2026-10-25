package app.ecorota.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    val state: StateFlow<LiveTripState> = mutableState.asStateFlow()

    /** True while the low-power auto-detection service is listening. */
    val monitoring: StateFlow<Boolean> = mutableMonitoring.asStateFlow()

    fun publish(state: LiveTripState) {
        mutableState.value = state
    }

    fun update(transform: (LiveTripState) -> LiveTripState) {
        mutableState.update(transform)
    }

    fun setMonitoring(value: Boolean) {
        mutableMonitoring.value = value
    }

    fun reset() {
        mutableState.value = LiveTripState()
        mutableMonitoring.value = false
    }
}
