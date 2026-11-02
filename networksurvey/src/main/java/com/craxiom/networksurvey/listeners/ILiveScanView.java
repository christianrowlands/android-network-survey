package com.craxiom.networksurvey.listeners;

/**
 * Marks a survey record listener as a screen that shows its radio live (the Wi-Fi list, details
 * and spectrum, the Bluetooth list and details, the Cellular view, and the Map tab).
 * <p>
 * While a live view is registered, its radio scans at the shorter of the survey's scan interval
 * and {@link #getLiveScanIntervalMs()}, where Android allows it, so what the user sees stays
 * current. Those faster scans are real measurements and reach every other listener too, so a
 * running survey saves them like any other scan. Bluetooth keeps its survey interval; its live
 * views also receive signal updates the survey's duplicate filter leaves out, and only they do.
 * <p>
 * Screens register only while they are in front of the user (see
 * {@code ServiceConnectionHandler}), so leaving the screen, backgrounding the app, or turning the
 * screen off ends the faster scanning.
 */
public interface ILiveScanView
{
    /**
     * @return The scan interval, in milliseconds, this screen asks for while it is open. The
     * default asks for no faster scanning.
     */
    default int getLiveScanIntervalMs()
    {
        return Integer.MAX_VALUE;
    }
}
