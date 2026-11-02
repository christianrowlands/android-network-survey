package com.craxiom.networksurvey.services.controller;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Tells a fresh cell list from a repeat of the previous one, per SIM.
 * <p>
 * Android answers a cell info request that comes sooner than the modem's refresh with the cached
 * list, carrying the same measurement timestamps as the last answer. The survey stamps each record
 * with the time it was processed, so a repeated list would otherwise be saved as a new measurement.
 * The cellular controller polls faster only for a live view, and drops the repeats while it does.
 * <p>
 * Thread safe: the cell info callbacks arrive on the survey executor.
 */
public class CellInfoFreshness
{
    // The sorted measurement timestamps of the last list seen for each subscription.
    private final Map<Integer, long[]> lastTimestamps = new HashMap<>();

    /**
     * Records the list and says whether it repeats the previous list for the same SIM.
     *
     * @param subscriptionId The subscription the list is for.
     * @param timestamps     The measurement timestamps of the list's cells
     *                       ({@code CellInfo.getTimestampMillis()}), in any order.
     * @return True when the list is not empty and has exactly the timestamps of the previous list for
     * this subscription.
     */
    public synchronized boolean isRepeat(int subscriptionId, long[] timestamps)
    {
        final long[] sorted = timestamps.clone();
        Arrays.sort(sorted);
        final long[] previous = lastTimestamps.put(subscriptionId, sorted);
        return sorted.length > 0 && Arrays.equals(previous, sorted);
    }

    /**
     * @return The newest timestamp in the previous list for the subscription, or null when there is
     * none. Used to log how often the modem really refreshes.
     */
    public synchronized Long newestPrevious(int subscriptionId)
    {
        final long[] previous = lastTimestamps.get(subscriptionId);
        if (previous == null || previous.length == 0) return null;
        return previous[previous.length - 1];
    }

    /**
     * Forgets every list, so the next list for each SIM counts as fresh. Called when a live view
     * opens, so the screen's first list is never held back.
     */
    public synchronized void reset()
    {
        lastTimestamps.clear();
    }
}
