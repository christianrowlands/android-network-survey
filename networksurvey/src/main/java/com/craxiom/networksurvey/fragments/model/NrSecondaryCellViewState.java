package com.craxiom.networksurvey.fragments.model;

/**
 * View state for the NR Secondary Cell details card on the cellular details screen, shown when the
 * device reports an NR cell as SECONDARY_SERVING: on 5G NSA (EN-DC) that is the NR cell carrying
 * the 5G data alongside the LTE anchor, and on 5G SA with NR carrier aggregation it is an NR
 * SCell. Kept as a record over immutable display values so the ViewModel's distinct-until-changed
 * setter works via value equality. A null view state hides the card.
 *
 * @param band      The formatted NR band display value (e.g. "n77 (TD 3700)"), or an empty string.
 * @param frequency The formatted frequency display value (e.g. "3709.920 MHz"), or an empty string.
 * @param pci       The formatted PCI display value including the PSS/SSS breakdown, or an empty string.
 * @param narfcn    The NARFCN display value, or an empty string.
 * @param ssRsrp    The SS-RSRP value in dBm, or null when not reported.
 * @param ssRsrq    The SS-RSRQ value in dB, or null when not reported.
 * @param ssSinr    The SS-SINR value in dB, or null when not reported.
 */
public record NrSecondaryCellViewState(String band, String frequency, String pci, String narfcn,
                                       Integer ssRsrp, Integer ssRsrq, Integer ssSinr)
{
}
