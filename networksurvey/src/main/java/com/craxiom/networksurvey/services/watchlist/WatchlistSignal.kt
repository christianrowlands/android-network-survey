package com.craxiom.networksurvey.services.watchlist

import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity

/**
 * Converts a watchlist sighting's signal between the stored history column and the value the alert,
 * the screens, and the sort orders use. The column is a non-null INTEGER, so a sighting of an access
 * point that reported no signal strength is stored as [WatchlistHitEntity.RSSI_UNKNOWN] and read back
 * as null (an unknown signal), never as 0 dBm.
 */
object WatchlistSignal {

    /**
     * @param signalStrength the record's signal strength in dBm, or null when the scan had none.
     * @return the whole dBm to store in [WatchlistHitEntity.rssi], or [WatchlistHitEntity.RSSI_UNKNOWN].
     */
    fun toStoredRssi(signalStrength: Float?): Int = dbm(signalStrength) ?: WatchlistHitEntity.RSSI_UNKNOWN

    /**
     * @param signalStrength the record's signal strength in dBm, or null when the scan had none.
     * @return the signal truncated to a whole dBm, or null when it is missing or not a real Wi-Fi
     *   reading (one that is not negative).
     */
    fun dbm(signalStrength: Float?): Int? = signalStrength?.toInt()?.let(::fromStoredRssi)

    /**
     * @param rssi a value from [WatchlistHitEntity.rssi].
     * @return the signal in dBm, or null for [WatchlistHitEntity.RSSI_UNKNOWN] or any other value
     *   that is not negative.
     */
    fun fromStoredRssi(rssi: Int): Int? = rssi.takeIf { it < 0 }
}

/** This sighting's signal in dBm, or null when the access point reported none. */
val WatchlistHitEntity.signalDbm: Int?
    get() = WatchlistSignal.fromStoredRssi(rssi)
