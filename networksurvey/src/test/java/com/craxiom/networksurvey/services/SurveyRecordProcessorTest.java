package com.craxiom.networksurvey.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link SurveyRecordProcessor}'s pure scan-level helpers. Runs under Robolectric
 * so the telephony SDK guards evaluate against a real API level, with Mockito mocks standing in
 * for the framework {@link CellInfo} objects that cannot be constructed in a host-side test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SurveyRecordProcessorTest
{
    private static CellInfoNr nrCell(boolean registered, int connectionStatus)
    {
        CellInfoNr cellInfo = mock(CellInfoNr.class);
        when(cellInfo.isRegistered()).thenReturn(registered);
        when(cellInfo.getCellConnectionStatus()).thenReturn(connectionStatus);
        return cellInfo;
    }

    private static CellInfoLte lteCell(boolean registered, int connectionStatus)
    {
        CellInfoLte cellInfo = mock(CellInfoLte.class);
        when(cellInfo.isRegistered()).thenReturn(registered);
        when(cellInfo.getCellConnectionStatus()).thenReturn(connectionStatus);
        return cellInfo;
    }

    // countFallbackEligibleNrCells gates the NR signal-strength fallback: the borrowed
    // measurement from SignalStrength can only be attributed to a cell when the scan contains
    // exactly one connected NR cell, since CellSignalStrengthNr carries no PCI to match on.

    @Test
    public void countFallbackEligibleNrCells_nsaSecondaryCell_countsOne()
    {
        // The motivating case: an LTE anchor plus the NSA NR data leg reported as
        // SECONDARY_SERVING. Only the NR cell is eligible.
        List<CellInfo> allCellInfo = List.of(
                lteCell(true, CellInfo.CONNECTION_PRIMARY_SERVING),
                nrCell(false, CellInfo.CONNECTION_SECONDARY_SERVING));

        assertEquals(1, SurveyRecordProcessor.countFallbackEligibleNrCells(allCellInfo));
    }

    @Test
    public void countFallbackEligibleNrCells_multipleConnectedNrCells_countsAll()
    {
        // NR CA under SA: a registered NR cell plus a SECONDARY_SERVING NR cell. Both are
        // eligible, so the fallback must not engage for either.
        List<CellInfo> allCellInfo = List.of(
                nrCell(true, CellInfo.CONNECTION_PRIMARY_SERVING),
                nrCell(false, CellInfo.CONNECTION_SECONDARY_SERVING));

        assertEquals(2, SurveyRecordProcessor.countFallbackEligibleNrCells(allCellInfo));
    }

    @Test
    public void countFallbackEligibleNrCells_nrNeighbors_doNotCount()
    {
        List<CellInfo> allCellInfo = List.of(
                nrCell(true, CellInfo.CONNECTION_PRIMARY_SERVING),
                nrCell(false, CellInfo.CONNECTION_NONE),
                nrCell(false, CellInfo.CONNECTION_UNKNOWN));

        assertEquals(1, SurveyRecordProcessor.countFallbackEligibleNrCells(allCellInfo));
    }

    @Test
    public void countFallbackEligibleNrCells_nonNrCells_doNotCount()
    {
        List<CellInfo> allCellInfo = List.of(
                lteCell(true, CellInfo.CONNECTION_PRIMARY_SERVING),
                lteCell(false, CellInfo.CONNECTION_SECONDARY_SERVING));

        assertEquals(0, SurveyRecordProcessor.countFallbackEligibleNrCells(allCellInfo));
    }

    @Test
    public void countFallbackEligibleNrCells_emptyList_countsZero()
    {
        assertEquals(0, SurveyRecordProcessor.countFallbackEligibleNrCells(Collections.emptyList()));
    }
}
