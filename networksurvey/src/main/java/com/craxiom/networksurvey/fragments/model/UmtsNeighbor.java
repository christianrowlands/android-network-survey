package com.craxiom.networksurvey.fragments.model;

import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.UNSET_VALUE;

/**
 * Holds the information for a single UMTS neighbor record that will be displayed in the Cellular UI.
 *
 * @since 1.6.0
 */
public class UmtsNeighbor implements Comparable<UmtsNeighbor>
{
    public final int uarfcn;
    public final int psc;
    public final int rscp;

    public static class Builder {
        private int uarfcn = UNSET_VALUE;
        private int psc = UNSET_VALUE;
        private int rscp = UNSET_VALUE;

        public Builder uarfcn(int uarfcn) {
            this.uarfcn = uarfcn;
            return this;
        }

        public Builder psc(int psc) {
            this.psc = psc;
            return this;
        }

        public Builder rscp(int rscp) {
            this.rscp = rscp;
            return this;
        }

        public UmtsNeighbor build() {
            return new UmtsNeighbor(this);
        }
    }

    private UmtsNeighbor(Builder builder) {
        this.uarfcn = builder.uarfcn;
        this.psc = builder.psc;
        this.rscp = builder.rscp;
    }

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
