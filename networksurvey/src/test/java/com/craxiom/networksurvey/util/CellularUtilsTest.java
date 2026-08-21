package com.craxiom.networksurvey.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.craxiom.messaging.ConnectionStatus;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.NrRecordData;
import com.craxiom.networksurvey.model.NrRecordWrapper;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

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

    @Test
    public void formatNrBands_singleKnownBand()
    {
        assertEquals("n77 (TD 3700)", CellularUtils.formatNrBands(new int[]{77}));
    }

    @Test
    public void formatNrBands_multipleBands()
    {
        assertEquals("n77 (TD 3700), n78 (TD 3500)", CellularUtils.formatNrBands(new int[]{77, 78}));
    }

    @Test
    public void formatNrBands_unknownBand()
    {
        assertEquals("n999", CellularUtils.formatNrBands(new int[]{999}));
    }

    @Test
    public void formatNrBands_emptyAndNull()
    {
        assertEquals("", CellularUtils.formatNrBands(new int[0]));
        assertEquals("", CellularUtils.formatNrBands(null));
    }

    @Test
    public void downlinkNarfcnToBand_uniqueBands()
    {
        assertEquals(71, CellularUtils.downlinkNarfcnToBand(126270)); // 631.35 MHz, T-Mobile 600 MHz
        assertEquals(41, CellularUtils.downlinkNarfcnToBand(501390)); // 2506.95 MHz, below n38's range
        assertEquals(77, CellularUtils.downlinkNarfcnToBand(660000)); // 3900 MHz, above n78's upper edge
        assertEquals(79, CellularUtils.downlinkNarfcnToBand(700000)); // 4500 MHz
        assertEquals(260, CellularUtils.downlinkNarfcnToBand(2245000)); // 39 GHz mmWave, below n259's range
    }

    /**
     * The bands recovered by leaving undeployed shadowing bands out of the table. Each of these
     * would resolve to -1 if its shadowing band (listed in the comment) were added back.
     */
    @Test
    public void downlinkNarfcnToBand_bandsRecoveredByOmittingUndeployedShadows()
    {
        assertEquals(5, CellularUtils.downlinkNarfcnToBand(176000)); // n26 omitted
        assertEquals(18, CellularUtils.downlinkNarfcnToBand(172500)); // n26 omitted
        assertEquals(13, CellularUtils.downlinkNarfcnToBand(150000)); // n67 omitted
        assertEquals(12, CellularUtils.downlinkNarfcnToBand(146000)); // n67 omitted
        assertEquals(66, CellularUtils.downlinkNarfcnToBand(437000)); // n65 omitted, above n1's edge
        assertEquals(50, CellularUtils.downlinkNarfcnToBand(290000)); // n75, n92, n94 omitted
        assertEquals(51, CellularUtils.downlinkNarfcnToBand(285500)); // n76, n91, n93 omitted
        assertEquals(46, CellularUtils.downlinkNarfcnToBand(792000)); // n47 omitted
    }

    @Test
    public void downlinkNarfcnToBand_ambiguousReturnsUnknown()
    {
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(520000)); // n38 and n41
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(390000)); // n2 and n25
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(425000)); // n1 and n66
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(640000)); // n48, n77, and n78
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(2075000)); // n257 and n261
    }

    /**
     * Bands that no NARFCN can ever resolve to, because every value in their range is also inside
     * a deployed band that the table cannot drop. This is a deliberate consequence of refusing to
     * guess, not an oversight, and it is asserted here so that any future table edit which changes
     * the set has to acknowledge it. Notably n78 sits entirely inside n77.
     */
    @Test
    public void downlinkNarfcnToBand_permanentlyShadowedBands()
    {
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(430000)); // n1, inside n66
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(392000)); // n2, inside n25
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(152000)); // n14, inside n28
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(471000)); // n30, inside n40
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(519000)); // n38, inside n41
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(645000)); // n48, inside n77 and n78
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(630000)); // n78, inside n77
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(381000)); // n101, inside n39
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(2080000)); // n261, inside n257
    }

    @Test
    public void downlinkNarfcnToBand_boundaries()
    {
        assertEquals(71, CellularUtils.downlinkNarfcnToBand(123400)); // n71 lower bound
        assertEquals(71, CellularUtils.downlinkNarfcnToBand(130400)); // n71 upper bound
        assertEquals(41, CellularUtils.downlinkNarfcnToBand(499200)); // n41 lower bound
        assertEquals(41, CellularUtils.downlinkNarfcnToBand(513999)); // last NARFCN below n38's range
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(514000)); // n38 starts, ambiguous with n41
        // n41's table entry ends at 537999, so 538000 belongs to n7 alone. It is the ONLY NARFCN
        // in n7's 524000-538000 range that resolves; n41 shadows the other 14,000.
        assertEquals(7, CellularUtils.downlinkNarfcnToBand(538000));
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(537999)); // one below, still ambiguous
    }

    @Test
    public void downlinkNarfcnToBand_invalidAndUnmatched()
    {
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(-1));
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(0)); // valid raster point, no band
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(3279166)); // beyond the global raster
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(Integer.MAX_VALUE)); // CellInfo.UNAVAILABLE
    }

    @Test
    public void formatNrBands_fallsBackToNarfcnWhenBandsEmpty()
    {
        assertEquals("n71 (600)", CellularUtils.formatNrBands(new int[0], 126270));
        assertEquals("n71 (600)", CellularUtils.formatNrBands(null, 126270));
    }

    @Test
    public void formatNrBands_reportedBandsWinOverNarfcn()
    {
        assertEquals("n78 (TD 3500)", CellularUtils.formatNrBands(new int[]{78}, 126270));
    }

    @Test
    public void formatNrBands_ambiguousOrInvalidNarfcnLeavesBandBlank()
    {
        assertEquals("", CellularUtils.formatNrBands(new int[0], 640000)); // n48/n77/n78
        assertEquals("", CellularUtils.formatNrBands(new int[0], Integer.MAX_VALUE));
        assertEquals("", CellularUtils.formatNrBands(null, -1));
    }

    @Test
    public void selectSecondaryServingNrCell_prefersSecondaryServing()
    {
        NrRecordWrapper secondaryServing = buildNrWrapper(101, -110f, ConnectionStatus.SECONDARY_SERVING);
        NrRecordWrapper strongerNeighbor = buildNrWrapper(102, -80f, ConnectionStatus.NEIGHBOR);

        // The explicitly reported secondary serving cell wins over a stronger cell.
        assertEquals(secondaryServing,
                CellularUtils.selectSecondaryServingNrCell(Arrays.asList(strongerNeighbor, secondaryServing)));
    }

    @Test
    public void selectSecondaryServingNrCell_firstSecondaryServingWinsWhenMultiple()
    {
        // NR carrier aggregation under SA can produce more than one SECONDARY_SERVING record in a
        // single scan. The first in list order is displayed; this pins that behavior.
        NrRecordWrapper firstSecondary = buildNrWrapper(201, -95f, ConnectionStatus.SECONDARY_SERVING);
        NrRecordWrapper secondSecondary = buildNrWrapper(202, -85f, ConnectionStatus.SECONDARY_SERVING);

        assertEquals(firstSecondary,
                CellularUtils.selectSecondaryServingNrCell(Arrays.asList(firstSecondary, secondSecondary)));
    }

    @Test
    public void selectSecondaryServingNrCell_noSelectionWithoutSecondaryServing()
    {
        // No heuristic: cells with an unknown or neighbor status must never be promoted, and a
        // PRIMARY_SERVING record in the non-serving list (a contradictory device report) does not
        // qualify either.
        NrRecordWrapper unknown = buildNrWrapper(301, -85f, ConnectionStatus.UNKNOWN);
        NrRecordWrapper neighbor = buildNrWrapper(302, -80f, ConnectionStatus.NEIGHBOR);
        NrRecordWrapper primary = buildNrWrapper(303, -75f, ConnectionStatus.PRIMARY_SERVING);

        assertNull(CellularUtils.selectSecondaryServingNrCell(Arrays.asList(unknown, neighbor, primary)));
    }

    @Test
    public void selectSecondaryServingNrCell_unrecognizedStatusNotSelected()
    {
        // A future enum value deserializes as UNRECOGNIZED; it must not be treated as secondary.
        NrRecordData.Builder dataBuilder = NrRecordData.newBuilder();
        dataBuilder.setConnectionStatusValue(999);
        NrRecord record = NrRecord.newBuilder().setData(dataBuilder).build();
        NrRecordWrapper unrecognized = new NrRecordWrapper(record, new int[]{77});

        assertNull(CellularUtils.selectSecondaryServingNrCell(Collections.singletonList(unrecognized)));
    }

    @Test
    public void selectSecondaryServingNrCell_emptyAndNullList()
    {
        assertNull(CellularUtils.selectSecondaryServingNrCell(Collections.emptyList()));
        assertNull(CellularUtils.selectSecondaryServingNrCell(null));
    }

    /**
     * Builds a minimal NR record wrapper for the selection tests. Each wrapper gets a distinct PCI
     * so the assertions stay meaningful even if a value-based equals is ever added to the wrapper.
     *
     * @param pci              The PCI to set, distinguishing this record from the others in a test.
     * @param ssRsrp           The SS-RSRP value to set, or null to leave it unset.
     * @param connectionStatus The connection status to set on the record.
     */
    private NrRecordWrapper buildNrWrapper(int pci, Float ssRsrp, ConnectionStatus connectionStatus)
    {
        NrRecordData.Builder dataBuilder = NrRecordData.newBuilder();
        dataBuilder.setPci(Int32Value.newBuilder().setValue(pci).build());
        dataBuilder.setConnectionStatus(connectionStatus);
        if (ssRsrp != null)
        {
            dataBuilder.setSsRsrp(FloatValue.newBuilder().setValue(ssRsrp).build());
        }

        NrRecord record = NrRecord.newBuilder().setData(dataBuilder).build();
        return new NrRecordWrapper(record, new int[]{77});
    }
}