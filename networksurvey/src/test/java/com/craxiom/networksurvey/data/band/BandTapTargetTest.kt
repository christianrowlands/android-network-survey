package com.craxiom.networksurvey.data.band

import com.craxiom.networksurvey.util.CellularUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the band numbers behind the tappable Band field on the cellular details screen.
 *
 * The field shows a formatted string while the tap needs numbers, and the two must never
 * disagree: tapping a field that reads "n48 / n77 / n78" has to offer exactly those three bands.
 * [CellularUtils.resolveNrBands] is the single source both sides use, so it is what is asserted
 * here.
 */
class BandTapTargetTest {

    @Test
    fun deviceReportedBandsWinOverTheChannel() {
        // The modem said n78; the NARFCN would have offered three candidates.
        val resolved = CellularUtils.resolveNrBands(intArrayOf(78), 640000)
        assertEquals(listOf(78), resolved.toList())
    }

    @Test
    fun ambiguousChannelOffersEveryCandidate() {
        val resolved = CellularUtils.resolveNrBands(intArrayOf(), 640000)
        assertEquals(listOf(48, 77, 78), resolved.toList())
    }

    @Test
    fun nullReportedBandsFallBackToTheChannel() {
        assertEquals(listOf(71), CellularUtils.resolveNrBands(null, 126270).toList())
    }

    @Test
    fun resolvedBandsMatchWhatTheFieldDisplays() {
        // The display drops the friendly names once there is more than one candidate, so compare
        // the designators rather than the whole string.
        val narfcn = 640000
        val displayed = CellularUtils.formatNrBands(intArrayOf(), narfcn)
        val resolved = CellularUtils.resolveNrBands(intArrayOf(), narfcn)
        assertEquals(resolved.joinToString(" / ") { "n$it" }, displayed)
    }

    @Test
    fun anUnknownChannelResolvesToNothingSoTheFieldStaysInert() {
        val resolved = CellularUtils.resolveNrBands(intArrayOf(), 200000)
        assertEquals(0, resolved.size)
        assertTrue(BandTapTarget(BandTechnology.NR, resolved.toList()).isEmpty)
    }

    @Test
    fun targetCarriesTechnologyAndOrder() {
        val target = BandTapTarget.of(BandTechnology.NR, 48, 77, 78)
        assertEquals(BandTechnology.NR, target.technology)
        assertEquals(listOf(48, 77, 78), target.bandNumbers)
        assertFalse(target.isEmpty)

        val lte = BandTapTarget.of(BandTechnology.LTE, 66)
        assertEquals(BandTechnology.LTE, lte.technology)
        assertEquals(listOf(66), lte.bandNumbers)
    }
}
