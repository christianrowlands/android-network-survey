package com.craxiom.networksurvey.gpstest.ui.sky

import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.model.SbasType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sky page's satellites and averages built from GnssStatus signals. */
class SkyModelTest {

    private fun signal(
        svid: Int,
        cn0: Float,
        used: Boolean = false,
        type: GnssType = GnssType.NAVSTAR,
        elevation: Float = 40f,
        azimuth: Float = 120f,
    ) = SatelliteStatus(svid, type, cn0, true, true, used, elevation, azimuth)

    @Test
    fun noSignals_isEmpty() {
        assertEquals(SkyState(emptyList(), SkySummary(0, 0, null, null)), SkyModel.from(emptyList()))
    }

    @Test
    fun theAverages_areOverSignals_inViewAndUsed() {
        val state = SkyModel.from(
            listOf(signal(1, 40f, used = true), signal(2, 30f, used = true), signal(3, 20f), signal(4, 0f)),
        )
        assertEquals(4, state.summary.satellites)
        assertEquals(2, state.summary.used)
        assertEquals(30f, state.summary.inViewAverage!!, 1e-4f)
        assertEquals(35f, state.summary.usedAverage!!, 1e-4f)
    }

    @Test
    fun withNothingHeardOrUsed_theAveragesAreNull() {
        val summary = SkyModel.from(listOf(signal(1, 0f), signal(2, 0f))).summary
        assertNull(summary.inViewAverage)
        assertNull(summary.usedAverage)
    }

    @Test
    fun twoFrequencies_ofOneSatellite_drawOnce_withTheStrongest_andUsed() {
        val state = SkyModel.from(listOf(signal(7, 31f, used = false), signal(7, 42f, used = true)))
        assertEquals(1, state.satellites.size)
        assertEquals(42f, state.satellites[0].cn0DbHz)
        assertTrue(state.satellites[0].usedInFix)
        // Both signals still count in the averages.
        assertEquals(36.5f, state.summary.inViewAverage!!, 1e-4f)
    }

    @Test
    fun theSameNumber_inTwoConstellations_isTwoSatellites() {
        val state = SkyModel.from(listOf(signal(5, 30f), signal(5, 30f, type = GnssType.GALILEO)))
        assertEquals(2, state.satellites.size)
    }

    @Test
    fun augmentationSatellites_areKeptApartByTheirSystem() {
        val waas = signal(131, 30f, type = GnssType.SBAS).apply { sbasType = SbasType.WAAS }
        val egnos = signal(131, 30f, type = GnssType.SBAS).apply { sbasType = SbasType.EGNOS }
        assertEquals(2, SkyModel.from(listOf(waas, egnos)).satellites.size)
    }

    @Test
    fun aSatelliteWithoutAngles_isNotPlotted() {
        val state = SkyModel.from(listOf(signal(9, 25f, elevation = 0f, azimuth = 0f), signal(10, 25f)))
        assertEquals(listOf(10), state.satellites.map { it.svid })
        assertEquals(1, state.summary.satellites)
    }
}
