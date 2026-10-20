package com.craxiom.networksurvey.ui.common

import androidx.compose.runtime.Immutable

/** One point of a trend: when it was taken (elapsed realtime) and its value. */
@Immutable
data class TrendPoint(val timeMs: Long, val value: Float)

/** A trend over the window, oldest point first. */
@Immutable
data class TrendSeries(val points: List<TrendPoint>) {
    /** The newest value, or null when there is none. */
    val last: Float? get() = points.lastOrNull()?.value

    /** @return The window's median, or null when there are no points. */
    fun median(): Float? = points.map { it.value }.medianOrNull()

    /** @return The lowest and highest values, or null when there are no points. */
    fun range(): ClosedFloatingPointRange<Float>? =
        if (points.isEmpty()) null else points.minOf { it.value }..points.maxOf { it.value }

    companion object {
        val EMPTY = TrendSeries(emptyList())
    }
}

/** @return The median of these values, or null when there are none. */
fun List<Float>.medianOrNull(): Float? {
    if (isEmpty()) return null
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}
