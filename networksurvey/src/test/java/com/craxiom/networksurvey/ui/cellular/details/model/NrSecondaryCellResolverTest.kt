package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.fragments.model.NrSecondaryCellSighting
import com.craxiom.networksurvey.util.TelephonyStateUtils.NrMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 5G NR secondary cell card, with a fake clock. */
class NrSecondaryCellResolverTest {

    private val towerA = "3102601234525600003"
    private val towerB = "3102601234599999999"

    private fun live(nowMs: Long, tower: String = towerA) =
        NrSecondaryCellResolver.resolve(DetailsRecords.nrSecondary(), NrMode.NON_STANDALONE, tower, NrSecondaryCellSighting.NONE, nowMs)

    @Test
    fun aSecondaryCell_isLive_andRemembered() {
        val result = live(nowMs = 10_000)

        val state = result.state!!
        assertFalse(state.idle())
        assertEquals("n41 (TD 2600+)", state.band())
        assertEquals("2600.550", state.frequency())
        assertEquals("271 (1/90)", state.pci())
        assertEquals("520110", state.narfcn())
        assertEquals(-84, state.ssRsrp())
        assertEquals(-11, state.ssRsrq())
        assertEquals(14, state.ssSinr())
        assertEquals(10_000, result.sighting.realtimeMs())
        assertEquals(towerA, result.sighting.servingCellId())
        assertNull(result.lastSeenAgeMs)
    }

    @Test
    fun afterTheLegDetaches_onNsa_theCardIsIdleWithItsAge() {
        val sighting = live(nowMs = 10_000).sighting

        val result = NrSecondaryCellResolver.resolve(null, NrMode.NON_STANDALONE, towerA, sighting, nowMs = 52_000)

        assertTrue(result.state!!.idle())
        assertEquals("271 (1/90)", result.state!!.pci())
        assertEquals(42_000L, result.lastSeenAgeMs)
        assertSame(sighting, result.sighting)
        assertEquals(NrSecondaryCellResolver.LastSeenAge(42, false), NrSecondaryCellResolver.lastSeenAge(result.lastSeenAgeMs!!))
    }

    @Test
    fun theIdleCard_hidesAfterTwoMinutes() {
        val sighting = live(nowMs = 0).sighting

        val atLimit = NrSecondaryCellResolver.resolve(null, NrMode.NON_STANDALONE, towerA, sighting, NrSecondaryCellResolver.IDLE_MAX_AGE_MS)
        val past = NrSecondaryCellResolver.resolve(null, NrMode.NON_STANDALONE, towerA, sighting, NrSecondaryCellResolver.IDLE_MAX_AGE_MS + 1)

        assertTrue(atLimit.state!!.idle())
        assertNull(past.state)
        assertNull(past.lastSeenAgeMs)
    }

    @Test
    fun aNewServingCell_dropsTheIdleCard() {
        val sighting = live(nowMs = 0).sighting

        assertNull(NrSecondaryCellResolver.resolve(null, NrMode.NON_STANDALONE, towerB, sighting, 5_000).state)
    }

    @Test
    fun standalone_isNeverIdle() {
        val sighting = live(nowMs = 0).sighting

        assertNull(NrSecondaryCellResolver.resolve(null, NrMode.STANDALONE, towerA, sighting, 5_000).state)
        assertNull(NrSecondaryCellResolver.resolve(null, NrMode.NONE, towerA, sighting, 5_000).state)
    }

    @Test
    fun withoutASighting_thereIsNoCard() {
        assertNull(NrSecondaryCellResolver.resolve(null, NrMode.NON_STANDALONE, towerA, NrSecondaryCellSighting.NONE, 5_000).state)
    }

    @Test
    fun lastSeenAge_secondsThenMinutes() {
        assertEquals(NrSecondaryCellResolver.LastSeenAge(0, false), NrSecondaryCellResolver.lastSeenAge(999))
        assertEquals(NrSecondaryCellResolver.LastSeenAge(59, false), NrSecondaryCellResolver.lastSeenAge(59_999))
        assertEquals(NrSecondaryCellResolver.LastSeenAge(1, true), NrSecondaryCellResolver.lastSeenAge(60_000))
        assertEquals(NrSecondaryCellResolver.LastSeenAge(2, true), NrSecondaryCellResolver.lastSeenAge(120_000))
    }
}
