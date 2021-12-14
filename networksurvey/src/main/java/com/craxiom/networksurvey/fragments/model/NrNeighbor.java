package com.craxiom.networksurvey.fragments.model;

import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.UNSET_VALUE;

import lombok.Builder;

/**
 * Holds the information for a single NR neighbor record that will be displayed in the Cellular UI.
 *
 * @since 1.6.0
 */
@Builder
public class NrNeighbor implements Comparable<NrNeighbor>
{
    @Builder.Default
    public int narfcn = UNSET_VALUE;
    @Builder.Default
    public int pci = UNSET_VALUE;
    @Builder.Default
    public int ssRsrp = UNSET_VALUE;
    @Builder.Default
    public int ssRsrq = UNSET_VALUE;

    @Override
    public int compareTo(NrNeighbor neighbor)
    {
        // flipping the MAX to MIN so that unset shows up as the lowest value
        int thisComparisonRsrp = ssRsrp == UNSET_VALUE ? Integer.MIN_VALUE : ssRsrp;
        int otherComparisonRsrp = neighbor.ssRsrp == UNSET_VALUE ? Integer.MIN_VALUE : neighbor.ssRsrp;

        // Invert the sorting so the strongest show up at the top
        return -1 * Integer.compare(thisComparisonRsrp, otherComparisonRsrp);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NrNeighbor neighbor = (NrNeighbor) o;

        if (narfcn != neighbor.narfcn) return false;
        if (pci != neighbor.pci) return false;
        if (ssRsrp != neighbor.ssRsrp) return false;
        return ssRsrq == neighbor.ssRsrq;
    }

    @Override
    public int hashCode()
    {
        int result = narfcn;
        result = 31 * result + pci;
        result = 31 * result + ssRsrp;
        result = 31 * result + ssRsrq;
        return result;
    }
}
