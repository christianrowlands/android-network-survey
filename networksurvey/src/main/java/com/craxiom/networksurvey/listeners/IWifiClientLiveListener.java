package com.craxiom.networksurvey.listeners;

import com.craxiom.networksurvey.scout.clients.ScoutClientSnapshot;

/**
 * Listener interface for the live screens that show Wi-Fi clients (the Clients list, Client details
 * and an access point's connected clients). A registered listener makes the NS Scout capture
 * clients while it is open, just as a live Wi-Fi screen starts Wi-Fi scanning; it is never sent
 * saved records.
 */
public interface IWifiClientLiveListener
{
    /**
     * Called every few seconds with every client the Scout heard recently. Called on a background
     * thread.
     *
     * @param snapshot The clients, possibly none.
     */
    void onWifiClientSnapshot(ScoutClientSnapshot snapshot);
}
