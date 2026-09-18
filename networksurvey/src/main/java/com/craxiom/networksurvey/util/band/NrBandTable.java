package com.craxiom.networksurvey.util.band;

/**
 * NR operating bands, their downlink NARFCN ranges, and which of them may label a live cell.
 * <p>
 * Split out of {@code CellularUtils} so the table and its provenance notes do not crowd the
 * general cellular helpers. {@code CellularUtils} keeps the public entry points and delegates.
 */
public final class NrBandTable
{
    /**
     * Marks a band in {@link #NR_BANDS} that may be used to label a live cell, meaning
     * {@link #downlinkNarfcnToBands(int)} will return it.
     */
    private static final int RESOLUTION = 1;

    /**
     * Marks a band in {@link #NR_BANDS} that is listed for reference only. It is real and belongs
     * in a band reference table a user can browse, but it never labels a live cell, because
     * including it would make a band operators actually deploy unresolvable. The trailing comment
     * on each such row says which band it would hurt.
     */
    private static final int REFERENCE_ONLY = 0;

    /**
     * Every NR operating band that has a downlink NR-ARFCN range, and whether it may be used to
     * label a live cell.
     * <p>
     * The table serves two purposes that pull in opposite directions. A band reference a user
     * browses should be complete, because a table with holes reads as a bug. Resolving a channel
     * number to a band should be conservative, because NR ranges overlap heavily and a band that
     * is real but undeployed can make a band that is real and deployed unresolvable. So every
     * band is listed, and the fourth column says which of the two roles it plays:
     * {@link #RESOLUTION} bands do both jobs, {@link #REFERENCE_ONLY} bands are browsable but
     * never label a live cell.
     * <p>
     * Worked example of why the split exists: n109 has exactly the same downlink range as n50
     * (286400-303400). Letting it resolve would take n50 from 8,599 uniquely resolvable channels
     * to zero, so every n50 cell on a device that reports no bands would show "n50 / n109"
     * forever. n50 is deployed; n109 is not. Listing n109 for reference costs nothing, and
     * resolving it costs n50 entirely.
     * <p>
     * SUL bands (n80 to n84, n86, n89, n95, n97 to n99) are absent because they have no downlink
     * range at all, so they cannot be keyed by a downlink NARFCN. They belong in the richer band
     * model that backs the reference screen, which carries uplink ranges too. FR1 rows are verified against
     * 3GPP TS 38.101-1 Table 5.4.2.3-1 ("Downlink Range of NREF"), release k00. FR2 rows (n257 and
     * above) come from TS 38.101-2, which has not been checked against a spec document here.
     * The NS Analytics web app carries its own copy in cellular-band-utils.ts. As of the
     * corrections made here that copy has diverged and this table is the authoritative one: the
     * web app still lists the phantom LTE band 108, still lacks LTE band 111, lacks n31, n54,
     * n72, n87 and n88, and resolves bands that are reference-only here, including the SUL bands
     * that have no downlink at all. Port these changes there rather than copying the other way.
     * <p>
     * Run {@code tools/bandtable/verify_band_tables.py} after editing this table. It checks every
     * row against the specification and flags any band the specification defines that is neither
     * listed here nor recorded in {@code tools/bandtable/exclusions.py} with a reason.
     * <p>
     * Unlike the E-UTRA table in {@link LteBandTable}, these ranges overlap heavily, which is why
     * {@link #downlinkNarfcnToBand(int)} refuses to answer for a NARFCN in more than one band.
     * <p>
     * Several of the reasons below turn on a band having no known commercial deployment, which is
     * a judgement about the world in September 2026 rather than a fact about the specification.
     * Re-check those when bumping the specification release; the structural reasons (SUL is
     * uplink only, NTN is satellite, n90 duplicates n41) do not age.
     * <p>
     * A band is marked {@link #REFERENCE_ONLY} when letting it resolve would shadow a band that
     * operators actually deploy while adding no resolvable range of its own. The cost of the alternative
     * is measurable: including every FR1 band the specification defines would raise the share of
     * ambiguous channels by roughly a sixth and roughly triple the number of never-resolvable
     * bands, swallowing n5, n12, n13, n41, n50, n66 and n71, which are among the most widely
     * deployed bands in the world. Every omission below either
     * has no known commercial deployment, or cannot carry a downlink at all:
     * <ul>
     *   <li>SUL bands (n80-n84, n86, n89, n95, n97-n99): uplink only, a downlink NARFCN can never be one</li>
     *   <li>n85: FDD rather than SUL, but undeployed, and including it would swallow n12 entirely</li>
     *   <li>NTN bands (n254-n256): satellite service, not reported for terrestrial cells</li>
     *   <li>n90: spectrum-identical duplicate of n41 (it exists for UE capability signaling)</li>
     *   <li>n105: no known commercial deployments, and it would permanently shadow n71</li>
     *   <li>n26: no confirmed NR deployments; including it shadows both n5 and n18</li>
     *   <li>n65: no known commercial deployments (operators use n1 at 2100 MHz); it has the
     *       identical range to n66, so including it makes n66 unresolvable everywhere</li>
     *   <li>n67: SDL, undeployed (700 SDL lots largely went unsold); it shadows n13</li>
     *   <li>n47: V2X sidelink, so no gNB transmits a downlink there; it shadows part of n46</li>
     *   <li>n75, n76, n91-n94: SDL and undeployed; they shadow n50 and n51</li>
     * </ul>
     * <p>
     * Some overlaps cannot be removed this way because both bands are real and deployed, so a
     * NARFCN in those shared ranges stays unresolved by design rather than being guessed at.
     * Preferring the narrower band would be wrong: US C-band at 3700-3800 MHz is n77 but falls in
     * n78's range, so a narrower-wins rule would confidently mislabel it.
     * <p>
     * Twelve bands are shadowed completely, meaning every NARFCN in their range also falls in
     * another band, so {@link #downlinkNarfcnToBand(int)} can never return them:
     * <ul>
     *   <li>n1 inside n66</li>
     *   <li>n2 inside n25 (US PCS)</li>
     *   <li>n14 inside n28 (public safety / FirstNet)</li>
     *   <li>n30 inside n40</li>
     *   <li>n38 inside n41</li>
     *   <li>n48 inside n77 and n78</li>
     *   <li>n78 inside n77</li>
     *   <li>n96 tiled exactly by n102 and n104</li>
     *   <li>n101 inside n39</li>
     *   <li>n102 inside n96</li>
     *   <li>n104 inside n96</li>
     *   <li>n261 inside n257</li>
     * </ul>
     * n102 and n104 are subdivisions of n96 rather than collisions: n96 covers the whole 6 GHz
     * band and those two slice it, so their containment is the spec's intent, not an error here.
     * Because the two subdivisions are contiguous and cover n96 exactly, none of the three can be
     * resolved from a channel number alone.
     * <p>
     * Across the whole table, roughly a quarter of the covered NARFCN values fall in more
     * than one band. That is why {@link #downlinkNarfcnToBands(int)} exists: callers that would
     * otherwise render nothing can show the candidate list instead. The exact set of fully
     * shadowed bands is asserted by NarfcnResolutionTest rather than restated here, so that it
     * cannot drift from the table.
     */
    private static final int[][] NR_BANDS = {
            // Band, lower NARFCN, upper NARFCN, role
            {1, 422000, 434000, RESOLUTION},
            {2, 386000, 398000, RESOLUTION},
            {3, 361000, 376000, RESOLUTION},
            {5, 173800, 178800, RESOLUTION},
            {7, 524000, 538000, RESOLUTION},
            {8, 185000, 192000, RESOLUTION},
            {12, 145800, 149200, RESOLUTION},
            {13, 149200, 151200, RESOLUTION},
            {14, 151600, 153600, RESOLUTION},
            {18, 172000, 175000, RESOLUTION},
            {20, 158200, 164200, RESOLUTION},
            {24, 305000, 311800, RESOLUTION},
            {25, 386000, 399000, RESOLUTION},
            {26, 171800, 178800, REFERENCE_ONLY}, // it has no confirmed NR deployments and would shadow both n5 and n18
            {28, 151600, 160600, RESOLUTION},
            {29, 143400, 145600, RESOLUTION},
            {30, 470000, 472000, RESOLUTION},
            {31, 92500, 93500, RESOLUTION},
            {34, 402000, 405000, RESOLUTION},
            {38, 514000, 524000, RESOLUTION},
            {39, 376000, 384000, RESOLUTION},
            {40, 460000, 480000, RESOLUTION},
            {41, 499200, 537999, RESOLUTION},
            {46, 743334, 795000, RESOLUTION},
            {47, 790334, 795000, REFERENCE_ONLY}, // it is V2X sidelink, so no gNB transmits a downlink there, and it shadows part of n46
            {48, 636667, 646666, RESOLUTION},
            {50, 286400, 303400, RESOLUTION},
            {51, 285400, 286400, RESOLUTION},
            {53, 496700, 499000, RESOLUTION},
            {54, 334000, 335000, RESOLUTION},
            {65, 422000, 440000, REFERENCE_ONLY}, // it has no known commercial deployments and shares n66's range, which it would hide
            {66, 422000, 440000, RESOLUTION},
            {67, 147600, 151600, REFERENCE_ONLY}, // it is undeployed SDL and shadows n13
            {68, 150600, 156600, REFERENCE_ONLY}, // it overlaps n13, n14 and n28, and would cost n28 most of its resolvable range
            {70, 399000, 404000, RESOLUTION},
            {71, 123400, 130400, RESOLUTION},
            {72, 92200, 93200, RESOLUTION},
            {74, 295000, 303600, RESOLUTION},
            {75, 286400, 303400, REFERENCE_ONLY}, // it is undeployed SDL and shadows n50
            {76, 285400, 286400, REFERENCE_ONLY}, // it is undeployed SDL and shadows n51
            {77, 620000, 680000, RESOLUTION},
            {78, 620000, 653333, RESOLUTION},
            {79, 693334, 733333, RESOLUTION},
            {85, 145600, 149200, REFERENCE_ONLY}, // it is undeployed and would swallow n12 entirely
            {87, 84000, 85000, RESOLUTION},
            {88, 84400, 85400, RESOLUTION},
            {90, 499200, 537999, REFERENCE_ONLY}, // it duplicates n41's spectrum and exists only for UE capability signalling
            {91, 285400, 286400, REFERENCE_ONLY}, // it is undeployed SDL and shadows n51
            {92, 286400, 303400, REFERENCE_ONLY}, // it is undeployed SDL and shadows n50
            {93, 285400, 286400, REFERENCE_ONLY}, // it is undeployed SDL and shadows n51
            {94, 286400, 303400, REFERENCE_ONLY}, // it is undeployed SDL and shadows n50
            {96, 795000, 875000, RESOLUTION},
            {100, 183880, 185000, RESOLUTION},
            {101, 380000, 382000, RESOLUTION},
            {102, 795000, 828333, RESOLUTION},
            {104, 828334, 875000, RESOLUTION},
            {105, 122400, 130400, REFERENCE_ONLY}, // it has no known commercial deployments and would permanently shadow n71
            {106, 187000, 188000, REFERENCE_ONLY}, // it overlaps n8, which is widely deployed at 900 MHz
            {109, 286400, 303400, REFERENCE_ONLY}, // it has the identical range to n50, so resolving it would make n50 unresolvable everywhere
            {110, 286400, 287000, REFERENCE_ONLY}, // it overlaps n50 and n51
            {115, 295180, 302180, REFERENCE_ONLY}, // it overlaps n50 and n74
            // FR2 (mmWave). Not yet checked against TS 38.101-2.
            {257, 2054166, 2104165, RESOLUTION},
            {258, 2016667, 2070832, RESOLUTION},
            {259, 2270833, 2337499, RESOLUTION},
            {260, 2229166, 2279165, RESOLUTION},
            {261, 2070833, 2084999, RESOLUTION},
            {262, 2399166, 2415832, RESOLUTION},
            {263, 2564083, 2794249, RESOLUTION},
    };

    /**
     * Returns every 5G NR operating band whose downlink NARFCN range contains the given NARFCN.
     * <p>
     * NR ARFCN ranges overlap heavily, so a single NARFCN routinely falls in more than one band.
     * Reporting all of them lets callers show the candidates rather than showing nothing, which
     * matters because a quarter of the covered NARFCN space is ambiguous. See the
     * {@link #NR_BANDS} javadoc for the bands that are shadowed completely.
     *
     * @param narfcn The downlink NARFCN to look up.
     * @return The matching band numbers in ascending order, or an empty array when the NARFCN is
     * invalid or falls in no known band. Never null.
     */
    public static int[] downlinkNarfcnToBands(int narfcn)
    {
        int matchCount = 0;
        for (int[] band : NR_BANDS)
        {
            if (band[3] == RESOLUTION && narfcn >= band[1] && narfcn <= band[2]) matchCount++;
        }

        final int[] matches = new int[matchCount];
        int index = 0;
        for (int[] band : NR_BANDS)
        {
            if (band[3] == RESOLUTION && narfcn >= band[1] && narfcn <= band[2])
            {
                matches[index++] = band[0];
            }
        }

        return matches;
    }

    /**
     * Returns the band for a NARFCN only when exactly one contains it, and -1 otherwise. See
     * {@link #downlinkNarfcnToBands(int)} for why more than one is normal.
     */
    public static int downlinkNarfcnToBand(int narfcn)
    {
        final int[] bands = downlinkNarfcnToBands(narfcn);
        return bands.length == 1 ? bands[0] : -1;
    }

    private NrBandTable()
    {
    }
}
