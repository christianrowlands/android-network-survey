package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.util.SignalBuckets

/**
 * The signal bucket (which colors a value) and the bar fraction (how full its bar is) for each
 * cellular measurement, on one scale so the two never disagree.
 *
 * The strongest-is-best measurements in dBm (RSSI, RSCP, RSRP, SS-RSRP) use the app's
 * [SignalBuckets] thresholds, so the Cellular view agrees with the map; their fraction puts each
 * threshold on a fifth of the bar, so a fair value always fills 40 to 60 percent. The quality
 * measurements (RSRQ, SNR, SS-RSRQ, SS-SINR) have no such table; they use the range each
 * [CellularProtocol] declares, split into five equal steps, and the bucket follows the fraction.
 */
object CellularSignalBuckets {

    private const val STRONG_FRACTION = 0.8f
    private const val GOOD_FRACTION = 0.6f
    private const val FAIR_FRACTION = 0.4f
    private const val WEAK_FRACTION = 0.2f

    /** The fraction of the bar each step between two thresholds takes. */
    private const val STEP_FRACTION = 0.2f

    /** @return The [SignalBuckets] value for [value] of [kind] on [protocol]. */
    fun bucket(protocol: CellularProtocol, kind: CellFieldKind, value: Int?): Int {
        if (value == null) return SignalBuckets.UNKNOWN
        thresholdProtocol(kind)?.let { return SignalBuckets.cellular(it, value) }
        return fraction(protocol, kind, value)?.let(::bucketForFraction) ?: SignalBuckets.UNKNOWN
    }

    /**
     * @return How full the bar for [value] of [kind] is, from 0 to 1, or null when there is no value
     * (a dBm value of 0 or more is the protobuf default, not a reading) or no scale for [kind].
     */
    fun fraction(protocol: CellularProtocol, kind: CellFieldKind, value: Int?): Float? {
        if (value == null) return null
        thresholdProtocol(kind)?.let { return thresholdFraction(it, value) }
        val (min, span) = range(protocol, kind) ?: return null
        if (span <= 0) return null
        return ((value - min).toFloat() / span).coerceIn(0f, 1f)
    }

    /** @return True when [kind] has a scale on [protocol], so a missing bar means a missing value. */
    fun hasRange(protocol: CellularProtocol, kind: CellFieldKind): Boolean =
        thresholdProtocol(kind) != null || (range(protocol, kind)?.second ?: 0) > 0

    /**
     * A dBm value placed on the threshold table: the weakest threshold sits at 0.2 and each stronger
     * one a fifth further, with one more step of headroom at each end. A value exactly on a
     * threshold belongs to the bucket below it (the thresholds compare with `>`), and its fraction
     * sits on that bucket's upper edge, so the bar and the color always match.
     */
    private fun thresholdFraction(thresholdProtocol: Int, dBm: Int): Float? {
        if (dBm >= 0) return null
        val thresholds = SignalBuckets.cellularThresholds(thresholdProtocol) ?: return null
        val ascending = thresholds.reversed()
        val step = thresholds[0] - thresholds[1]
        val anchors = listOf(ascending.first() - step) + ascending + (ascending.last() + step)
        if (dBm <= anchors.first()) return 0f
        if (dBm >= anchors.last()) return 1f
        val upper = anchors.indexOfFirst { dBm <= it }
        val low = anchors[upper - 1]
        val high = anchors[upper]
        return (upper - 1) * STEP_FRACTION + STEP_FRACTION * (dBm - low) / (high - low)
    }

    /** @return The [SurveyedPointEntity] protocol whose dBm thresholds [kind] uses, or null. */
    private fun thresholdProtocol(kind: CellFieldKind): Int? = when (kind) {
        CellFieldKind.RSSI -> SurveyedPointEntity.PROTOCOL_GSM
        CellFieldKind.RSCP -> SurveyedPointEntity.PROTOCOL_UMTS
        CellFieldKind.RSRP -> SurveyedPointEntity.PROTOCOL_LTE
        CellFieldKind.SS_RSRP -> SurveyedPointEntity.PROTOCOL_NR
        else -> null
    }

    /** @return The protocol's minimum and normalized span for a quality [kind], or null. */
    private fun range(protocol: CellularProtocol, kind: CellFieldKind): Pair<Int, Int>? = when (kind) {
        CellFieldKind.RSRQ, CellFieldKind.SS_RSRQ -> protocol.minSignalTwo to protocol.maxNormalizedSignalTwo
        CellFieldKind.SNR, CellFieldKind.SS_SINR -> protocol.minSignalThree to protocol.maxNormalizedSignalThree
        else -> null
    }

    private fun bucketForFraction(fraction: Float): Int = when {
        fraction >= STRONG_FRACTION -> SignalBuckets.STRONG
        fraction >= GOOD_FRACTION -> SignalBuckets.GOOD
        fraction >= FAIR_FRACTION -> SignalBuckets.FAIR
        fraction >= WEAK_FRACTION -> SignalBuckets.WEAK
        else -> SignalBuckets.VERY_WEAK
    }
}
