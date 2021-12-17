package com.craxiom.networksurvey.fragments.model;

import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.UNSET_VALUE;

import lombok.Builder;

/**
 * Holds the information for a single UMTS neighbor record that will be displayed in the Cellular UI.
 *
 * @since 1.6.0
 */
@Builder
public class UmtsNeighbor implements Comparable<UmtsNeighbor>
{
    @Builder.Default
    public int uarfcn = UNSET_VALUE;
    @Builder.Default
    public int psc = UNSET_VALUE;
    @Builder.Default
    public int rscp = UNSET_VALUE;

    @Override
    public int compareTo(UmtsNeighbor neighbor)
    {
        // flipping the MAX to MIN so that unset shows up as the lowest value
        int thisComparisonRscp = rscp == UNSET_VALUE ? Integer.MIN_VALUE : rscp;
        int otherComparisonRscp = neighbor.rscp == UNSET_VALUE ? Integer.MIN_VALUE : neighbor.rscp;

        // Invert the sorting so the strongest show up at the top
        return -1 * Integer.compare(thisComparisonRscp, otherComparisonRscp);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UmtsNeighbor neighbor = (UmtsNeighbor) o;

        if (uarfcn != neighbor.uarfcn) return false;
        if (psc != neighbor.psc) return false;
        return rscp == neighbor.rscp;
    }

    @Override
    public int hashCode()
    {
        int result = uarfcn;
        result = 31 * result + psc;
        result = 31 * result + rscp;
        return result;
    }
}
