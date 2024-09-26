package com.craxiom.networksurvey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.craxiom.networksurvey.util.CalculationUtils;

import org.junit.Test;

/**
 * Calculation Utils unit tests, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class CalculationUtilsUnitTest
{
    @Test
    public void validateLteCellId_isCorrect()
    {
        assertTrue(CalculationUtils.isLteCellIdValid(19_292));
        assertTrue(CalculationUtils.isLteCellIdValid(0));
        assertTrue(CalculationUtils.isLteCellIdValid(268_435_455));
    }

    @Test
    public void validateLteCellId_isInvalid()
    {
        assertFalse(CalculationUtils.isLteCellIdValid(-1));
        assertFalse(CalculationUtils.isLteCellIdValid(-9_999));
        assertFalse(CalculationUtils.isLteCellIdValid(268_435_456));
        assertFalse(CalculationUtils.isLteCellIdValid(1_268_435_456));
    }

    @Test
    public void getEnodebId_isCorrect()
    {
        assertEquals(1_809, CalculationUtils.getEnodebIdFromCellId(463_128));
        assertEquals(0, CalculationUtils.getEnodebIdFromCellId(0));
        assertEquals(0, CalculationUtils.getEnodebIdFromCellId(1));
        assertEquals(1_048_575, CalculationUtils.getEnodebIdFromCellId(268_435_455));
    }

    @Test
    public void getEnodebId_isWrong()
    {
        assertNotEquals(15, CalculationUtils.getEnodebIdFromCellId(1));
        assertNotEquals(8, CalculationUtils.getEnodebIdFromCellId(48_624));
    }

    @Test
    public void getSectorId_isCorrect()
    {
        assertEquals(24, CalculationUtils.getSectorIdFromCellId(463_128));
        assertEquals(0, CalculationUtils.getSectorIdFromCellId(0));
        assertEquals(1, CalculationUtils.getSectorIdFromCellId(1));
        assertEquals(255, CalculationUtils.getSectorIdFromCellId(268_435_455));
    }

    @Test
    public void getSectorId_isWrong()
    {
        assertNotEquals(15, CalculationUtils.getSectorIdFromCellId(1));
        assertNotEquals(8, CalculationUtils.getSectorIdFromCellId(48_624));
    }

    @Test
    public void getUmtsShortCellIdFromCid_isCorrect()
    {
        assertEquals(17315, CalculationUtils.getUmtsShortCellIdFromCid(1655715)); // Lower 16 bits
        assertEquals(39540, CalculationUtils.getUmtsShortCellIdFromCid(3054196)); // Lower 16 bits
        assertEquals(12416, CalculationUtils.getUmtsShortCellIdFromCid(306000000));
        assertEquals(65535, CalculationUtils.getUmtsShortCellIdFromCid(268435455)); // Lower 16 bits max value
    }

    @Test
    public void getUmtsShortCellIdFromCid_isWrong()
    {
        assertNotEquals(4660, CalculationUtils.getUmtsShortCellIdFromCid(14709556)); // Testing wrong value
        assertNotEquals(0, CalculationUtils.getUmtsShortCellIdFromCid(305896)); // Should not return 0
    }

    @Test
    public void getUmtsRncFromCid_isCorrect()
    {
        assertEquals(464, CalculationUtils.getUmtsRncFromCid(30419896)); // Upper 12 bits
        assertEquals(4095, CalculationUtils.getUmtsRncFromCid(268435455)); // Upper 12 bits max value
        assertEquals(0, CalculationUtils.getUmtsRncFromCid(4660)); // No bits in upper 12
        assertEquals(465, CalculationUtils.getUmtsRncFromCid(30539760)); // Only upper 12 bits
    }

    @Test
    public void getUmtsRncFromCid_isWrong()
    {
        assertNotEquals(4660, CalculationUtils.getUmtsRncFromCid(1450556)); // Testing wrong value
        assertNotEquals(65535, CalculationUtils.getUmtsRncFromCid(305419896)); // Should not return 65535
    }

    @Test
    public void getPSS_isCorrect()
    {
        assertEquals(0, CalculationUtils.getPrimarySyncSequence(0));
        assertEquals(1, CalculationUtils.getPrimarySyncSequence(1));
        assertEquals(1, CalculationUtils.getPrimarySyncSequence(247));
        assertEquals(2, CalculationUtils.getPrimarySyncSequence(503));
    }

    @Test
    public void getPSS_isWrong()
    {
        assertNotEquals(0, CalculationUtils.getPrimarySyncSequence(79));
    }

    @Test
    public void getSSS_isCorrect()
    {
        assertEquals(0, CalculationUtils.getSecondarySyncSequence(0));
        assertEquals(0, CalculationUtils.getSecondarySyncSequence(1));
        assertEquals(82, CalculationUtils.getSecondarySyncSequence(248));
        assertEquals(167, CalculationUtils.getSecondarySyncSequence(503));
    }

    @Test
    public void getSSS_isWrong()
    {
        assertNotEquals(25, CalculationUtils.getPrimarySyncSequence(79));
    }

    @Test
    public void getNetworkType()
    {
        assertEquals("Unknown", CalculationUtils.getNetworkType(0));
        assertEquals("GPRS", CalculationUtils.getNetworkType(1));
        assertEquals("EDGE", CalculationUtils.getNetworkType(2));
        assertEquals("UMTS", CalculationUtils.getNetworkType(3));
        assertEquals("CDMA", CalculationUtils.getNetworkType(4));
        assertEquals("EVDO 0", CalculationUtils.getNetworkType(5));
        assertEquals("EVDO A", CalculationUtils.getNetworkType(6));
        assertEquals("CDMA - 1xRTT", CalculationUtils.getNetworkType(7));
        assertEquals("HSDPA", CalculationUtils.getNetworkType(8));
        assertEquals("HSUPA", CalculationUtils.getNetworkType(9));
        assertEquals("HSPA", CalculationUtils.getNetworkType(10));
        assertEquals("IDEN", CalculationUtils.getNetworkType(11));
        assertEquals("EVDO B", CalculationUtils.getNetworkType(12));
        assertEquals("LTE", CalculationUtils.getNetworkType(13));
        assertEquals("CDMA - eHRPD", CalculationUtils.getNetworkType(14));
        assertEquals("HSPA+", CalculationUtils.getNetworkType(15));
        assertEquals("GSM", CalculationUtils.getNetworkType(16));
        assertEquals("TD-SCDMA", CalculationUtils.getNetworkType(17));
        assertEquals("IWLAN", CalculationUtils.getNetworkType(18));
        assertEquals("LTE-CA", CalculationUtils.getNetworkType(19));
        assertEquals("NR", CalculationUtils.getNetworkType(20));
    }
}