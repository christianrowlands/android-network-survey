package com.craxiom.networksurvey.fragments.model;

import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.UNSET_VALUE;

/**
 * Holds the information for a single NR neighbor record that will be displayed in the Cellular UI.
 *
 * @since 1.6.0
 */
public class NrNeighbor implements Comparable<NrNeighbor>
{
    public final int narfcn;
    public final int pci;
    public final int ssRsrp;
    public final int ssRsrq;

    public static class Builder
    {
        private int narfcn = UNSET_VALUE;
        private int pci = UNSET_VALUE;
        private int ssRsrp = UNSET_VALUE;
        private int ssRsrq = UNSET_VALUE;

        public Builder narfcn(int narfcn)
        {
            this.narfcn = narfcn;
            return this;
        }

        public Builder pci(int pci)
        {
            this.pci = pci;
            return this;
        }

        public Builder ssRsrp(int ssRsrp)
        {
            this.ssRsrp = ssRsrp;
            return this;
        }

        public Builder ssRsrq(int ssRsrq)
        {
            this.ssRsrq = ssRsrq;
            return this;
        }

        public NrNeighbor build()
        {
            return new NrNeighbor(this);
        }
    }

    private NrNeighbor(Builder builder)
    {
        this.narfcn = builder.narfcn;
        this.pci = builder.pci;
        this.ssRsrp = builder.ssRsrp;
        this.ssRsrq = builder.ssRsrq;
    }

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
