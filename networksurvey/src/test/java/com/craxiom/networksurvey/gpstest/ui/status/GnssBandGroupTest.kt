package com.craxiom.networksurvey.gpstest.ui.status

import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Signals grouped by the radio band they pass through. */
class GnssBandGroupTest {

    private fun signal(type: GnssType, mhz: Double?) = SatelliteStatus(1, type, 40f, true, true, true, 45f, 90f).apply {
        if (mhz != null) {
            hasCarrierFrequency = true
            carrierFrequencyHz = mhz * 1_000_000.0
        }
    }

    @Test
    fun gpsL1_galileoE1_andBeidouB1c_shareAGroup() {
        assertEquals(GnssBandGroup.L1, GnssBandGroup.of(signal(GnssType.NAVSTAR, 1575.42)))
        assertEquals(GnssBandGroup.L1, GnssBandGroup.of(signal(GnssType.GALILEO, 1575.42)))
        assertEquals(GnssBandGroup.L1, GnssBandGroup.of(signal(GnssType.BEIDOU, 1575.42)))
    }

    @Test
    fun glonassChannels_allLandInTheGlonassGroups() {
        // Channel -7 and +6 of L1, channel -7 and +6 of L2
        assertEquals(GnssBandGroup.GLONASS_L1, GnssBandGroup.ofMhz(1598.0625))
        assertEquals(GnssBandGroup.GLONASS_L1, GnssBandGroup.ofMhz(1605.375))
        assertEquals(GnssBandGroup.GLONASS_L2, GnssBandGroup.ofMhz(1242.9375))
        assertEquals(GnssBandGroup.GLONASS_L2, GnssBandGroup.ofMhz(1248.625))
    }

    @Test
    fun nearbyBands_stayApart() {
        assertEquals(GnssBandGroup.B3I, GnssBandGroup.ofMhz(1268.52))
        assertEquals(GnssBandGroup.E6, GnssBandGroup.ofMhz(1278.75))
        assertEquals(GnssBandGroup.B1I, GnssBandGroup.ofMhz(1561.098))
        assertEquals(GnssBandGroup.L5, GnssBandGroup.ofMhz(1176.45))
        assertEquals(GnssBandGroup.E5B, GnssBandGroup.ofMhz(1207.14))
        assertEquals(GnssBandGroup.L2, GnssBandGroup.ofMhz(1227.60))
    }

    @Test
    fun anUnlistedCarrier_isOther_andNoCarrier_isAllSignals() {
        assertEquals(GnssBandGroup.OTHER, GnssBandGroup.ofMhz(1191.795))
        assertEquals(GnssBandGroup.ALL_SIGNALS, GnssBandGroup.of(signal(GnssType.NAVSTAR, null)))
    }
}
