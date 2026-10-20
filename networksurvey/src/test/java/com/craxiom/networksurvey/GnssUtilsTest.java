package com.craxiom.networksurvey;

import android.location.GnssStatus;

import com.craxiom.messaging.gnss.Constellation;
import com.craxiom.networksurvey.constants.GnssMessageConstants;

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
