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
     * Signal 3 is SNR
     */
    LTE(-125, 35, -23, 11, 2, 13),

    /**
     * Signal 1 is SS_RSRP
     * Signal 2 is SS_RSRQ
     * Signal 3 is SS_SINR
     */
    NR(-110, 40, -31, 28, -5, 29);

    private final int minSignalOne;
    private final int maxNormalizedSignalOne;
    private final int minSignalTwo;
    private final int maxNormalizedSignalTwo;
    private final int minSignalThree;
    private final int maxNormalizedSignalThree;

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
        this(minSignalOne, maxNormalizedSignalOne, minSignalTwo, maxNormalizedSignalTwo, -1, -1);
    }

    /**
     * @param minSignalOne             The minimum value that should be represented in the UI via the progress bar for signal 1.
     * @param maxNormalizedSignalOne   When added with minSignalOne, represents the "good" signal value in the progress bar.
     * @param minSignalTwo             The minimum value that should be represented in the UI via the progress bar for signal 2.
     * @param maxNormalizedSignalTwo   When added with minSignalTwo, represents the "good" signal value in the progress bar.
     * @param minSignalThree           The minimum value that should be represented in the UI via the progress bar for signal 3.
     * @param maxNormalizedSignalThree When added with minSignalThree, represents the "good" signal value in the progress bar.
     */
    CellularProtocol(int minSignalOne, int maxNormalizedSignalOne, int minSignalTwo, int maxNormalizedSignalTwo, int minSignalThree, int maxNormalizedSignalThree)
    {
        this.minSignalOne = minSignalOne;
        this.maxNormalizedSignalOne = maxNormalizedSignalOne;
        this.minSignalTwo = minSignalTwo;
        this.maxNormalizedSignalTwo = maxNormalizedSignalTwo;
        this.minSignalThree = minSignalThree;
        this.maxNormalizedSignalThree = maxNormalizedSignalThree;
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

    /**
     * @return The minimum value for signal three, or -1 if this protocol type does not support a third signal.
     */
    public int getMinSignalThree()
    {
        return minSignalThree;
    }

    /**
     * @return The maximum normalized value for signal three, or -1 if this protocol type does not support a third signal.
     */
    public int getMaxNormalizedSignalThree()
    {
        return maxNormalizedSignalThree;
    }
}
