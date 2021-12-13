package com.craxiom.networksurvey.fragments.model;

import android.telephony.CellInfo;

import lombok.Builder;

/**
 * Holds the information for a single LTE neighbor record that will be displayed in the Cellular UI.
 *
 * @since 1.6.0
 */
@Builder
public class LteNeighbor implements Comparable<LteNeighbor>
{
    /**
     * AKA {@link CellInfo#UNAVAILABLE}, but I am not using that because it was added in API level 29.
     */
    public static final int UNSET_VALUE = Integer.MAX_VALUE;

    public int earfcn = UNSET_VALUE;
    public int pci = UNSET_VALUE;
    public int rsrp = UNSET_VALUE;
    public int rsrq = UNSET_VALUE;
    public int ta = UNSET_VALUE;

    @Override
    public int compareTo(LteNeighbor neighbor)
    {
        // flipping the MAX to MIN so that unset shows up as the lowest value
        int thisComparisonRsrp = rsrp == UNSET_VALUE ? Integer.MIN_VALUE : rsrp;
        int otherComparisonRsrp = neighbor.rsrp == UNSET_VALUE ? Integer.MIN_VALUE : neighbor.rsrp;

        return Integer.compare(thisComparisonRsrp, otherComparisonRsrp);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LteNeighbor neighbor = (LteNeighbor) o;

        if (earfcn != neighbor.earfcn) return false;
        if (pci != neighbor.pci) return false;
        if (rsrp != neighbor.rsrp) return false;
        if (rsrq != neighbor.rsrq) return false;
        return ta == neighbor.ta;
    }

    @Override
    public int hashCode()
    {
        int result = earfcn;
        result = 31 * result + pci;
        result = 31 * result + rsrp;
        result = 31 * result + rsrq;
        result = 31 * result + ta;
        return result;
    }
}
