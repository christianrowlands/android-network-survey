package com.craxiom.networksurvey.model;

/**
 * Defines the possible Cellular protocols that we handle in this app.
 *
 * @since 1.6.0
 */
public enum CellularProtocol
{
    NONE(-1, -1),

    /**
     * Signal 1 is RSSI
     */
    GSM(-107, 18),
    CDMA(-1, -1),

    /**
     * Signal 1 is RSSI
     * Signal 2 is RSCP
     */
    UMTS(-107, 30, -115, 30),

    /**
     * Signal 1 is RSRP
     * Signal 2 is RSRQ
     */
    LTE(-125, 35, -23, 11),

    /**
     * Signal 1 is SS_RSRP
     * Signal 2 is SS_RSRQ
     */
    NR(-110, 40, -31, 28);

    private final int minSignalOne;
    private final int maxNormalizedSignalOne;
    private final int minSignalTwo;
    private final int maxNormalizedSignalTwo;

    /**
     * Condensed version for protocols with one signal value.
     */
    CellularProtocol(int minSignalOne, int maxNormalizedSignalOne)
    {
        this(minSignalOne, maxNormalizedSignalOne, -1, -1);
    }

    /**
     * @param minSignalOne           The minimum value that should be represented in the UI via the progress bar for signal 1.
     * @param maxNormalizedSignalOne When added with minSignalOne, represents the "good" signal value in the progress bar.
     * @param minSignalTwo           The minimum value that should be represented in the UI via the progress bar for signal 2.
     * @param maxNormalizedSignalTwo When added with minSignalTwo, represents the "good" signal value in the progress bar.
     */
    CellularProtocol(int minSignalOne, int maxNormalizedSignalOne, int minSignalTwo, int maxNormalizedSignalTwo)
    {
        this.minSignalOne = minSignalOne;
        this.maxNormalizedSignalOne = maxNormalizedSignalOne;
        this.minSignalTwo = minSignalTwo;
        this.maxNormalizedSignalTwo = maxNormalizedSignalTwo;
    }

    public int getMinSignalOne()
    {
        return minSignalOne;
    }

    public int getMaxNormalizedSignalOne()
    {
        return maxNormalizedSignalOne;
    }

    /**
     * @return The minimum value for signal 2, or -1 if this protocol type does not support a second signal.
     */
    public int getMinSignalTwo()
    {
        return minSignalTwo;
    }

    /**
     * @return The maximum normalized value for signal 2, or -1 if this protocol type does not support a second signal.
     */
    public int getMaxNormalizedSignalTwo()
    {
        return maxNormalizedSignalTwo;
    }
}
