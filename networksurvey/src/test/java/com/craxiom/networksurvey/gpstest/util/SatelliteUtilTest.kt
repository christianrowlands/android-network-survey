package com.craxiom.networksurvey.gpstest.util

import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.util.SatelliteUtil.toSatelliteMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The signal and satellite counts the Fix panel's "Used / seen" and the filter notice show. */
class SatelliteUtilTest {

    private fun signal(svid: Int, cn0: Float, used: Boolean, mhz: Double? = null, type: GnssType = GnssType.NAVSTAR) =
        SatelliteStatus(svid, type, cn0, true, true, used, 40f, 120f).apply {
            if (mhz != null) {
                hasCarrierFrequency = true
                carrierFrequencyHz = mhz * 1_000_000
            }
        }

    @Test
    fun noSignals_countsNothing() {
        val metadata = emptyList<SatelliteStatus>().toSatelliteMetadata()
        assertEquals(0, metadata.numSignalsTotal)
        assertEquals(0, metadata.numSatsTotal)
    }

    @Test
    fun aSatelliteHeardOnTwoFrequencies_countsOnce_andItsSignalsTwice() {
        val metadata = listOf(
            signal(5, 40f, used = true, mhz = 1575.42),
            signal(5, 35f, used = true, mhz = 1176.45),
            signal(7, 20f, used = false, mhz = 1575.42),
            signal(9, 0f, used = false, mhz = 1575.42),
        ).toSatelliteMetadata()

        assertEquals(4, metadata.numSignalsTotal)
        assertEquals(3, metadata.numSignalsInView)
        assertEquals(2, metadata.numSignalsUsed)
        assertEquals(3, metadata.numSatsTotal)
        assertEquals(2, metadata.numSatsInView)
        assertEquals(1, metadata.numSatsUsed)
        assertTrue(metadata.isDualFrequencyPerSatInUse)
        assertTrue(metadata.isNonPrimaryCarrierFreqInView)
    }

    @Test
    fun theSameNumber_inTwoConstellations_isTwoSatellites() {
        val metadata = listOf(signal(3, 30f, used = true), signal(3, 30f, used = true, type = GnssType.GALILEO)).toSatelliteMetadata()

        assertEquals(2, metadata.numSatsTotal)
        assertEquals(2, metadata.numSatsUsed)
    }
}
