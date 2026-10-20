package com.craxiom.networksurvey.gpstest.util

import com.craxiom.networksurvey.gpstest.model.SatelliteStatus

/**
 * Gives each signal of a GnssStatus a key that identifies it from one scan to the next, so a row
 * and its trend line stay attached to the same signal while the list re-sorts.
 */
object SignalKeys {

    /** Separates a repeated signal's key from the number of its repeat. */
    private const val REPEAT_SEPARATOR = "#"

    /**
     * Sets [SatelliteStatus.signalKey] on each of [statuses]: the satellite, constellation (and SBAS
     * system) and carrier, from [SatelliteUtils.createGnssStatusKey]. Phones sometimes report the
     * same satellite and carrier twice (two signals on one frequency, or two unknown carriers), so a
     * repeat gets "#2", "#3" in the order the phone listed them, which keeps every key unique.
     */
    fun assign(statuses: List<SatelliteStatus>) {
        val seen = mutableMapOf<String, Int>()
        for (status in statuses) {
            val base = SatelliteUtils.createGnssStatusKey(status)
            val count = (seen[base] ?: 0) + 1
            seen[base] = count
            status.signalKey = if (count == 1) base else "$base$REPEAT_SEPARATOR$count"
        }
    }
}
