package com.craxiom.networksurvey.privacy

import android.location.Location
import com.craxiom.networksurvey.privacy.PrivacyZoneRulesTest.Companion.north
import com.craxiom.networksurvey.privacy.PrivacyZoneRulesTest.Companion.zone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The thread-safe holder: Android fixes are aged the same way on every API level, zone and policy
 * changes apply at once, and sessions reset the decision.
 */
@RunWith(RobolectricTestRunner::class)
class PrivacyZoneGateTest {

    private val home = zone("home", 40.0, -105.0, 250)
    private val zones = MutableStateFlow(listOf(home))
    private var allowed = true
    private var elapsedMs = 10_000_000L
    private var wallMs = 1_700_000_000_000L
    private val gate = PrivacyZoneGate(zones, { allowed }, { elapsedMs }, { wallMs }, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `a fix inside a zone pauses and the flow and the volatile read agree`() {
        gate.onLocation(location(meters = 50.0))
        assertTrue(gate.isPausedForZone())
        assertEquals("home", gate.currentState().zoneName)
        assertEquals(gate.currentState(), gate.state.value)
    }

    @Test
    fun `starts undecided and keeps records`() {
        assertFalse(gate.currentState().decided)
        assertFalse(gate.isPausedForZone())
    }

    @Test
    fun `a fix older than 60 s does not decide, whatever the API level`() {
        gate.onLocation(location(meters = 50.0, ageMs = 61_000))
        assertFalse(gate.currentState().decided)
    }

    @Test
    fun `a fix without accuracy does not decide`() {
        gate.onLocation(location(meters = 50.0, accuracy = null))
        assertFalse(gate.currentState().decided)
    }

    @Test
    fun `losing the provider holds the decision`() {
        gate.onLocation(location(meters = 50.0))
        gate.onLocation(null)
        assertTrue(gate.isPausedForZone())
    }

    @Test
    fun `a fix without an elapsed realtime stamp is aged by the wall clock`() {
        val fix = gate.toFix(location(meters = 0.0, ageMs = 5_000).apply { elapsedRealtimeNanos = 0L })
        assertEquals(elapsedMs - 5_000, fix.elapsedRealtimeMs)

        val undated = gate.toFix(location(meters = 0.0).apply { elapsedRealtimeNanos = 0L; time = 0L })
        assertNull(undated.elapsedRealtimeMs)
    }

    @Test
    fun `a zone change applies without a new fix`() {
        gate.onLocation(location(meters = 50.0))
        zones.value = emptyList()
        assertFalse(gate.isPausedForZone())
        zones.value = listOf(home)
        assertTrue(gate.isPausedForZone())
    }

    @Test
    fun `the organization turning zones off stops the pause and empties the active zones`() {
        gate.onLocation(location(meters = 50.0))
        allowed = false
        gate.refreshPolicy()
        assertFalse(gate.isPausedForZone())
        assertTrue(gate.activeZones().isEmpty())
    }

    @Test
    fun `a new session re-reads the policy`() {
        allowed = false
        gate.onSessionStarted()
        gate.onLocation(location(meters = 50.0))
        assertFalse(gate.isPausedForZone())
    }

    @Test
    fun `a new session forgets an old decision and reuses a fresh fix`() {
        gate.onLocation(location(meters = 50.0))
        elapsedMs += 30_000
        gate.onSessionStarted()
        assertTrue(gate.isPausedForZone())

        elapsedMs += 60_000
        gate.onSessionStarted()
        assertFalse(gate.currentState().decided)
    }

    @Test
    fun `ending the session clears the pause`() {
        gate.onLocation(location(meters = 50.0))
        gate.onSessionEnded()
        assertFalse(gate.isPausedForZone())
        assertFalse(gate.state.value.decided)
    }

    private fun location(meters: Double, accuracy: Float? = 10f, ageMs: Long = 0L): Location {
        val (lat, lon) = north(home, meters)
        return Location("test").apply {
            latitude = lat
            longitude = lon
            accuracy?.let { this.accuracy = it }
            elapsedRealtimeNanos = (elapsedMs - ageMs) * 1_000_000L
            time = wallMs - ageMs
        }
    }
}
