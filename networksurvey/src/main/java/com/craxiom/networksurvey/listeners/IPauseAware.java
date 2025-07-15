package com.craxiom.networksurvey.listeners;

/**
 * Interface for components that need to be notified when operations are paused or resumed
 * for battery management.
 */
public interface IPauseAware
{
    /**
     * Called when operations should be paused to conserve battery.
     * Implementations should reduce or stop background processing while maintaining
     * connection state.
     */
    void onPause();

    /**
     * Called when operations should resume after being paused.
     * Implementations should restore normal processing.
     */
    void onResume();
}