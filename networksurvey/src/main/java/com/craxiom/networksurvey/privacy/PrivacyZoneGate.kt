package com.craxiom.networksurvey.privacy

import android.content.Context
import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The process-wide holder of the privacy zone decision ([PrivacyZoneEngine]), fed once per fix by
 * `GpsListener` and read by anything that must hold records back.
 *
 * Reading is cheap and thread safe: [currentState] and [isPausedForZone] are a volatile read for the
 * record pipeline, and [state] is a StateFlow for the UI. Every input (a fix, a zone change, the
 * organization's policy, a session starting or ending) goes through one lock, so the published state
 * always matches the inputs in the order they arrived.
 *
 * @param zones               The saved zones; the gate follows every change.
 * @param isAllowedByPolicy   Reads the organization's `allow_privacy_zones` ([PrivacyZonePolicy]).
 * @param elapsedRealtimeMs   The elapsed realtime clock, in milliseconds.
 * @param currentTimeMs       The wall clock, used only for a fix without an elapsed realtime stamp.
 */
class PrivacyZoneGate internal constructor(
    zones: StateFlow<List<PrivacyZone>>,
    private val isAllowedByPolicy: () -> Boolean,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
    private val currentTimeMs: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val lock = Any()
    private val engine = PrivacyZoneEngine()

    @Volatile
    private var current: PrivacyZoneState = PrivacyZoneState.UNDECIDED
    private val _state = MutableStateFlow(PrivacyZoneState.UNDECIDED)

    /** The decision, for the UI. */
    val state: StateFlow<PrivacyZoneState> = _state.asStateFlow()

    init {
        synchronized(lock) {
            engine.setZones(zones.value)
            publish(engine.setAllowed(isAllowedByPolicy()))
        }
        scope.launch { zones.collect { update { engine.setZones(it) } } }
    }

    /** @return The decision right now: a volatile read, cheap enough for every record. */
    fun currentState(): PrivacyZoneState = current

    /** @return True while records must be held back from every sink because the phone is in a zone. */
    fun isPausedForZone(): Boolean = current.pausedForZone

    /**
     * @return The zones in force, for checks against a location other than the phone's latest fix
     * (a queued community row, for example): none when the organization turned zones off.
     */
    fun activeZones(): List<PrivacyZone> = synchronized(lock) { engine.activeZones() }

    /**
     * Feeds a fix from the phone's primary location provider (null when the provider turned off).
     * Only a usable fix (500 m or better, at most 60 s old on every API level) makes a new decision.
     */
    fun onLocation(location: Location?) {
        val fix = location?.let { toFix(it) }
        update { engine.onFix(fix, elapsedRealtimeMs()) }
    }

    /**
     * Called when a survey session starts, before any record is built. Forgets the last session's
     * decision, decides again from the latest fix if it is still usable, and re-reads the
     * organization's policy (it may have changed while nothing was surveying).
     */
    fun onSessionStarted() {
        val allowed = isAllowedByPolicy()
        update {
            engine.setAllowed(allowed)
            engine.startSession(elapsedRealtimeMs())
        }
    }

    /** Called when the survey session ends: nothing is paused until the next usable fix. */
    fun onSessionEnded() = update { engine.endSession() }

    /** Re-reads the organization's `allow_privacy_zones`; call when the managed configuration changes. */
    fun refreshPolicy() {
        val allowed = isAllowedByPolicy()
        update { engine.setAllowed(allowed) }
    }

    /** Converts [location], working out when it was taken the same way on every API level. */
    internal fun toFix(location: Location): PrivacyZoneFix {
        val takenAt = when {
            location.elapsedRealtimeNanos > 0L -> location.elapsedRealtimeNanos / NANOS_PER_MILLI
            // Some providers leave the elapsed realtime stamp unset; fall back to the wall clock.
            location.time > 0L -> elapsedRealtimeMs() - (currentTimeMs() - location.time)
            else -> null
        }
        return PrivacyZoneFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            elapsedRealtimeMs = takenAt,
        )
    }

    private inline fun update(change: () -> PrivacyZoneState) {
        synchronized(lock) { publish(change()) }
    }

    private fun publish(state: PrivacyZoneState) {
        // Logs the change but never the zone's name or place
        if (state.pausedForZone != current.pausedForZone || state.decided != current.decided) {
            Timber.i("Privacy zone decision: decided=%s, paused=%s", state.decided, state.pausedForZone)
        }
        current = state
        _state.value = state
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L

        @Volatile
        private var instance: PrivacyZoneGate? = null

        /** @return The process-wide gate over the saved zones and the organization's policy. */
        @JvmStatic
        fun getInstance(context: Context): PrivacyZoneGate = instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                PrivacyZoneGate(PrivacyZoneStore.getInstance(appContext).zones, { PrivacyZonePolicy.isAllowed(appContext) })
            }.also { instance = it }
        }
    }
}
