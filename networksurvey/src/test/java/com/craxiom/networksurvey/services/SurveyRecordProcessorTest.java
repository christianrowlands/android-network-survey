package com.craxiom.networksurvey.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.location.GnssStatus;
import android.location.Location;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    // buildUsedInFixMap joins the GnssStatus, which is the only place Android reports whether a
    // satellite was used in the position solution, onto the raw GnssMeasurements the GNSS survey
    // records are built from. The measurements carry no such flag of their own.

    /**
     * Builds a {@link GnssStatus} stand-in. The class is final and has no public constructor below
     * API 30, so the four accessors the mapping reads are stubbed directly.
     *
     * @param satellites One {@code {constellationType, svid, usedInFix}} triple per entry, where
     *                   {@code usedInFix} is 1 for used and 0 for not used.
     */
    private static GnssStatus gnssStatus(int[]... satellites)
    {
        GnssStatus status = mock(GnssStatus.class);
        when(status.getSatelliteCount()).thenReturn(satellites.length);
        for (int i = 0; i < satellites.length; i++)
        {
            when(status.getConstellationType(i)).thenReturn(satellites[i][0]);
            when(status.getSvid(i)).thenReturn(satellites[i][1]);
            when(status.usedInFix(i)).thenReturn(satellites[i][2] == 1);
        }
        return status;
    }

    @Test
    public void buildUsedInFixMap_mapsEachSatelliteToItsUsedInFixFlag()
    {
        Map<Long, Boolean> map = SurveyRecordProcessor.buildUsedInFixMap(gnssStatus(
                new int[]{GnssStatus.CONSTELLATION_GPS, 16, 1},
                new int[]{GnssStatus.CONSTELLATION_GPS, 23, 0}));

        assertEquals(2, map.size());
        assertTrue(map.get(SurveyRecordProcessor.createSatelliteKey(GnssStatus.CONSTELLATION_GPS, 16)));
        assertEquals(Boolean.FALSE, map.get(SurveyRecordProcessor.createSatelliteKey(GnssStatus.CONSTELLATION_GPS, 23)));
    }

    @Test
    public void buildUsedInFixMap_sameSvidInDifferentConstellations_staysSeparate()
    {
        // GPS PRN 5 and GLONASS slot 5 are different satellites, so the constellation has to be
        // part of the key or one would overwrite the other.
        Map<Long, Boolean> map = SurveyRecordProcessor.buildUsedInFixMap(gnssStatus(
                new int[]{GnssStatus.CONSTELLATION_GPS, 5, 1},
                new int[]{GnssStatus.CONSTELLATION_GLONASS, 5, 0}));

        assertEquals(2, map.size());
        assertTrue(map.get(SurveyRecordProcessor.createSatelliteKey(GnssStatus.CONSTELLATION_GPS, 5)));
        assertEquals(Boolean.FALSE, map.get(SurveyRecordProcessor.createSatelliteKey(GnssStatus.CONSTELLATION_GLONASS, 5)));
    }

    @Test
    public void buildUsedInFixMap_multiFrequencySatellite_usedOnAnySignalCountsAsUsed()
    {
        // A modern satellite is reported once per signal (for example GPS L1 and L5), and the
        // used in fix flag is per signal. usedInSolution describes the satellite, so any signal
        // being used makes the satellite used, whatever order the entries arrive in.
        Map<Long, Boolean> notUsedFirst = SurveyRecordProcessor.buildUsedInFixMap(gnssStatus(
                new int[]{GnssStatus.CONSTELLATION_GPS, 10, 0},
                new int[]{GnssStatus.CONSTELLATION_GPS, 10, 1}));
        Map<Long, Boolean> usedFirst = SurveyRecordProcessor.buildUsedInFixMap(gnssStatus(
                new int[]{GnssStatus.CONSTELLATION_GPS, 10, 1},
                new int[]{GnssStatus.CONSTELLATION_GPS, 10, 0}));

        Long key = SurveyRecordProcessor.createSatelliteKey(GnssStatus.CONSTELLATION_GPS, 10);
        assertEquals(1, notUsedFirst.size());
        assertTrue(notUsedFirst.get(key));
        assertTrue(usedFirst.get(key));
    }

    @Test
    public void buildUsedInFixMap_satelliteMissingFromStatus_hasNoEntry()
    {
        // A measurement for a satellite the status did not mention must leave usedInSolution
        // unset rather than report false, since the receiver said nothing about it.
        Map<Long, Boolean> map = SurveyRecordProcessor.buildUsedInFixMap(gnssStatus(
                new int[]{GnssStatus.CONSTELLATION_GPS, 16, 1}));

        assertNull(map.get(SurveyRecordProcessor.createSatelliteKey(GnssStatus.CONSTELLATION_GALILEO, 16)));
    }

    @Test
    public void buildUsedInFixMap_emptyStatus_isEmpty()
    {
        assertTrue(SurveyRecordProcessor.buildUsedInFixMap(gnssStatus()).isEmpty());
    }

    @Test
    public void createSatelliteKey_highSvids_doNotCollideAcrossConstellations()
    {
        // QZSS svids run to 200 and SBAS to 158, so the key has to keep them apart from a
        // neighbouring constellation type rather than folding them into the same bucket.
        assertEquals(
                SurveyRecordProcessor.createSatelliteKey(GnssStatus.CONSTELLATION_QZSS, 193),
                SurveyRecordProcessor.createSatelliteKey(GnssStatus.CONSTELLATION_QZSS, 193));
        assertTrue(!SurveyRecordProcessor.createSatelliteKey(GnssStatus.CONSTELLATION_QZSS, 193)
                .equals(SurveyRecordProcessor.createSatelliteKey(GnssStatus.CONSTELLATION_SBAS, 193)));
    }

    // getLocationAgeMs is the single source of the locationAge stamped on every record type. It
    // reads Location#getElapsedRealtimeNanos (API 17) rather than Location#getElapsedRealtimeAgeMillis
    // (API 33) so that the age is reported on every version this app supports. 0 means unknown.

    /**
     * Builds a {@link Location} whose elapsed realtime is the given number of nanoseconds.
     */
    private static Location locationAtRealtimeNanos(long elapsedRealtimeNanos)
    {
        Location location = mock(Location.class);
        when(location.getElapsedRealtimeNanos()).thenReturn(elapsedRealtimeNanos);
        return location;
    }

    @Test
    public void getLocationAgeMs_reportsTheAgeWithoutRequiringAndroid13()
    {
        // 30 seconds of elapsed realtime between the fix and the reference instant.
        Location location = locationAtRealtimeNanos(10_000L * 1_000_000L);

        assertEquals(30_000, SurveyRecordProcessor.getLocationAgeMs(location, 40_000L));
    }

    @Test
    public void getLocationAgeMs_fixWithNoElapsedRealtime_isUnknown()
    {
        // A Location that never had its elapsed realtime set would otherwise report the whole
        // device uptime as its age, which is a confident wrong answer.
        assertEquals(0, SurveyRecordProcessor.getLocationAgeMs(locationAtRealtimeNanos(0L), 40_000L));
        assertEquals(0, SurveyRecordProcessor.getLocationAgeMs(locationAtRealtimeNanos(-1L), 40_000L));
    }

    @Test
    public void getLocationAgeMs_fixNewerThanTheReference_isUnknownRatherThanNegative()
    {
        // The caller reads the clock once and then reads several listeners, so a fresher fix can
        // land in between. A negative age would wrap to a huge value when read as a uint32.
        Location location = locationAtRealtimeNanos(50_000L * 1_000_000L);

        assertEquals(0, SurveyRecordProcessor.getLocationAgeMs(location, 40_000L));
    }

    @Test
    public void getLocationAgeMs_hugeAge_clampsInsteadOfOverflowing()
    {
        // Beyond about 24 days the millisecond age no longer fits an int. Wrapping would turn a
        // hopelessly stale fix into one that looks fresh.
        Location location = locationAtRealtimeNanos(1_000_000L);

        long reference = Integer.MAX_VALUE + 60_000L;
        assertEquals(Integer.MAX_VALUE, SurveyRecordProcessor.getLocationAgeMs(location, reference));
    }
}
