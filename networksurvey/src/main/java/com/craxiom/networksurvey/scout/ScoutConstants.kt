package com.craxiom.networksurvey.scout

/** Preference keys and fixed values shared by the NS Scout classes. */
object ScoutConstants {
    /** The paired Scouts, a JSON list of `PairedScout` keyed by Scout ID. */
    const val PREFERENCE_PAIRED_SCOUTS = "scout_paired_scouts"

    /** Where Wi-Fi comes from, a `WifiSource.preferenceValue`. */
    const val PREFERENCE_WIFI_SOURCE = "scout_wifi_source"

    /** The name of the dedicated thread that owns the Scout link, decoding and state. */
    const val THREAD_NAME = "NS-Scout"

    /** A Scout not heard for this long leaves the pairing sheet's list. */
    const val NEARBY_EXPIRY_MS = 10_000L

    /** How often the pairing sheet's list is pruned and republished at most. */
    const val NEARBY_PUBLISH_INTERVAL_MS = 1_000L
}
