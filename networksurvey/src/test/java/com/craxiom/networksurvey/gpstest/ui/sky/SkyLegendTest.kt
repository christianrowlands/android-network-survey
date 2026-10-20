package com.craxiom.networksurvey.gpstest.ui.sky

import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SbasType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The legend lists every system the plot can draw, with the plot's own marks. */
class SkyLegendTest {

    @Test
    fun everyKnownConstellation_hasARow_withThePlotsMark() {
        val expected = GnssType.entries.filter { it != GnssType.UNKNOWN && it != GnssType.SBAS }.toSet()
        assertEquals(expected, SkyLegend.constellations.map { it.first }.toSet())
        SkyLegend.constellations.forEach { (type, row) -> assertEquals(SkyProjection.shape(type), row.shape) }
    }

    @Test
    fun everyKnownAugmentationSystem_hasARow_drawnAsTheSbasMark() {
        assertEquals(SbasType.entries.filter { it != SbasType.UNKNOWN }.toSet(), SkyLegend.augmentations.map { it.first }.toSet())
        SkyLegend.augmentations.forEach { (_, row) -> assertEquals(SatelliteShape.DIAMOND, row.shape) }
    }
}
