package com.craxiom.networksurvey.constants;

/**
 * The constants associated with the Wi-Fi Watchlist messages in the Network Survey Messaging API. The
 * message type strings must exactly match the outer protobuf message class names.
 */
public class WatchlistMessageConstants
{
    private WatchlistMessageConstants()
    {
    }

    public static final String WATCHLIST_MATCH_MESSAGE_TYPE = "WatchlistMatch";
    public static final String WATCHLIST_ENTRY_UPDATE_MESSAGE_TYPE = "WatchlistEntryUpdate";
}
