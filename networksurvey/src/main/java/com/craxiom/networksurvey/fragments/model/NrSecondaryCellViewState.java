package com.craxiom.networksurvey.fragments.model;

import com.craxiom.networksurvey.data.band.BandTapTarget;

/**
 * View state for the NR Secondary Cell details card on the cellular details screen, shown when the
 * device reports an NR cell as SECONDARY_SERVING: on 5G NSA (EN-DC) that is the NR cell carrying
 * the 5G data alongside the LTE anchor, and on 5G SA with NR carrier aggregation it is an NR
 * SCell. On NSA the NR leg detaches within seconds of data going idle, so instead of vanishing the
 * card can also render the last-seen cell dimmed with an Idle badge (see {@code idle}). Kept as a
 * record over immutable display values so that an unchanged card compares equal. A null view state
 * hides the card. Built by
 * {@link com.craxiom.networksurvey.ui.cellular.details.model.NrSecondaryCellResolver}.
 * <p>
 * The "Last seen ..." age deliberately lives outside this record, so that a card held idle stays
 * equal from scan to scan.
 *
 * @param band      The formatted NR band display value (e.g. "n77 (TD 3700)"), or an empty string.
 * @param bandTapTarget The band numbers behind {@code band}, for opening the band reference.
 * @param frequency The frequency in MHz without its unit (e.g. "3709.920"), or an empty string.
 * @param pci       The formatted PCI display value including the PSS/SSS breakdown, or an empty string.
 * @param narfcn    The NARFCN display value, or an empty string.
 * @param ssRsrp    The SS-RSRP value in dBm, or null when not reported.
 * @param ssRsrq    The SS-RSRQ value in dB, or null when not reported.
 * @param ssSinr    The SS-SINR value in dB, or null when not reported.
 * @param idle      True when the values are the last-seen cell rather than a live report; the card
 *                  dims and shows the Idle badge so stale data never renders as live.
 */
public record NrSecondaryCellViewState(String band, BandTapTarget bandTapTarget,
                                       String frequency, String pci, String narfcn,
                                       Integer ssRsrp, Integer ssRsrq, Integer ssSinr,
                                       boolean idle)
{
    /**
     * @return A copy of this view state marked idle, for rendering the most recent live cell after
     * the NR leg has detached.
     */
    public NrSecondaryCellViewState asIdle()
    {
        return new NrSecondaryCellViewState(band, bandTapTarget, frequency, pci, narfcn, ssRsrp,
                ssRsrq, ssSinr, true);
    }
}
