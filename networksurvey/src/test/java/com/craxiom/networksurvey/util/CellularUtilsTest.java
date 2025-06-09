package com.craxiom.networksurvey.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CellularUtilsTest
{
    private static final double DELTA = 0.0001; // Tolerance for double comparisons

    @Test
    public void narfcnToFrequencyMhz_range1_boundaryValues()
    {
        // Test boundary values for Range 1: 0 ≤ ARFCN ≤ 599,999
        // Formula: F_REF = NARFCN * 0.005 (5 kHz steps)

        // Lower boundary
        assertEquals(0.0, CellularUtils.narfcnToFrequencyMhz(0), DELTA);

        // Mid-range value
        assertEquals(1500.0, CellularUtils.narfcnToFrequencyMhz(300000), DELTA); // 300000 * 0.005 = 1500.0 MHz

        // Upper boundary
        assertEquals(2999.995, CellularUtils.narfcnToFrequencyMhz(599999), DELTA); // 599999 * 0.005 = 2999.995 MHz
    }

    @Test
    public void narfcnToFrequencyMhz_range2_boundaryValues()
    {
        // Test boundary values for Range 2: 600,000 ≤ ARFCN ≤ 2,016,666
        // Formula: F_REF = 3000.0 + (NARFCN - 600000) * 0.015 (15 kHz steps)

        // Lower boundary
        assertEquals(3000.0, CellularUtils.narfcnToFrequencyMhz(600000), DELTA); // 3000.0 + (600000 - 600000) * 0.015 = 3000.0 MHz

        // Mid-range value
        assertEquals(18249.99, CellularUtils.narfcnToFrequencyMhz(1616666), DELTA); // 3000.0 + (1616666 - 600000) * 0.015 = 18249.99 MHz

        // Upper boundary
        assertEquals(24249.99, CellularUtils.narfcnToFrequencyMhz(2016666), DELTA); // 3000.0 + (2016666 - 600000) * 0.015 = 24249.99 MHz
    }

    @Test
    public void narfcnToFrequencyMhz_range3_boundaryValues()
    {
        // Test boundary values for Range 3: 2,016,667 ≤ ARFCN ≤ 3,279,165
        // Formula: F_REF = 24250.08 + (NARFCN - 2016667) * 0.060 (60 kHz steps)

        // Lower boundary
        assertEquals(24250.08, CellularUtils.narfcnToFrequencyMhz(2016667), DELTA); // 24250.08 + (2016667 - 2016667) * 0.060 = 24250.08 MHz

        // Mid-range value (calculate a simpler value)
        assertEquals(30250.08, CellularUtils.narfcnToFrequencyMhz(2116667), DELTA); // 24250.08 + (2116667 - 2016667) * 0.060 = 24250.08 + 6000 = 30250.08 MHz

        // Upper boundary
        assertEquals(99999.96, CellularUtils.narfcnToFrequencyMhz(3279165), DELTA); // 24250.08 + (3279165 - 2016667) * 0.060 ≈ 100000.0 MHz
    }

    @Test
    public void narfcnToFrequencyMhz_commonNrBands()
    {
        // Test some common 5G NR band frequencies based on 3GPP specifications

        // N1 (2100 MHz): NARFCN around 422000 should give ~2110 MHz
        double frequency = CellularUtils.narfcnToFrequencyMhz(422000);
        assertEquals(2110.0, frequency, DELTA); // 3000 + (422000 - 600000) * 0.015 = 2110.0 MHz

        // N78 (3500 MHz): NARFCN around 633333 should give ~3500 MHz  
        frequency = CellularUtils.narfcnToFrequencyMhz(633333);
        assertEquals(3499.995, frequency, DELTA); // 3000 + (633333 - 600000) * 0.015 = 3499.995 MHz

        // N41 (2600 MHz): NARFCN around 520000 should give ~2600 MHz in range 1
        frequency = CellularUtils.narfcnToFrequencyMhz(520000);
        assertEquals(2600.0, frequency, DELTA); // 520000 * 0.005 = 2600.0 MHz
    }

    @Test
    public void narfcnToFrequencyMhz_invalidValues()
    {
        // Test invalid NARFCN values

        // Negative value
        assertEquals(-1.0, CellularUtils.narfcnToFrequencyMhz(-1), DELTA);
        assertEquals(-1.0, CellularUtils.narfcnToFrequencyMhz(-100), DELTA);

        // Value above maximum range
        assertEquals(-1.0, CellularUtils.narfcnToFrequencyMhz(3279166), DELTA);
        assertEquals(-1.0, CellularUtils.narfcnToFrequencyMhz(4000000), DELTA);
    }

    @Test
    public void narfcnToFrequencyMhz_edgeCases()
    {
        // Test edge cases between ranges

        // Last value in range 1
        assertEquals(2999.995, CellularUtils.narfcnToFrequencyMhz(599999), DELTA);

        // First value in range 2
        assertEquals(3000.0, CellularUtils.narfcnToFrequencyMhz(600000), DELTA);

        // Last value in range 2
        assertEquals(24249.99, CellularUtils.narfcnToFrequencyMhz(2016666), DELTA);

        // First value in range 3
        assertEquals(24250.08, CellularUtils.narfcnToFrequencyMhz(2016667), DELTA);
    }

    @Test
    public void narfcnToFrequencyMhz_precisionTest()
    {
        // Test precision for small NARFCN values
        assertEquals(0.005, CellularUtils.narfcnToFrequencyMhz(1), DELTA); // 1 * 0.005 = 0.005 MHz
        assertEquals(0.010, CellularUtils.narfcnToFrequencyMhz(2), DELTA); // 2 * 0.005 = 0.010 MHz
        assertEquals(0.050, CellularUtils.narfcnToFrequencyMhz(10), DELTA); // 10 * 0.005 = 0.050 MHz
    }

    @Test
    public void getNrBandName_commonBands()
    {
        // Test common Sub-6 GHz FDD bands
        assertEquals("2100", CellularUtils.getNrBandName(1));
        assertEquals("1900 PCS", CellularUtils.getNrBandName(2));
        assertEquals("1800", CellularUtils.getNrBandName(3));
        assertEquals("850", CellularUtils.getNrBandName(5));
        assertEquals("2600", CellularUtils.getNrBandName(7));
        assertEquals("900 GSM", CellularUtils.getNrBandName(8));
    }

    @Test
    public void getNrBandName_tddBands()
    {
        // Test common TDD bands
        assertEquals("TD 2600+", CellularUtils.getNrBandName(41));
        assertEquals("TD 3600", CellularUtils.getNrBandName(48));
        assertEquals("TD 3700", CellularUtils.getNrBandName(77));
        assertEquals("TD 3500", CellularUtils.getNrBandName(78));
        assertEquals("TD 4700", CellularUtils.getNrBandName(79));
    }

    @Test
    public void getNrBandName_mmWaveBands()
    {
        // Test mmWave bands
        assertEquals("28 GHz", CellularUtils.getNrBandName(257));
        assertEquals("26 GHz", CellularUtils.getNrBandName(258));
        assertEquals("41 GHz", CellularUtils.getNrBandName(259));
        assertEquals("39 GHz", CellularUtils.getNrBandName(260));
        assertEquals("28 GHz", CellularUtils.getNrBandName(261));
        assertEquals("47 GHz", CellularUtils.getNrBandName(262));
    }

    @Test
    public void getNrBandName_specialBands()
    {
        // Test some special purpose bands
        assertEquals("700 a", CellularUtils.getNrBandName(12));
        assertEquals("700 PS", CellularUtils.getNrBandName(14));
        assertEquals("800 Lower", CellularUtils.getNrBandName(18));
        assertEquals("800", CellularUtils.getNrBandName(20));
        assertEquals("1900+", CellularUtils.getNrBandName(25));
        assertEquals("850+", CellularUtils.getNrBandName(26));
        assertEquals("700 APT", CellularUtils.getNrBandName(28));
        assertEquals("AWS", CellularUtils.getNrBandName(66));
        assertEquals("AWS-4", CellularUtils.getNrBandName(70));
        assertEquals("600", CellularUtils.getNrBandName(71));
    }

    @Test
    public void getNrBandName_newAddedBands()
    {
        // Test newly added NR bands
        assertEquals("700 c", CellularUtils.getNrBandName(13));
        assertEquals("1600 L", CellularUtils.getNrBandName(24));
        assertEquals("700 d", CellularUtils.getNrBandName(29));
        assertEquals("450", CellularUtils.getNrBandName(31));
        assertEquals("TD 2000", CellularUtils.getNrBandName(34));
        assertEquals("TD 2600", CellularUtils.getNrBandName(38));
        assertEquals("TD 1900+", CellularUtils.getNrBandName(39));
        assertEquals("TD 2300", CellularUtils.getNrBandName(40));
        assertEquals("TD Unlicensed", CellularUtils.getNrBandName(46));
        assertEquals("TD V2X", CellularUtils.getNrBandName(47));
        assertEquals("2100+", CellularUtils.getNrBandName(65));
        assertEquals("SUL 1800", CellularUtils.getNrBandName(80));
        assertEquals("SUL 700 a", CellularUtils.getNrBandName(85));
        assertEquals("TD 2500", CellularUtils.getNrBandName(90));
        assertEquals("DL 2100", CellularUtils.getNrBandName(95));
        assertEquals("NTN 2100", CellularUtils.getNrBandName(256));
    }

    @Test
    public void getNrBandName_unknownBands()
    {
        // Test unknown/invalid band numbers
        assertNull(CellularUtils.getNrBandName(0));
        assertNull(CellularUtils.getNrBandName(4)); // n4 doesn't exist in NR
        assertNull(CellularUtils.getNrBandName(15)); // n15 doesn't exist in NR
        assertNull(CellularUtils.getNrBandName(-1));
        assertNull(CellularUtils.getNrBandName(1000));
    }

    @Test
    public void getLteBandName_commonLowBands()
    {
        // Test common low frequency bands (600-900 MHz)
        assertEquals("850", CellularUtils.getLteBandName(5));
        assertEquals("900 GSM", CellularUtils.getLteBandName(8));
        assertEquals("700 a", CellularUtils.getLteBandName(12));
        assertEquals("700 c", CellularUtils.getLteBandName(13));
        assertEquals("700 PS", CellularUtils.getLteBandName(14));
        assertEquals("700 b", CellularUtils.getLteBandName(17));
        assertEquals("800 DD", CellularUtils.getLteBandName(20));
        assertEquals("850+", CellularUtils.getLteBandName(26));
        assertEquals("700 APT", CellularUtils.getLteBandName(28));
        assertEquals("600", CellularUtils.getLteBandName(71));
    }

    @Test
    public void getLteBandName_commonMidBands()
    {
        // Test common mid frequency bands (1400-2700 MHz)
        assertEquals("2100 MHz", CellularUtils.getLteBandName(1));
        assertEquals("1900 PCS", CellularUtils.getLteBandName(2));
        assertEquals("1800+", CellularUtils.getLteBandName(3));
        assertEquals("AWS-1", CellularUtils.getLteBandName(4));
        assertEquals("2600", CellularUtils.getLteBandName(7));
        assertEquals("1900+", CellularUtils.getLteBandName(25));
        assertEquals("2300 TDD", CellularUtils.getLteBandName(40));
        assertEquals("2500 TDD", CellularUtils.getLteBandName(41));
        assertEquals("AWS", CellularUtils.getLteBandName(66));
    }

    @Test
    public void getLteBandName_highBands()
    {
        // Test high frequency bands (3300-5900 MHz)
        assertEquals("3500", CellularUtils.getLteBandName(22));
        assertEquals("3400 TDD", CellularUtils.getLteBandName(42));
        assertEquals("3600 TDD", CellularUtils.getLteBandName(43));
        assertEquals("5200 TDD", CellularUtils.getLteBandName(46));
        assertEquals("3550 CBRS", CellularUtils.getLteBandName(48));
    }

    @Test
    public void getLteBandName_awsBands()
    {
        // Test AWS (Advanced Wireless Services) bands
        assertEquals("AWS-1", CellularUtils.getLteBandName(4));
        assertEquals("AWS-3", CellularUtils.getLteBandName(10));
        assertEquals("AWS", CellularUtils.getLteBandName(66));
        assertEquals("AWS-4", CellularUtils.getLteBandName(70));
    }

    @Test
    public void getLteBandName_newIncludedBands()
    {
        // Test some of the newly included bands
        assertEquals("850 Japan", CellularUtils.getLteBandName(6));
        assertEquals("1800", CellularUtils.getLteBandName(9));
        assertEquals("1500 Lower", CellularUtils.getLteBandName(11));
        assertEquals("2000 S-band", CellularUtils.getLteBandName(23));
        assertEquals("450", CellularUtils.getLteBandName(31));
        assertEquals("2100+", CellularUtils.getLteBandName(65));
        assertEquals("NB-IoT", CellularUtils.getLteBandName(103));
    }

    @Test
    public void getLteBandName_unknownBands()
    {
        // Test unknown/invalid band numbers
        assertNull(CellularUtils.getLteBandName(0));
        assertNull(CellularUtils.getLteBandName(15)); // Band 15 doesn't exist
        assertNull(CellularUtils.getLteBandName(16)); // Band 16 doesn't exist
        assertNull(CellularUtils.getLteBandName(99));
        assertNull(CellularUtils.getLteBandName(-1));
        assertNull(CellularUtils.getLteBandName(1000));
    }
}