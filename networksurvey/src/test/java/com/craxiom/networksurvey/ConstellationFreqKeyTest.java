package com.craxiom.networksurvey;

import android.location.GnssStatus;

import com.craxiom.networksurvey.model.ConstellationFreqKey;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Tests for {@link ConstellationFreqKey}, which is used as a HashMap key to match the AGC values
 * from {@link android.location.GnssAutomaticGainControl} (exact long frequency) against
 * {@link android.location.GnssMeasurement} carrier frequencies (float derived, so only accurate to
 * roughly 128 Hz at GNSS frequencies).
 *
 * @since 1.58
 */
public class ConstellationFreqKeyTest
{
    private static final long GPS_L1_HZ = 1_575_420_000L;
    private static final long GPS_L5_HZ = 1_176_450_000L;
    private static final long BEIDOU_B1I_HZ = 1_561_098_000L;

    /**
     * Regression test for the equals() bug where the normalized frequency Strings were compared
     * with == instead of .equals(), which made every non-GLONASS HashMap lookup fail.
     */
    @Test
    public void sameConstellationAndFrequencyAreEqual()
    {
        ConstellationFreqKey key1 = new ConstellationFreqKey(GnssStatus.CONSTELLATION_GPS, GPS_L1_HZ);
        ConstellationFreqKey key2 = new ConstellationFreqKey(GnssStatus.CONSTELLATION_GPS, GPS_L1_HZ);

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());

        Map<ConstellationFreqKey, Float> agcMap = new HashMap<>();
        agcMap.put(key1, 3.5f);
        assertEquals(Float.valueOf(3.5f), agcMap.get(key2));
    }

    /**
     * Regression test for the kHz truncation bug. The measurement side frequency passes through a
     * float (for BeiDou B1I, (long) (float) 1561098000 is 1561097984), so truncating integer
     * division put the two sides of the AGC lookup in different kHz buckets. Rounding to the
     * nearest kHz makes them match.
     */
    @Test
    public void floatDerivedFrequencyMatchesExactFrequency()
    {
        long floatDerivedHz = (long) (float) BEIDOU_B1I_HZ;
        assertNotEquals(BEIDOU_B1I_HZ, floatDerivedHz);

        ConstellationFreqKey measurementKey = new ConstellationFreqKey(GnssStatus.CONSTELLATION_BEIDOU, floatDerivedHz);
        ConstellationFreqKey agcKey = new ConstellationFreqKey(GnssStatus.CONSTELLATION_BEIDOU, BEIDOU_B1I_HZ);

        assertEquals(agcKey, measurementKey);
        assertEquals(agcKey.hashCode(), measurementKey.hashCode());
    }

    @Test
    public void differentBandsAreNotEqual()
    {
        ConstellationFreqKey l1Key = new ConstellationFreqKey(GnssStatus.CONSTELLATION_GPS, GPS_L1_HZ);
        ConstellationFreqKey l5Key = new ConstellationFreqKey(GnssStatus.CONSTELLATION_GPS, GPS_L5_HZ);

        assertNotEquals(l1Key, l5Key);
    }

    @Test
    public void differentConstellationsAreNotEqual()
    {
        ConstellationFreqKey gpsKey = new ConstellationFreqKey(GnssStatus.CONSTELLATION_GPS, GPS_L1_HZ);
        ConstellationFreqKey beidouKey = new ConstellationFreqKey(GnssStatus.CONSTELLATION_BEIDOU, GPS_L1_HZ);

        assertNotEquals(gpsKey, beidouKey);
    }

    /**
     * GLONASS is FDMA, so different channel frequencies within a band share a single AGC value and
     * must normalize to the same key. The frequencies below are actual values observed on a
     * Pixel 8 Pro (see the comment in ConstellationFreqKey).
     */
    @Test
    public void glonassChannelFrequenciesShareOneKey()
    {
        ConstellationFreqKey lowChannelKey = new ConstellationFreqKey(GnssStatus.CONSTELLATION_GLONASS, 1_598_062_464L);
        ConstellationFreqKey highChannelKey = new ConstellationFreqKey(GnssStatus.CONSTELLATION_GLONASS, 1_605_374_976L);
        ConstellationFreqKey agcCenterKey = new ConstellationFreqKey(GnssStatus.CONSTELLATION_GLONASS, 1_602_000_000L);

        assertEquals(agcCenterKey, lowChannelKey);
        assertEquals(agcCenterKey, highChannelKey);
    }
}
