package com.craxiom.networksurvey.gpstest.ui.sky

import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.model.SbasType

/**
 * One satellite on the sky plot. A satellite heard on two frequencies arrives as two signals; it is
 * drawn once, with its strongest signal.
 *
 * @property cn0DbHz The strongest C/N0 of its signals, 0 when none is heard.
 * @property usedInFix True when any of its signals is used in the fix.
 */
data class SkySatellite(
    val svid: Int,
    val gnssType: GnssType,
    val sbasType: SbasType,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val cn0DbHz: Float,
    val usedInFix: Boolean,
) {
    val shape: SatelliteShape get() = SkyProjection.shape(gnssType)
    val inView: Boolean get() = SkyProjection.inView(cn0DbHz)
}

/**
 * The numbers under the plot and on the averages card.
 *
 * @property satellites Satellites with a position on the plot.
 * @property used Those used in the fix.
 * @property inViewAverage The mean C/N0 of the signals heard, or null when none is.
 * @property usedAverage The mean C/N0 of the signals used in the fix, or null when none is.
 */
data class SkySummary(val satellites: Int, val used: Int, val inViewAverage: Float?, val usedAverage: Float?)

/** What the sky page draws. */
data class SkyState(val satellites: List<SkySatellite>, val summary: SkySummary)

/** Builds the sky page's state from the (already filtered) GnssStatus signals. */
object SkyModel {

    fun from(statuses: List<SatelliteStatus>): SkyState {
        val satellites = statuses
            .groupBy { Triple(it.gnssType, it.svid, it.sbasType) }
            .values
            .mapNotNull { signals -> merge(signals) }
        val heard = statuses.filter { SkyProjection.inView(it.cn0DbHz) }
        val used = statuses.filter { it.usedInFix }
        return SkyState(
            satellites = satellites,
            summary = SkySummary(
                satellites = satellites.size,
                used = satellites.count { it.usedInFix },
                // The averages are over signals, as the old sky view computed them.
                inViewAverage = heard.map { it.cn0DbHz }.averageOrNull(),
                usedAverage = used.map { it.cn0DbHz }.averageOrNull(),
            ),
        )
    }

    /** @return One satellite from its signals, or null when none of them has a position. */
    private fun merge(signals: List<SatelliteStatus>): SkySatellite? {
        val placed = signals.firstOrNull { SkyProjection.project(it.elevationDegrees, it.azimuthDegrees) != null }
            ?: return null
        return SkySatellite(
            svid = placed.svid,
            gnssType = placed.gnssType,
            sbasType = placed.sbasType,
            elevationDegrees = placed.elevationDegrees,
            azimuthDegrees = placed.azimuthDegrees,
            cn0DbHz = signals.maxOf { it.cn0DbHz },
            usedInFix = signals.any { it.usedInFix },
        )
    }

    private fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else sum() / size
}
