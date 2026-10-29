package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.util.SignalBuckets

/**
 * The signal bucket (which colors a value) and the bar fraction (how full its bar is) for each
 * cellular measurement, on one scale so the two never disagree.
 *
 * Every measurement reads its cut points from [SignalBuckets]: the strength measurements in dBm
 * (RSSI, RSCP, RSRP, SS-RSRP) share the map's tables, so the Cellular view agrees with the map, and
 * the quality measurements (RSRQ, SNR, SS-RSRQ, SS-SINR) have their own. The fraction puts each cut
 * point on a fifth of the bar, so a fair value always fills 40 to 60 percent.
 */
object CellularSignalBuckets {

    /** The fraction of the bar each step between two thresholds takes. */
    private const val STEP_FRACTION = 0.2f

    /** @return The [SignalBuckets] value for [value] of [kind]. */
    fun bucket(kind: CellFieldKind, value: Int?): Int {
        if (value == null) return SignalBuckets.UNKNOWN
        thresholdProtocol(kind)?.let { return SignalBuckets.cellular(it, value) }
        return when (kind) {
            CellFieldKind.RSRQ, CellFieldKind.SS_RSRQ -> SignalBuckets.rsrq(value)
            CellFieldKind.SNR, CellFieldKind.SS_SINR -> SignalBuckets.sinr(value)
            else -> SignalBuckets.UNKNOWN
        }
    }

    /**
     * @return How full the bar for [value] of [kind] is, from 0 to 1, or null when there is no value
     * (a dBm value of 0 or more is the protobuf default, not a reading) or no scale for [kind].
     */
    fun fraction(kind: CellFieldKind, value: Int?): Float? {
        if (value == null) return null
        thresholdProtocol(kind)?.let { protocol ->
            if (value >= 0) return null
            return SignalBuckets.cellularThresholds(protocol)?.let { thresholdFraction(it, value) }
        }
        return qualityThresholds(kind)?.let { thresholdFraction(it, value) }
    }

    /** @return True when [kind] has a scale, so a missing bar means a missing value. */
    fun hasRange(kind: CellFieldKind): Boolean = thresholdProtocol(kind) != null || qualityThresholds(kind) != null

    /**
     * A value placed on its threshold table (strongest first): the weakest threshold sits at 0.2 and
     * each stronger one a fifth further, with one more step of headroom at each end (as wide as the
     * step next to it). A value exactly on a threshold belongs to the bucket below it (the thresholds
     * compare with `>`), and its fraction sits on that bucket's upper edge, so the bar and the color
     * always match.
     */
    private fun thresholdFraction(thresholds: IntArray, value: Int): Float {
        val ascending = thresholds.reversed()
        val lowHeadroom = ascending[1] - ascending[0]
        val highHeadroom = ascending[ascending.lastIndex] - ascending[ascending.lastIndex - 1]
        val anchors = listOf(ascending.first() - lowHeadroom) + ascending + (ascending.last() + highHeadroom)
        if (value <= anchors.first()) return 0f
        if (value >= anchors.last()) return 1f
        val upper = anchors.indexOfFirst { value <= it }
        val low = anchors[upper - 1]
        val high = anchors[upper]
        return (upper - 1) * STEP_FRACTION + STEP_FRACTION * (value - low) / (high - low)
    }

    /** @return The [SurveyedPointEntity] protocol whose dBm thresholds [kind] uses, or null. */
    private fun thresholdProtocol(kind: CellFieldKind): Int? = when (kind) {
        CellFieldKind.RSSI -> SurveyedPointEntity.PROTOCOL_GSM
        CellFieldKind.RSCP -> SurveyedPointEntity.PROTOCOL_UMTS
        CellFieldKind.RSRP -> SurveyedPointEntity.PROTOCOL_LTE
        CellFieldKind.SS_RSRP -> SurveyedPointEntity.PROTOCOL_NR
        else -> null
    }

    /** @return The dB thresholds of a quality [kind], or null. */
    private fun qualityThresholds(kind: CellFieldKind): IntArray? = when (kind) {
        CellFieldKind.RSRQ, CellFieldKind.SS_RSRQ -> SignalBuckets.rsrqThresholds()
        CellFieldKind.SNR, CellFieldKind.SS_SINR -> SignalBuckets.sinrThresholds()
        else -> null
    }
}
