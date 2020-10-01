package com.craxiom.networksurvey;

import android.location.GnssStatus;

import com.craxiom.messaging.gnss.Constellation;
import com.craxiom.networksurvey.constants.GnssMessageConstants;
import com.craxiom.networksurvey.model.GnssType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the various conversions for GNSS scanning and logging.
 *
 * @since 0.3.0
 */
public class GnssUtilsTest
{
    @Test
    public void validateConstellationString()
    {
        assertEquals("", GnssMessageConstants.getConstellationString(Constellation.UNKNOWN));
        assertEquals("GPS", GnssMessageConstants.getConstellationString(Constellation.GPS));
        assertEquals("GLONASS", GnssMessageConstants.getConstellationString(Constellation.GLONASS));
        assertEquals("GALILEO", GnssMessageConstants.getConstellationString(Constellation.GALILEO));
        assertEquals("BEIDOU", GnssMessageConstants.getConstellationString(Constellation.BEIDOU));
        assertEquals("SBAS", GnssMessageConstants.getConstellationString(Constellation.SBAS));
        assertEquals("QZSS", GnssMessageConstants.getConstellationString(Constellation.QZSS));
        assertEquals("IRNSS", GnssMessageConstants.getConstellationString(Constellation.IRNSS));
        assertEquals("", GnssMessageConstants.getConstellationString(Constellation.UNRECOGNIZED));
    }

    @Test
    public void validateGnssToProtobufConversion()
    {
        assertEquals(Constellation.UNKNOWN, GnssMessageConstants.getProtobufConstellation(GnssType.UNKNOWN));
        assertEquals(Constellation.GPS, GnssMessageConstants.getProtobufConstellation(GnssType.NAVSTAR));
        assertEquals(Constellation.GLONASS, GnssMessageConstants.getProtobufConstellation(GnssType.GLONASS));
        assertEquals(Constellation.GALILEO, GnssMessageConstants.getProtobufConstellation(GnssType.GALILEO));
        assertEquals(Constellation.QZSS, GnssMessageConstants.getProtobufConstellation(GnssType.QZSS));
        assertEquals(Constellation.BEIDOU, GnssMessageConstants.getProtobufConstellation(GnssType.BEIDOU));
        assertEquals(Constellation.IRNSS, GnssMessageConstants.getProtobufConstellation(GnssType.IRNSS));
        assertEquals(Constellation.SBAS, GnssMessageConstants.getProtobufConstellation(GnssType.SBAS));
    }

    @Test
    public void validateAndroidGnssToProtobufConversion()
    {
        assertEquals(Constellation.UNKNOWN, GnssMessageConstants.getProtobufConstellation(GnssStatus.CONSTELLATION_UNKNOWN));
        assertEquals(Constellation.GPS, GnssMessageConstants.getProtobufConstellation(GnssStatus.CONSTELLATION_GPS));
        assertEquals(Constellation.GLONASS, GnssMessageConstants.getProtobufConstellation(GnssStatus.CONSTELLATION_GLONASS));
        assertEquals(Constellation.GALILEO, GnssMessageConstants.getProtobufConstellation(GnssStatus.CONSTELLATION_GALILEO));
        assertEquals(Constellation.QZSS, GnssMessageConstants.getProtobufConstellation(GnssStatus.CONSTELLATION_QZSS));
        assertEquals(Constellation.BEIDOU, GnssMessageConstants.getProtobufConstellation(GnssStatus.CONSTELLATION_BEIDOU));
        assertEquals(Constellation.IRNSS, GnssMessageConstants.getProtobufConstellation(GnssStatus.CONSTELLATION_IRNSS));
        assertEquals(Constellation.SBAS, GnssMessageConstants.getProtobufConstellation(GnssStatus.CONSTELLATION_SBAS));
    }
}
