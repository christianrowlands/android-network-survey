package com.craxiom.networksurvey.util

import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.util.SignalBuckets.UNKNOWN

/**
 * The single threshold table behind the survey points signal ramp. The map layer, the coarse
 * aggregation (which relies on strong being the highest value so MAX() is "best"), and the legend
 * all read from here so they can never disagree.
 *
 * Cellular thresholds are per technology because RSRP and RSSI are not comparable; Wi-Fi and
 * Bluetooth share the app-wide RSSI categories from [WifiSignalCategory].
 */
object SignalBuckets {
    const val UNKNOWN = 0
    const val VERY_WEAK = 1
    const val WEAK = 2
    const val FAIR = 3
    const val GOOD = 4
    const val STRONG = 5

    /** LTE RSRP and NR SS-RSRP cut points, strongest first; a value above the first is strong. */
    private val RSRP_THRESHOLDS = intArrayOf(-85, -95, -105, -115)

    /** GSM RSSI and UMTS RSCP cut points. */
    private val CELLULAR_RSSI_THRESHOLDS = intArrayOf(-75, -85, -95, -105)

    /** Wi-Fi and Bluetooth RSSI cut points, matching [WifiSignalCategory]. */
    private val RSSI_THRESHOLDS = intArrayOf(-60, -70, -80, -90)

    /**
     * The four cut points for a cellular technology (a copy, strongest first), or null when the
     * technology has no ramp (CDMA, unknown).
     */
    fun cellularThresholds(protocol: Int): IntArray? = when (protocol) {
        SurveyedPointEntity.PROTOCOL_LTE, SurveyedPointEntity.PROTOCOL_NR -> RSRP_THRESHOLDS.copyOf()
        SurveyedPointEntity.PROTOCOL_GSM, SurveyedPointEntity.PROTOCOL_UMTS -> CELLULAR_RSSI_THRESHOLDS.copyOf()
        else -> null
    }

    /** The Wi-Fi and Bluetooth cut points (a copy, strongest first). */
    fun rssiThresholds(): IntArray = RSSI_THRESHOLDS.copyOf()

    /** Bucket for a cellular primary signal in dBm; [UNKNOWN] for CDMA, unknown technology, or a missing value. */
    fun cellular(protocol: Int, dBm: Int): Int =
        cellularThresholds(protocol)?.let { bucket(it, dBm) } ?: UNKNOWN

    /** Bucket for a Wi-Fi or Bluetooth RSSI in dBm; [UNKNOWN] for a missing value. */
    fun rssi(dBm: Int): Int = bucket(RSSI_THRESHOLDS, dBm)

    private fun bucket(thresholds: IntArray, dBm: Int): Int {
        // Real signal values are negative; 0 is the protobuf default for an absent field.
        if (dBm >= 0) return UNKNOWN
        return when {
            dBm > thresholds[0] -> STRONG
            dBm > thresholds[1] -> GOOD
            dBm > thresholds[2] -> FAIR
            dBm > thresholds[3] -> WEAK
            else -> VERY_WEAK
        }
    }
}
