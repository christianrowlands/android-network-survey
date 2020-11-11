package com.craxiom.networksurvey.listeners;

/**
 * Implement this class to register as a listener for qualifying GNSS failure events.
 *
 * @since 0.4.0
 */
public interface IGnssFailureListener
{
    /**
     * Called when a qualifying GNSS failure has occurred, such as a timeout on receiving GNSS
     * measurements.
     */
    void onGnssFailure();
}
