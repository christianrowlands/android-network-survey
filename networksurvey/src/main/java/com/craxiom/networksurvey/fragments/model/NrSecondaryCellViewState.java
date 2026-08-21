package com.craxiom.networksurvey.fragments.model;

/**
 * View state for the NR Secondary Cell details card on the cellular details screen, shown when the
 * device reports an NR cell as SECONDARY_SERVING: on 5G NSA (EN-DC) that is the NR cell carrying
 * the 5G data alongside the LTE anchor, and on 5G SA with NR carrier aggregation it is an NR
 * SCell. On NSA the NR leg detaches within seconds of data going idle, so instead of vanishing the
 * card can also render the last-seen cell dimmed with an Idle badge (see {@code idle}). Kept as a
 * record over immutable display values so the ViewModel's distinct-until-changed setter works via
 * value equality. A null view state hides the card.
 * <p>
 * The ticking "Last seen ..." age deliberately lives outside this record, in its own LiveData, so
 * that holding the card idle does not rebuild and re-render the whole card body once per scan.
 *
 * @param band      The formatted NR band display value (e.g. "n77 (TD 3700)"), or an empty string.
 * @param frequency The formatted frequency display value (e.g. "3709.920 MHz"), or an empty string.
 * @param pci       The formatted PCI display value including the PSS/SSS breakdown, or an empty string.
 * @param narfcn    The NARFCN display value, or an empty string.
 * @param ssRsrp    The SS-RSRP value in dBm, or null when not reported.
 * @param ssRsrq    The SS-RSRQ value in dB, or null when not reported.
 * @param ssSinr    The SS-SINR value in dB, or null when not reported.
 * @param idle      True when the values are the last-seen cell rather than a live report; the card
 *                  dims and shows the Idle badge so stale data never renders as live.
 */
public record NrSecondaryCellViewState(String band, String frequency, String pci, String narfcn,
                                       Integer ssRsrp, Integer ssRsrq, Integer ssSinr,
                                       boolean idle)
{
    /**
     * @return A copy of this view state marked idle, for rendering the most recent live cell after
     * the NR leg has detached.
     */
    public NrSecondaryCellViewState asIdle()
    {
        return new NrSecondaryCellViewState(band, frequency, pci, narfcn, ssRsrp, ssRsrq, ssSinr,
                true);
    }
}
