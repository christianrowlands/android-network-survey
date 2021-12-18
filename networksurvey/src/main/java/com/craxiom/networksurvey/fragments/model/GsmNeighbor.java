package com.craxiom.networksurvey.fragments.model;

import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.UNSET_VALUE;

import lombok.Builder;

/**
 * Holds the information for a single GSM neighbor record that will be displayed in the Cellular UI.
 *
 * @since 1.6.0
 */
@Builder
public class GsmNeighbor implements Comparable<GsmNeighbor>
{
    @Builder.Default
    public int arfcn = UNSET_VALUE;
    @Builder.Default
    public int bsic = UNSET_VALUE;
    @Builder.Default
    public int rssi = UNSET_VALUE;

    @Override
    public int compareTo(GsmNeighbor neighbor)
    {
        // flipping the MAX to MIN so that unset shows up as the lowest value
        int thisComparisonRssi = rssi == UNSET_VALUE ? Integer.MIN_VALUE : rssi;
        int otherComparisonRssi = neighbor.rssi == UNSET_VALUE ? Integer.MIN_VALUE : neighbor.rssi;

        // Invert the sorting so the strongest show up at the top
        return -1 * Integer.compare(thisComparisonRssi, otherComparisonRssi);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GsmNeighbor neighbor = (GsmNeighbor) o;

        if (arfcn != neighbor.arfcn) return false;
        if (bsic != neighbor.bsic) return false;
        return rssi == neighbor.rssi;
    }

    @Override
    public int hashCode()
    {
        int result = arfcn;
        result = 31 * result + bsic;
        result = 31 * result + rssi;
        return result;
    }
}
