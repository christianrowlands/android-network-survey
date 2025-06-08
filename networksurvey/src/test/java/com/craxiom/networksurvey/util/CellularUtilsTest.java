package com.craxiom.networksurvey.util;

import static org.junit.Assert.assertEquals;

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
        assertEquals(100000.0, CellularUtils.narfcnToFrequencyMhz(3279165), DELTA); // 24250.08 + (3279165 - 2016667) * 0.060 ≈ 100000.0 MHz
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
}