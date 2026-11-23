package com.craxiom.networksurvey.privacy

import com.craxiom.networksurvey.privacy.PrivacyZoneRulesTest.Companion.NOW
import com.craxiom.networksurvey.privacy.PrivacyZoneRulesTest.Companion.fix
import com.craxiom.networksurvey.privacy.PrivacyZoneRulesTest.Companion.north
import com.craxiom.networksurvey.privacy.PrivacyZoneRulesTest.Companion.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sticky per-session decision: usable fixes decide, anything else holds the last decision. */
class PrivacyZoneEngineTest {

    private val home = zone("home", 40.0, -105.0, 250)
    private val engine = PrivacyZoneEngine().apply { setZones(listOf(home)) }

    @Test
    fun `no decision yet keeps records`() {
        val state = engine.state()
        assertFalse(state.decided)
        assertFalse(state.pausedForZone)
        assertNull(state.zoneName)
    }

    @Test
    fun `a usable fix inside a zone pauses with the zone's name`() {
        val state = engine.onFix(fix(home, meters = 100.0), NOW)
        assertTrue(state.decided)
        assertTrue(state.pausedForZone)
        assertEquals("home", state.zoneName)
    }

    @Test
    fun `a usable fix outside every zone decides outside`() {
        val state = engine.onFix(fix(home, meters = 1_000.0), NOW)
        assertTrue(state.decided)
        assertFalse(state.pausedForZone)
    }

    @Test
    fun `accuracy widening places a fix just outside the radius inside`() {
        assertTrue(engine.onFix(fix(home, meters = 300.0, accuracy = 60f), NOW).pausedForZone)
        assertFalse(engine.onFix(fix(home, meters = 300.0, accuracy = 40f), NOW).pausedForZone)
    }

    @Test
    fun `a coarse fix far from home cannot pause and does not end an inside decision`() {
        engine.onFix(fix(home, meters = 5_000.0), NOW)
        // A 1.5 km cell fix whose circle covers home makes no new decision
        assertFalse(engine.onFix(fix(home, meters = 1_000.0, accuracy = 1_500f), NOW).pausedForZone)

        engine.onFix(fix(home, meters = 50.0), NOW)
        // A coarse fix far away while at home does not restart saving
        assertTrue(engine.onFix(fix(home, meters = 3_000.0, accuracy = 900f), NOW).pausedForZone)
    }

    @Test
    fun `a stale fix makes no new decision`() {
        engine.onFix(fix(home, meters = 50.0), NOW)
        val stale = fix(home, meters = 5_000.0, takenAt = NOW - 61_000)
        assertTrue(engine.onFix(stale, NOW).pausedForZone)
    }

    @Test
    fun `the decision holds with no fix and no time limit`() {
        engine.onFix(fix(home, meters = 50.0), NOW)
        assertTrue(engine.onFix(null, NOW + 3_600_000).pausedForZone)
        assertTrue(engine.state().pausedForZone)
    }

    @Test
    fun `a stale or coarse first fix leaves the session undecided`() {
        assertFalse(engine.onFix(fix(home, takenAt = NOW - 120_000), NOW).decided)
        assertFalse(engine.onFix(fix(home, accuracy = 800f), NOW).decided)
    }

    @Test
    fun `leaving a zone resumes on the next usable fix`() {
        engine.onFix(fix(home, meters = 50.0), NOW)
        assertFalse(engine.onFix(fix(home, meters = 800.0), NOW + 10_000).pausedForZone)
    }

    @Test
    fun `with several zones the one the phone is in is named`() {
        val work = zone("work", north(home, 10_000.0).first, -105.0, 500)
        engine.setZones(listOf(home, work))
        assertEquals("work", engine.onFix(fix(home, meters = 10_100.0), NOW).zoneName)
        assertEquals("home", engine.onFix(fix(home, meters = 10.0), NOW).zoneName)
        assertFalse(engine.onFix(fix(home, meters = 5_000.0), NOW).pausedForZone)
    }

    @Test
    fun `a zone added around the last usable fix pauses at once and removing it resumes`() {
        val engine = PrivacyZoneEngine()
        engine.onFix(fix(home, meters = 20.0), NOW)
        assertFalse(engine.state().pausedForZone)
        assertTrue(engine.setZones(listOf(home)).pausedForZone)
        assertFalse(engine.setZones(emptyList()).pausedForZone)
        assertTrue(engine.state().decided)
    }

    @Test
    fun `zones turned off by the organization never pause`() {
        engine.onFix(fix(home, meters = 20.0), NOW)
        val off = engine.setAllowed(false)
        assertFalse(off.pausedForZone)
        assertTrue(engine.activeZones().isEmpty())
        assertTrue(engine.setAllowed(true).pausedForZone)
        assertEquals(listOf(home), engine.activeZones())
    }

    @Test
    fun `a new session forgets the old decision when the latest fix is stale`() {
        engine.onFix(fix(home, meters = 20.0), NOW)
        val state = engine.startSession(NOW + 61_000)
        assertFalse(state.decided)
        assertFalse(state.pausedForZone)
    }

    @Test
    fun `a new session decides from the latest fix when it is still usable`() {
        engine.onFix(fix(home, meters = 20.0), NOW)
        assertTrue(engine.startSession(NOW + 30_000).pausedForZone)
    }

    @Test
    fun `a new session does not reuse a coarse latest fix`() {
        engine.onFix(fix(home, meters = 20.0), NOW)
        engine.onFix(fix(home, meters = 20.0, accuracy = 900f), NOW + 5_000)
        assertFalse(engine.startSession(NOW + 6_000).decided)
    }

    @Test
    fun `ending the session clears the decision`() {
        engine.onFix(fix(home, meters = 20.0), NOW)
        val state = engine.endSession()
        assertFalse(state.decided)
        assertFalse(state.pausedForZone)
    }
}
