package com.craxiom.networksurvey.fragments.model;

import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.UNSET_VALUE;

/**
 * Holds the information for a single GSM neighbor record that will be displayed in the Cellular UI.
 *
 * @since 1.6.0
 */
public class GsmNeighbor implements Comparable<GsmNeighbor>
{
    public final int arfcn;
    public final int bsic;
    public final int rssi;

    public static class Builder
    {
        private int arfcn = UNSET_VALUE;
        private int bsic = UNSET_VALUE;
        private int rssi = UNSET_VALUE;

        public Builder arfcn(int arfcn)
        {
            this.arfcn = arfcn;
            return this;
        }

        public Builder bsic(int bsic)
        {
            this.bsic = bsic;
            return this;
        }

        public Builder rssi(int rssi)
        {
            this.rssi = rssi;
            return this;
        }

        public GsmNeighbor build()
        {
            return new GsmNeighbor(this);
        }
    }

    private GsmNeighbor(Builder builder)
    {
        this.arfcn = builder.arfcn;
        this.bsic = builder.bsic;
        this.rssi = builder.rssi;
    }

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
