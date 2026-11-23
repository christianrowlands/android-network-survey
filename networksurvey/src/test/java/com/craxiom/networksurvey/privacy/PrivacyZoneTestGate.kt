package com.craxiom.networksurvey.privacy

import android.location.Location
import com.craxiom.networksurvey.privacy.PrivacyZoneRulesTest.Companion.north
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [PrivacyZoneGate] on a fixed clock with one zone, [HOME], for the record pipeline's tests:
 * [enter] puts the phone inside it and [leave] 5 km away. Needs Robolectric ([Location]).
 */
class PrivacyZoneTestGate(zones: List<PrivacyZone> = listOf(HOME)) {
    private val elapsedMs = 10_000_000L
    private val wallMs = 1_700_000_000_000L

    /** The saved zones the gate follows. */
    val zones = MutableStateFlow(zones)

    val gate = PrivacyZoneGate(this.zones, { true }, { elapsedMs }, { wallMs }, CoroutineScope(Dispatchers.Unconfined))

    /** A fresh, accurate fix [meters] north of [HOME]'s center. */
    fun fixAt(meters: Double, accuracy: Float = ACCURACY_METERS): Location {
        val (lat, lon) = north(HOME, meters)
        return Location("test").apply {
            latitude = lat
            longitude = lon
            this.accuracy = accuracy
            elapsedRealtimeNanos = elapsedMs * NANOS_PER_MILLI
            time = wallMs
        }
    }

    fun enter() = gate.onLocation(fixAt(0.0))

    fun leave() = gate.onLocation(fixAt(OUTSIDE_METERS))

    companion object {
        val HOME = PrivacyZone("home", "Home", 40.0, -105.0, 250)
        const val ACCURACY_METERS = 10f
        const val OUTSIDE_METERS = 5_000.0
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
