package com.craxiom.networksurvey.data.band

import com.craxiom.networksurvey.ui.cellular.bands.BandFormatting
import com.craxiom.networksurvey.util.CellularUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Loads the generated band reference through the real repository and checks it against the tables
 * in [CellularUtils].
 *
 * The app deliberately holds this data twice. The browsable reference is an asset, because it is
 * large, rich, and only needed on a screen that has a Context. Labelling a live cell stays in a
 * static table on a display path that has none. That split is worth having, but it is only safe
 * while the two agree, which is what this test enforces.
 */
@RunWith(RobolectricTestRunner::class)
class CellularBandsAssetTest {

    private val bands: List<CellularBand> by lazy {
        runBlocking { BandReferenceRepository.bands(RuntimeEnvironment.getApplication()) }
    }

    private val lte get() = bands.filter { it.technology == BandTechnology.LTE }
    private val nr get() = bands.filter { it.technology == BandTechnology.NR }

    @Test
    fun assetLoadsAndCoversBothTechnologies() {
        assertTrue("the asset did not load", bands.isNotEmpty())
        assertTrue("expected the full LTE band set", lte.size >= 70)
        assertTrue("expected the full NR band set", nr.size >= 75)
    }

    @Test
    fun everyBandHasADesignatorAndSomeRange() {
        for (band in bands) {
            assertTrue("${band.designator} is not a sensible band number", band.number in 1..1024)
            assertTrue(
                "${band.designator} has neither an uplink nor a downlink",
                band.primaryMhz != null,
            )
        }
    }

    @Test
    fun lteChannelRangesMatchCellularUtils() {
        for (band in lte) {
            val channels = band.downlinkChannels ?: continue
            assertEquals(
                "${band.designator} low edge",
                band.number,
                CellularUtils.downlinkEarfcnToBand(channels.first),
            )
            assertEquals(
                "${band.designator} high edge",
                band.number,
                CellularUtils.downlinkEarfcnToBand(channels.last),
            )
        }
    }

    @Test
    fun nrResolutionFlagsMatchCellularUtils() {
        for (band in nr) {
            val channels = band.downlinkChannels ?: continue
            // Both edges, because the one range error the specification caught in this table
            // (n102's lower bound) was an edge error.
            for (channel in listOf(channels.first, channels.last)) {
                val matches = CellularUtils.downlinkNarfcnToBands(channel).toList()
                if (band.resolvesLiveCells) {
                    assertTrue(
                        "${band.designator} claims to resolve but the table omits it at $channel",
                        matches.contains(band.number),
                    )
                } else {
                    assertTrue(
                        "${band.designator} is reference only but the table returns it at $channel",
                        !matches.contains(band.number),
                    )
                }
            }
        }
    }

    @Test
    fun referenceOnlyBandsAreStillBrowsable() {
        val referenceOnly = nr.filter { !it.resolvesLiveCells }
        // A reference with holes in it reads as a bug, so these are present even though they
        // never label a live cell.
        assertTrue("expected the non-resolving bands to still be listed", referenceOnly.size >= 18)
        assertTrue(
            "n109 must be browsable even though it never resolves",
            referenceOnly.any { it.number == 109 },
        )
    }

    /**
     * The twelve bands a channel number can never identify on its own. The detail sheet states
     * this outright, which the old blank Band field could not, so the data behind that sentence
     * has to stay correct as the table changes.
     */
    @Test
    fun fullyShadowedBandsAreRecordedAndAgreeWithTheResolver() {
        val shadowed = nr.filter { it.isFullyShadowed }.map { it.number }.sorted()
        assertEquals(listOf(1, 2, 14, 30, 38, 48, 78, 96, 101, 102, 104, 261), shadowed)

        for (band in nr.filter { it.isFullyShadowed }) {
            val channels = band.downlinkChannels!!
            for (channel in listOf(channels.first, (channels.first + channels.last) / 2, channels.last)) {
                assertEquals(
                    "${band.designator} should never resolve alone at $channel",
                    -1,
                    CellularUtils.downlinkNarfcnToBand(channel),
                )
            }
            assertTrue("${band.designator} must name what shadows it", band.shadowedBy.isNotEmpty())
        }
    }

    /**
     * Ties the frequency ranges to the channel ranges.
     *
     * Everything else in this file compares the asset against the Java tables, but the generator
     * derives those columns from the same tables, so that is partly self referential. Frequencies
     * come only from the specification documents and had no guard. A channel range and a frequency
     * range describe the same spectrum through a fixed raster step, so they must agree, and a
     * transposed digit in either breaks the relation.
     */
    @Test
    fun frequencyRangesAgreeWithChannelRangesThroughTheRaster() {
        for (band in lte) {
            val channels = band.downlinkChannels ?: continue
            val mhz = band.downlinkMhz ?: continue
            // TS 36.101: LTE steps 100 kHz per EARFCN, and the range is inclusive, so a 60 MHz
            // band holds 600 channels whose first and last differ by 599.
            val expectedChannels = ((mhz.endInclusive - mhz.start) * 10).toInt()
            assertEquals(
                "${band.designator}: ${mhz.start}-${mhz.endInclusive} MHz does not match " +
                        "EARFCN ${channels.first}-${channels.last}",
                expectedChannels,
                channels.last - channels.first + 1,
            )
        }

        for (band in nr) {
            if (band.isFr2) continue // FR2 frequencies are derived, not read from a specification.
            val channels = band.downlinkChannels ?: continue
            val mhz = band.downlinkMhz ?: continue
            // TS 38.104 global raster: 5 kHz per NARFCN below 3 GHz, 15 kHz above it. Seven
            // bands (n41, n46, n47, n48, n79, n90, n104) have an upper NREF one step inside the
            // band edge because the raster does not divide their width evenly, so one step of
            // tolerance is correct rather than lenient.
            val stepKhz = if (channels.first <= 599999) 5.0 else 15.0
            val expectedSpan = (mhz.endInclusive - mhz.start) * 1000.0 / stepKhz
            assertEquals(
                "${band.designator}: ${mhz.start}-${mhz.endInclusive} MHz does not match " +
                        "NARFCN ${channels.first}-${channels.last}",
                expectedSpan,
                (channels.last - channels.first).toDouble(),
                1.0,
            )
        }
    }

    /**
     * Duplex spacing is a magnitude, never a signed difference.
     *
     * Twelve bands are reverse duplex, with the downlink below the uplink, and a signed
     * subtraction printed those as a negative spacing on the one row the detail sheet leads with
     * for a paired band. B13, B20 and B71 are all in that set and are all widely deployed.
     */
    @Test
    fun duplexSpacingIsAlwaysPositive() {
        val paired = bands.filter { it.downlinkMhz != null && it.uplinkMhz != null }
        val reverseDuplex = paired.filter { it.downlinkMhz!!.start < it.uplinkMhz!!.start }
        assertEquals("expected the known reverse duplex bands", 12, reverseDuplex.size)

        for (band in paired) {
            val spacing = BandFormatting.duplexSpacingMhz(band) ?: continue
            assertTrue("${band.designator} reported a negative duplex spacing of $spacing",
                spacing > 0)
        }

        val b13 = lte.single { it.number == 13 }
        assertEquals("B13 is quoted as a 31 MHz spacing", 31.0,
            BandFormatting.duplexSpacingMhz(b13)!!, 0.001)
    }

    @Test
    fun bandNamesMatchCellularUtils() {
        for (band in lte) {
            assertEquals(
                "${band.designator} name disagrees with CellularUtils",
                CellularUtils.getLteBandName(band.number),
                band.name,
            )
        }
        for (band in nr) {
            assertEquals(
                "${band.designator} name disagrees with CellularUtils",
                CellularUtils.getNrBandName(band.number),
                band.name,
            )
        }
    }

    @Test
    fun sulBandsFallBackToTheirUplinkRange() {
        val sul = nr.filter { it.duplex == DuplexMode.SUL }
        assertTrue("expected some SUL bands in the reference", sul.isNotEmpty())
        for (band in sul) {
            assertEquals("${band.designator} should have no downlink", null, band.downlinkMhz)
            assertEquals(
                "${band.designator} should identify by its uplink",
                band.uplinkMhz,
                band.primaryMhz,
            )
        }
    }
}
