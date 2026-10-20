package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.data.band.BandTechnology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A Band value split into its number and the name under it. */
class BandDisplayTest {

    private fun band(value: String?, technology: BandTechnology, vararg bands: Int) =
        CellField(CellFieldKind.BAND, value, bandTarget = BandTapTarget.of(technology, *bands))

    @Test
    fun oneNamedNrBand_isSplit() {
        assertEquals(BandDisplay("n41", "TD 2600+"), BandDisplay.of(band("n41 (TD 2600+)", BandTechnology.NR, 41)))
    }

    @Test
    fun oneNamedLteBand_isSplit() {
        assertEquals(BandDisplay("66", "AWS"), BandDisplay.of(band("66 (AWS)", BandTechnology.LTE, 66)))
    }

    @Test
    fun severalBands_stayAsTheyAre() {
        assertEquals(
            BandDisplay("n48 / n77 / n78", null),
            BandDisplay.of(band("n48 / n77 / n78", BandTechnology.NR, 48, 77, 78)),
        )
    }

    @Test
    fun anUnnamedBand_staysAsItIs() {
        assertEquals(BandDisplay("n999", null), BandDisplay.of(band("n999", BandTechnology.NR, 999)))
    }

    @Test
    fun noValue_isNothing() {
        assertNull(BandDisplay.of(band(null, BandTechnology.NR, 41)))
    }
}
