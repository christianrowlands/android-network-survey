package com.craxiom.networksurvey.util

import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.util.SignalBuckets.UNKNOWN

/**
 * The single threshold table behind the survey points signal ramp. The map layer, the coarse
 * aggregation (which relies on strong being the highest value so MAX() is "best"), and the legend
 * all read from here so they can never disagree.
 *
 * Cellular thresholds are per technology because RSRP and RSSI are not comparable; Wi-Fi and
 * Bluetooth share the app-wide RSSI categories from [WifiSignalCategory]. The cellular quality
 * measurements (RSRQ and SNR/SINR) have their own tables, which only the Cellular view uses.
 *
 * Every table lists four cut points, strongest first, and a value must be above a cut point to
 * reach that bucket. The values follow the Android default carrier config and the common
 * industry tables, which use one scale for LTE and NR.
 */
object SignalBuckets {
    const val UNKNOWN = 0
    const val VERY_WEAK = 1
    const val WEAK = 2
    const val FAIR = 3
    const val GOOD = 4
    const val STRONG = 5

    /** LTE RSRP and NR SS-RSRP cut points (the Android LTE fallback table). */
    private val RSRP_THRESHOLDS = intArrayOf(-85, -95, -105, -115)

    /** GSM RSSI and UMTS RSCP cut points. */
    private val CELLULAR_RSSI_THRESHOLDS = intArrayOf(-75, -85, -95, -105)

    /** Wi-Fi and Bluetooth RSSI cut points, matching [WifiSignalCategory]. */
    private val RSSI_THRESHOLDS = intArrayOf(-60, -70, -80, -90)

    /** LTE RSRQ and NR SS-RSRQ cut points in dB: strong from -10, very weak at -20 and below. */
    private val RSRQ_THRESHOLDS = intArrayOf(-11, -14, -17, -20)

    /** LTE SNR and NR SS-SINR cut points in dB: strong from 20, very weak below 0. */
    private val SINR_THRESHOLDS = intArrayOf(19, 12, 4, -1)

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

    /** The RSRQ and SS-RSRQ cut points (a copy, strongest first). */
    fun rsrqThresholds(): IntArray = RSRQ_THRESHOLDS.copyOf()

    /** The SNR and SS-SINR cut points (a copy, strongest first). */
    fun sinrThresholds(): IntArray = SINR_THRESHOLDS.copyOf()

    /** Bucket for a cellular primary signal in dBm; [UNKNOWN] for CDMA, unknown technology, or a missing value. */
    fun cellular(protocol: Int, dBm: Int): Int =
        cellularThresholds(protocol)?.let { bucket(it, dBm) } ?: UNKNOWN

    /** Bucket for a Wi-Fi or Bluetooth RSSI in dBm; [UNKNOWN] for a missing value. */
    fun rssi(dBm: Int): Int = bucket(RSSI_THRESHOLDS, dBm)

    /** Bucket for an RSRQ or SS-RSRQ in dB. Zero and positive values are real readings. */
    fun rsrq(dB: Int): Int = rank(RSRQ_THRESHOLDS, dB)

    /** Bucket for an SNR or SS-SINR in dB. Zero and positive values are real readings. */
    fun sinr(dB: Int): Int = rank(SINR_THRESHOLDS, dB)

    private fun bucket(thresholds: IntArray, dBm: Int): Int {
        // Real signal values are negative; 0 is the protobuf default for an absent field.
        if (dBm >= 0) return UNKNOWN
        return rank(thresholds, dBm)
    }

    private fun rank(thresholds: IntArray, value: Int): Int = when {
        value > thresholds[0] -> STRONG
        value > thresholds[1] -> GOOD
        value > thresholds[2] -> FAIR
        value > thresholds[3] -> WEAK
        else -> VERY_WEAK
    }
}
