package com.craxiom.networksurvey.fragments.model;

import android.telephony.CellInfo;

/**
 * Holds the information for a single LTE neighbor record that will be displayed in the Cellular UI.
 *
 * @since 1.6.0
 */
public class LteNeighbor
{
    /**
     * AKA {@link CellInfo#UNAVAILABLE}, but I am not using that because it was added in API level 29.
     */
    public static final int UNSET_VALUE = Integer.MAX_VALUE;

    public final int earfcn;
    public final int pci;
    public final int rsrp;
    public final int rsrq;
    public final int ta;

    public LteNeighbor(int earfcn, int pci, int rsrp, int rsrq, int ta)
    {
        this.earfcn = earfcn;
        this.pci = pci;
        this.rsrp = rsrp;
        this.rsrq = rsrq;
        this.ta = ta;
    }
}
