package com.craxiom.networksurvey.data.band

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests the search behaviour that keeps the band browser honest.
 *
 * The rule under test throughout: a bare number is a valid band number, EARFCN, NARFCN and
 * frequency all at once, and the app reports every reading that matches rather than silently
 * picking one.
 */
@RunWith(RobolectricTestRunner::class)
class BandSearchTest {

    private val bands: List<CellularBand> by lazy {
        runBlocking { BandReferenceRepository.bands(RuntimeEnvironment.getApplication()) }
    }

    private fun readings(query: String, forced: BandReading? = null) =
        BandSearch.interpret(query, bands, forced).map { it.reading }

    @Test
    fun bareNumberIsReadEveryValidWay() {
        val groups = BandSearch.interpret("78", bands)
        assertTrue("a bare number should offer more than one reading", groups.size > 1)
        assertTrue(groups.any { it.reading == BandReading.BAND_NUMBER })
        assertTrue(groups.any { it.reading == BandReading.EARFCN })
    }

    @Test
    fun autoModeFindsAnAmbiguousNarfcn() {
        // The runtime path: nothing pinned, user types a NARFCN.
        val groups = BandSearch.interpret("640000", bands)
        assertTrue("auto mode should surface the NARFCN reading", groups.isNotEmpty())
        val narfcn = groups.single { it.reading == BandReading.NARFCN }
        assertEquals(listOf(48, 77, 78), narfcn.bands.map { it.number }.sorted())
    }

    @Test
    fun designatorFixesTheReadingWithNoGuessing() {
        val groups = BandSearch.interpret("n78", bands)
        assertEquals(1, groups.size)
        assertEquals(BandReading.BAND_NUMBER, groups.first().reading)
        assertEquals(listOf(78), groups.first().bands.map { it.number })
        assertEquals(BandTechnology.NR, groups.first().bands.first().technology)
    }

    @Test
    fun designatorDistinguishesLteFromNr() {
        assertEquals(
            BandTechnology.LTE,
            BandSearch.interpret("B48", bands).first().bands.first().technology,
        )
        assertEquals(
            BandTechnology.NR,
            BandSearch.interpret("n48", bands).first().bands.first().technology,
        )
    }

    @Test
    fun ambiguousNarfcnListsEveryCandidate() {
        val group = BandSearch.interpret("640000", bands, BandReading.NARFCN).single()
        assertEquals(listOf(48, 77, 78), group.bands.map { it.number }.sorted())
    }

    @Test
    fun narfcnAlwaysYieldsAFrequencyEvenWithNoBand() {
        // 200000 is a valid raster point, 1000 MHz, that no operating band contains.
        val group = BandSearch.interpret("200000", bands, BandReading.NARFCN).single()
        assertEquals("no band should match", 0, group.bands.size)
        assertEquals("but the frequency is still known", 1000.0, group.frequencyMhz!!, 0.001)
    }

    @Test
    fun pinningAReadingSuppressesTheOthers() {
        assertEquals(listOf(BandReading.EARFCN), readings("1850", BandReading.EARFCN))
        assertEquals(listOf(BandReading.NARFCN), readings("1850", BandReading.NARFCN))
    }

    @Test
    fun explicitUnitsAreHonouredRegardlessOfThePinnedReading() {
        val group = BandSearch.interpret("3750 MHz", bands, BandReading.NARFCN).single()
        assertEquals(BandReading.FREQUENCY, group.reading)
        assertTrue("n77 covers 3750 MHz", group.bands.any { it.number == 77 })
        val ghz = BandSearch.interpret("3.75 GHz", bands).single()
        assertEquals(3750.0, ghz.frequencyMhz!!, 0.001)
    }

    @Test
    fun nameSearchFindsBandsByTheirColloquialName() {
        val group = BandSearch.interpret("cbrs", bands).single()
        assertEquals(BandReading.NAME, group.reading)
        assertTrue("B48 is the CBRS band", group.bands.any { it.number == 48 })
    }

    @Test
    fun earfcnFrequencyUsesTheBandsOwnOffset() {
        // B1 starts at 2110 MHz with EARFCN 0, so EARFCN 300 is 2140 MHz.
        val band = bands.single { it.technology == BandTechnology.LTE && it.number == 1 }
        assertEquals(2140.0, BandSearch.earfcnToMhz(300, band)!!, 0.001)
    }

    /**
     * A pinned frequency that lands in no band must still report itself, the way the channel
     * readings do. Returning nothing left the screen indistinguishable from an empty field.
     */
    @Test
    fun pinnedFrequencyWithNoHitsStillReportsItself() {
        val group = BandSearch.interpret("10000", bands, BandReading.FREQUENCY).single()
        assertEquals(BandReading.FREQUENCY, group.reading)
        assertEquals("10 GHz sits between FR1 and FR2", 0, group.bands.size)
        assertEquals(10000.0, group.frequencyMhz!!, 0.001)
    }

    /**
     * The pinned path and the explicit-unit path have to agree; they used to differ because only
     * one of them dropped empty results.
     */
    @Test
    fun pinnedAndSuffixedFrequencyAgree() {
        val pinned = BandSearch.interpret("10000", bands, BandReading.FREQUENCY).single()
        val suffixed = BandSearch.interpret("10000 MHz", bands).single()
        assertEquals(pinned.reading, suffixed.reading)
        assertEquals(pinned.bands.size, suffixed.bands.size)
        assertEquals(pinned.frequencyMhz!!, suffixed.frequencyMhz!!, 0.001)
    }

    /**
     * The signal the browser needs in order to say "nothing matches" rather than silently showing
     * the whole catalogue.
     */
    @Test
    fun unmatchableQueryReturnsNoGroups() {
        assertTrue(BandSearch.interpret("zzz", bands).isEmpty())
        assertTrue(BandSearch.interpret("n999", bands).isEmpty())
    }

    @Test
    fun emptyQueryReturnsNothingSoTheCatalogueShows() {
        assertTrue(BandSearch.interpret("", bands).isEmpty())
        assertTrue(BandSearch.interpret("   ", bands).isEmpty())
    }

    @Test
    fun referenceOnlyBandsAreStillSearchable() {
        val group = BandSearch.interpret("n109", bands).single()
        assertEquals(109, group.bands.single().number)
        assertTrue("n109 is browsable", !group.bands.single().resolvesLiveCells)
    }
}
