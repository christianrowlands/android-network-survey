package com.craxiom.networksurvey.gpstest.ui.status

import androidx.compose.runtime.Immutable
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.ui.common.TrendPoint
import com.craxiom.networksurvey.ui.common.TrendSeries
import com.craxiom.networksurvey.ui.common.medianOrNull

/** How far back the trends reach. */
const val TREND_WINDOW_MS = 120_000L

/** A pause longer than this between two points breaks a trend line (the signal was not listed). */
const val TREND_GAP_MS = 3_000L

/**
 * The fixed C/N0 scale of every trend line, so a drop is the same height on every row. It reaches
 * above the sky meter's 45 dB-Hz: a C/N0 unusually high for a phone is one of the things a closer
 * look is for, so the line must not flatten there.
 */
const val CN0_TREND_MIN = 10f
const val CN0_TREND_MAX = 50f

/** How many of a band's strongest signals its C/N0 trend averages. */
private const val TOP_SIGNALS = 4

/**
 * One band's trends.
 *
 * @property agc The median AGC of the band's signals at each scan; no point where none had AGC.
 * @property cn0Top The average C/N0 of the band's [TOP_SIGNALS] strongest signals at each scan.
 * Not the signals used in the fix: when a jammer pushes the weak ones out of the fix, their
 * average can rise, and every change of the used set would step the line.
 */
@Immutable
data class BandTrend(val group: GnssBandGroup, val agc: TrendSeries, val cn0Top: TrendSeries)

/**
 * What the trend views draw, at [nowMs].
 *
 * @property bands One entry per band heard in the window, in [GnssBandGroup] order.
 * @property signals Each signal's C/N0 trend, by [SatelliteStatus.signalKey].
 * @property agcReported True once this phone has reported AGC since the trends started.
 */
@Immutable
data class GnssTrends(
    val nowMs: Long,
    val bands: List<BandTrend>,
    val signals: Map<String, TrendSeries>,
    val agcReported: Boolean,
) {
    /** @return The C/N0 trend of the signal with [key], empty when it has none. */
    fun signal(key: String): TrendSeries = signals[key] ?: TrendSeries.EMPTY

    companion object {
        val EMPTY = GnssTrends(0L, emptyList(), emptyMap(), agcReported = false)
    }
}

/**
 * The last [TREND_WINDOW_MS] of AGC and C/N0, recorded at each GnssStatus, so a sudden change shows
 * as a step on a line instead of a number that was different a moment ago. Not thread safe; the
 * view model records and reads it on the main thread.
 *
 * A signal still listed with a C/N0 of 0 records 0 (the bottom of the line): that drop is what the
 * line is for. Only a signal missing from a scan, or AGC that went stale, leaves a gap.
 */
class GnssTrendHistory {
    private val signalSeries = mutableMapOf<String, ArrayDeque<TrendPoint>>()
    private val agcSeries = mutableMapOf<GnssBandGroup, ArrayDeque<TrendPoint>>()
    private val cn0Series = mutableMapOf<GnssBandGroup, ArrayDeque<TrendPoint>>()
    private var agcReported = false

    /** Records one scan of [statuses] (all of them, before any filter) taken at [nowMs]. */
    fun record(nowMs: Long, statuses: List<SatelliteStatus>) {
        for (status in statuses) {
            if (status.signalKey.isNotEmpty()) signalSeries.append(status.signalKey, nowMs, cn0(status))
        }
        statuses.groupBy(GnssBandGroup::of).forEach { (group, members) ->
            members.filter { it.hasAgc }.map { it.agcDb }.medianOrNull()?.let {
                agcSeries.append(group, nowMs, it)
                agcReported = true
            }
            val top = members.map(::cn0).sortedDescending().take(TOP_SIGNALS)
            cn0Series.append(group, nowMs, top.average().toFloat())
        }
        val oldest = nowMs - TREND_WINDOW_MS
        signalSeries.prune(oldest)
        agcSeries.prune(oldest)
        cn0Series.prune(oldest)
    }

    /** @return The trends as of [nowMs], copied so later scans do not change them. */
    fun snapshot(nowMs: Long): GnssTrends {
        val groups = (agcSeries.keys + cn0Series.keys).sortedBy { it.ordinal }
        return GnssTrends(
            nowMs = nowMs,
            bands = groups.map { BandTrend(it, agcSeries.series(it), cn0Series.series(it)) },
            signals = signalSeries.mapValues { TrendSeries(it.value.toList()) },
            agcReported = agcReported,
        )
    }

    /** Forgets everything, for when the GNSS view stops. */
    fun clear() {
        signalSeries.clear()
        agcSeries.clear()
        cn0Series.clear()
        agcReported = false
    }

    private fun cn0(status: SatelliteStatus): Float = status.cn0DbHz.coerceAtLeast(0f)

    private fun <K> MutableMap<K, ArrayDeque<TrendPoint>>.append(key: K, timeMs: Long, value: Float) {
        getOrPut(key) { ArrayDeque() }.addLast(TrendPoint(timeMs, value))
    }

    private fun <K> MutableMap<K, ArrayDeque<TrendPoint>>.prune(oldestMs: Long) {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val points = iterator.next().value
            while (points.isNotEmpty() && points.first().timeMs < oldestMs) points.removeFirst()
            if (points.isEmpty()) iterator.remove()
        }
    }

    private fun <K> Map<K, ArrayDeque<TrendPoint>>.series(key: K): TrendSeries =
        get(key)?.let { TrendSeries(it.toList()) } ?: TrendSeries.EMPTY
}
