package com.craxiom.networksurvey.services.controller;

/**
 * Where the {@link WifiController} gets Wi-Fi records from: the phone's own scanner
 * ({@link PhoneWifiSource}) or an NS Scout ({@code ScoutWifiSource}), never both. The controller
 * starts the one that is selected while something needs Wi-Fi and calls every method with its
 * lock held, so a source never runs twice and never runs next to the other one.
 */
public interface WifiScanSource
{
    /**
     * Starts producing records. Called only while this source is stopped.
     */
    void start();

    /**
     * Stops producing records and lets go of the radio. Called only while this source runs.
     */
    void stop();

    /**
     * Stops producing records for a while (battery or queue backpressure), keeping what the
     * running survey set up. Called only while this source runs.
     */
    void pause();

    /**
     * Undoes {@link #pause()}.
     */
    void resume();

    /**
     * A live view opened or closed, so the scan interval the screens ask for changed.
     */
    default void onLiveViewsChanged()
    {
    }

    /**
     * The survey's Wi-Fi scan interval changed while this source runs.
     */
    default void onScanRateChanged()
    {
    }

    /**
     * @return Whether a live view may make this source scan faster than the survey's interval. Only
     * the phone's scanner speeds up, and only where Android does not throttle it.
     */
    default boolean canScanFaster()
    {
        return false;
    }

    /**
     * Gives {@code sink} the records still held back (the Scout's open aggregation window) without
     * releasing them, on the calling thread. Used by a sink that is about to leave while others
     * stay, so it still gets what was heard so far.
     */
    default void peekPending(java.util.function.Consumer<java.util.List<com.craxiom.networksurvey.scout.wifi.ScoutHeard<com.craxiom.networksurvey.board.v1.ApObservation>>> sink)
    {
    }

    /**
     * Releases everything for good, when the service is destroyed. Called after {@link #stop()}.
     */
    default void destroy()
    {
    }
}
