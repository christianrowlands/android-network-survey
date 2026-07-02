package com.craxiom.networksurvey.listeners;

import com.craxiom.messaging.WatchlistEntryUpdate;
import com.craxiom.messaging.WatchlistMatch;

/**
 * Listener interface for those interested in being notified about Wi-Fi Watchlist events so they can
 * be published (for example, streamed over MQTT).
 * <p>
 * There are two kinds of event: a {@link WatchlistMatch}, fired when a watched network is
 * observed (de-duplicated on a presence transition with a per-entry cooldown), and a
 * {@link WatchlistEntryUpdate}, fired on (re)connect and whenever the watchlist changes, always
 * carrying a full snapshot of the list.
 */
public interface IWatchlistListener
{
    /**
     * Called when a watched network (SSID and/or BSSID) is observed.
     *
     * @param watchlistMatch the match describing the sighting.
     */
    void onWatchlistMatch(WatchlistMatch watchlistMatch);

    /**
     * Called with a full snapshot of the watchlist when it changes and once on (re)connect.
     *
     * @param watchlistEntryUpdate the update carrying the full snapshot.
     */
    void onWatchlistEntryUpdate(WatchlistEntryUpdate watchlistEntryUpdate);
}
