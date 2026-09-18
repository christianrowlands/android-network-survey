package com.craxiom.networksurvey.util.band;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.craxiom.networksurvey.util.CellularUtils;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tests for resolving a downlink NARFCN to its 5G NR operating band or bands.
 * <p>
 * These cases live here rather than in {@code CellularUtilsTest} because that class was already
 * close to the project's file size limit, and because NARFCN resolution is the piece most likely
 * to change when the band tables are edited. The sweep and set assertions below are deliberate
 * tripwires: they are meant to fail loudly when the meaning of the table changes, and to stay
 * quiet when an individual range is legitimately corrected.
 */
public class NarfcnResolutionTest
{
    /**
     * The RESOLUTION rows of {@code CellularUtils.NR_BANDS}, as {band, lowNarfcn, highNarfcn}.
     * Mirrored here so the sweep can walk the covered space without exposing the private table.
     * Reference-only bands are excluded on purpose: they are browsable but never label a cell.
     * If this drifts from the production table, {@link #coveredSpace_matchesProductionTable()}
     * fails.
     */
    private static final int[][] COVERED_RANGES = {
            {1, 422000, 434000}, {2, 386000, 398000}, {3, 361000, 376000}, {5, 173800, 178800},
            {7, 524000, 538000}, {8, 185000, 192000}, {12, 145800, 149200}, {13, 149200, 151200},
            {14, 151600, 153600}, {18, 172000, 175000}, {20, 158200, 164200}, {24, 305000, 311800},
            {25, 386000, 399000}, {28, 151600, 160600}, {29, 143400, 145600}, {30, 470000, 472000},
            {31, 92500, 93500}, {34, 402000, 405000}, {38, 514000, 524000}, {39, 376000, 384000},
            {40, 460000, 480000}, {41, 499200, 537999}, {46, 743334, 795000}, {48, 636667, 646666},
            {50, 286400, 303400}, {51, 285400, 286400}, {53, 496700, 499000}, {54, 334000, 335000},
            {66, 422000, 440000}, {70, 399000, 404000}, {71, 123400, 130400}, {72, 92200, 93200},
            {74, 295000, 303600}, {77, 620000, 680000}, {78, 620000, 653333}, {79, 693334, 733333},
            {87, 84000, 85000}, {88, 84400, 85400}, {96, 795000, 875000}, {100, 183880, 185000},
            {101, 380000, 382000}, {102, 795000, 828333}, {104, 828334, 875000}, {257, 2054166, 2104165},
            {258, 2016667, 2070832}, {259, 2270833, 2337499}, {260, 2229166, 2279165}, {261, 2070833, 2084999},
            {262, 2399166, 2415832}, {263, 2564083, 2794249},
    };

    /**
     * The bands that participate in downlink resolution. This is a curated set, not the full 3GPP
     * band list: SUL, NTN, and undeployed bands are deliberately excluded because including them
     * would shadow bands operators actually run. That rationale is prose in the production
     * javadoc, so it is pinned here as an assertion. A regenerated or edited table that changes
     * this membership silently changes what every NR cell displays.
     */
    private static final int[] RESOLVING_BANDS = {
            1, 2, 3, 5, 7, 8, 12, 13, 14, 18, 20, 24, 25, 28, 29,
            30, 31, 34, 38, 39, 40, 41, 46, 48, 50, 51, 53, 54, 66, 70,
            71, 72, 74, 77, 78, 79, 87, 88, 96, 100, 101, 102, 104, 257, 258,
            259, 260, 261, 262, 263,
    };

    /**
     * Bands that no NARFCN can ever resolve to on its own, because every value in their range is
     * also inside another band the table cannot drop.
     * <p>
     * n96 joined this set when n102's lower bound was corrected to the TS 38.101-1 value of
     * 795000. n102 (795000-828333) and n104 (828334-875000) are contiguous and together tile n96
     * (795000-875000) exactly, so no NARFCN in the 6 GHz band resolves to a single operating
     * band. The stale 796334 left a 1334 channel sliver where n96 stood alone, which is the only
     * reason it used to look resolvable.
     */
    private static final int[] FULLY_SHADOWED_BANDS =
            {1, 2, 14, 30, 38, 48, 78, 96, 101, 102, 104, 261};

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
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(800000)); // n102, inside n96
        assertEquals(-1, CellularUtils.downlinkNarfcnToBand(850000)); // n104, inside n96
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
    public void formatNrBands_invalidNarfcnLeavesBandBlank()
    {
        assertEquals("", CellularUtils.formatNrBands(new int[0], Integer.MAX_VALUE));
        assertEquals("", CellularUtils.formatNrBands(null, -1));
        assertEquals("", CellularUtils.formatNrBands(new int[0], 0)); // valid raster point, no band
    }

    /**
     * The behavior this phase exists to change. An ambiguous NARFCN used to render an empty Band
     * field, which hid the serving band entirely on devices that report no bands of their own.
     * Every candidate is now listed, without friendly names so the string still fits the row.
     */
    @Test
    public void formatNrBands_ambiguousNarfcnListsCandidates()
    {
        assertEquals("n48 / n77 / n78", CellularUtils.formatNrBands(new int[0], 640000));
        assertEquals("n2 / n25", CellularUtils.formatNrBands(new int[0], 390000));
        assertEquals("n7 / n38 / n41", CellularUtils.formatNrBands(new int[0], 524000));
        assertEquals("n1 / n66", CellularUtils.formatNrBands(null, 425000));
    }

    /**
     * The regression gate for the whole band layer. Walks every NARFCN the table covers and
     * asserts two things at once: that a covered channel always produces at least one candidate
     * band, and that collapsing that candidate list to a single answer reproduces exactly what
     * the previous single-band implementation returned. The second half is what makes it safe to
     * change the internals of resolution without changing behavior.
     */
    @Test
    public void sweep_everyCoveredNarfcnResolvesAndMatchesLegacyBehavior()
    {
        for (int[] range : COVERED_RANGES)
        {
            for (int narfcn = range[1]; narfcn <= range[2]; narfcn++)
            {
                final int[] candidates = CellularUtils.downlinkNarfcnToBands(narfcn);
                assertTrue("NARFCN " + narfcn + " is covered but resolved to no band",
                        candidates.length > 0);

                final int expected = candidates.length == 1 ? candidates[0] : -1;
                assertEquals("NARFCN " + narfcn + " changed its single-band answer",
                        expected, CellularUtils.downlinkNarfcnToBand(narfcn));
            }
        }
    }

    /**
     * A covered NARFCN must never render an empty Band field. This is the user-visible half of
     * the sweep above, and the specific defect this phase fixes.
     */
    @Test
    public void sweep_noCoveredNarfcnRendersABlankBand()
    {
        for (int[] range : COVERED_RANGES)
        {
            for (int narfcn = range[1]; narfcn <= range[2]; narfcn++)
            {
                assertTrue("NARFCN " + narfcn + " rendered a blank band",
                        !CellularUtils.formatNrBands(new int[0], narfcn).isEmpty());
            }
        }
    }

    /**
     * Tripwire. Asserting the set of permanently shadowed bands rather than a count of ambiguous
     * channels, because a count fails on every legitimate range correction while telling the
     * reader nothing, whereas a change to this set is always a change in meaning.
     */
    @Test
    public void shadowedBandSet_hasNotChanged()
    {
        final Set<Integer> shadowed = new TreeSet<>();
        for (int[] range : COVERED_RANGES)
        {
            boolean resolvableSomewhere = false;
            for (int narfcn = range[1]; narfcn <= range[2]; narfcn++)
            {
                if (CellularUtils.downlinkNarfcnToBand(narfcn) == range[0])
                {
                    resolvableSomewhere = true;
                    break;
                }
            }
            if (!resolvableSomewhere) shadowed.add(range[0]);
        }

        final Set<Integer> expected = new TreeSet<>();
        for (int band : FULLY_SHADOWED_BANDS) expected.add(band);

        final Set<Integer> newlyShadowed = new LinkedHashSet<>(shadowed);
        newlyShadowed.removeAll(expected);
        final Set<Integer> noLongerShadowed = new LinkedHashSet<>(expected);
        noLongerShadowed.removeAll(shadowed);

        assertEquals("The set of permanently shadowed NR bands changed. Newly shadowed: "
                        + newlyShadowed + ". No longer shadowed: " + noLongerShadowed
                        + ". This is a deliberate consequence of the band table, so update this"
                        + " assertion only after confirming the change is intended.",
                expected, shadowed);
    }

    /**
     * Pins which bands take part in downlink resolution at all. See {@link #RESOLVING_BANDS}.
     */
    @Test
    public void resolvingBandSet_hasNotChanged()
    {
        final Set<Integer> actual = new TreeSet<>();
        for (int[] range : COVERED_RANGES) actual.add(range[0]);

        final Set<Integer> expected = new TreeSet<>();
        for (int band : RESOLVING_BANDS) expected.add(band);

        assertEquals("The set of bands participating in NARFCN resolution changed. Excluding a"
                        + " band hides it from every cell display; including one can shadow a"
                        + " deployed band. Confirm the change is intended before updating this.",
                expected, actual);
    }

    /**
     * Guards the mirrored table above against drifting from the production one. Every value in
     * {@link #COVERED_RANGES} must actually resolve inside its own range, and the band just
     * outside a range must not claim it.
     */
    @Test
    public void coveredSpace_matchesProductionTable()
    {
        for (int[] range : COVERED_RANGES)
        {
            final int[] atLow = CellularUtils.downlinkNarfcnToBands(range[1]);
            final int[] atHigh = CellularUtils.downlinkNarfcnToBands(range[2]);

            assertTrue("Band " + range[0] + " does not contain its own low edge " + range[1],
                    contains(atLow, range[0]));
            assertTrue("Band " + range[0] + " does not contain its own high edge " + range[2],
                    contains(atHigh, range[0]));
        }
    }

    /**
     * The candidate list is the input to the display string, so its order is load bearing.
     */
    @Test
    public void downlinkNarfcnToBands_returnsAscendingCandidates()
    {
        assertArrayEquals(new int[]{48, 77, 78}, CellularUtils.downlinkNarfcnToBands(640000));
        assertArrayEquals(new int[]{7, 38, 41}, CellularUtils.downlinkNarfcnToBands(524000));
        assertArrayEquals(new int[]{2, 25}, CellularUtils.downlinkNarfcnToBands(390000));
        assertArrayEquals(new int[]{71}, CellularUtils.downlinkNarfcnToBands(126270));
        assertArrayEquals(new int[0], CellularUtils.downlinkNarfcnToBands(-1));
        assertArrayEquals(new int[0], CellularUtils.downlinkNarfcnToBands(Integer.MAX_VALUE));
    }

    /**
     * Reference-only bands must never label a live cell. n109 is the sharpest case: it has the
     * same downlink range as n50, so if it ever started resolving, every n50 cell on a device
     * that reports no bands of its own would read "n50 / n109" and n50 would become permanently
     * unresolvable. The other four guard bands that overlap widely deployed spectrum.
     */
    @Test
    public void referenceOnlyBands_neverResolve()
    {
        // n109 shares 286400-303400 with n50 exactly.
        assertArrayEquals(new int[]{50}, CellularUtils.downlinkNarfcnToBands(290000));
        // n110 (286400-287000) and n75/n92/n94 also sit on n50.
        assertArrayEquals(new int[]{50}, CellularUtils.downlinkNarfcnToBands(286500));
        // n115 (295180-302180) overlaps n50 and n74.
        assertArrayEquals(new int[]{50, 74}, CellularUtils.downlinkNarfcnToBands(300000));
        // n68 (150600-156600) would otherwise join n13, n14 and n28 here.
        assertArrayEquals(new int[]{13}, CellularUtils.downlinkNarfcnToBands(151000));
        assertArrayEquals(new int[]{14, 28}, CellularUtils.downlinkNarfcnToBands(152000));
        // n106 (187000-188000) sits inside n8.
        assertArrayEquals(new int[]{8}, CellularUtils.downlinkNarfcnToBands(187500));
    }

    /**
     * n50 keeps a range where it resolves on its own. This is the specific regression the
     * reference-only split exists to prevent, so it is asserted directly rather than being left
     * to the shadowed-set tripwire.
     */
    @Test
    public void n50_remainsResolvable()
    {
        assertEquals(50, CellularUtils.downlinkNarfcnToBand(290000));
        assertEquals(8, CellularUtils.downlinkNarfcnToBand(187500));
        assertEquals(28, CellularUtils.downlinkNarfcnToBand(158000));
    }

    private static boolean contains(int[] values, int target)
    {
        return Arrays.stream(values).anyMatch(value -> value == target);
    }
}
