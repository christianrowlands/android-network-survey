package com.craxiom.networksurvey.gpstest.ui

import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SbasType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one table of systems the sky legend and the Status rows read their flags and names from. */
class GnssSystemsTest {

    @Test
    fun everyKnownConstellation_andAugmentationSystem_hasAnEntry() {
        val constellations = GnssType.entries.filter { it != GnssType.UNKNOWN && it != GnssType.SBAS }
        assertEquals(constellations.toSet(), GnssSystems.constellations.map { it.first }.toSet())
        assertEquals(SbasType.entries.filter { it != SbasType.UNKNOWN }.toSet(), GnssSystems.augmentations.map { it.first }.toSet())
    }

    @Test
    fun aSignal_findsItsConstellation_orForSbasItsAugmentationSystem() {
        assertEquals(R.string.gps_content_description, GnssSystems.of(GnssType.NAVSTAR, SbasType.UNKNOWN)?.shortName)
        assertEquals(R.drawable.ic_flag_european_union, GnssSystems.of(GnssType.GALILEO, null)?.flag)
        assertEquals(R.string.waas_content_description, GnssSystems.of(GnssType.SBAS, SbasType.WAAS)?.shortName)
        assertEquals(R.string.sky_legend_shape_southpan, GnssSystems.of(GnssType.SBAS, SbasType.SOUTHPAN)?.legendName)
    }

    @Test
    fun anUnknownSystem_hasNoEntry() {
        assertNull(GnssSystems.of(GnssType.UNKNOWN, SbasType.UNKNOWN))
        assertNull(GnssSystems.of(GnssType.SBAS, SbasType.UNKNOWN))
        assertNull(GnssSystems.of(GnssType.SBAS, null))
    }
}
