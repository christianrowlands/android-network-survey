package com.craxiom.networksurvey.scout.wifi

import android.location.Location
import kotlin.math.abs

/**
 * One phone location fix, kept apart from [Location] so the history is plain data.
 *
 * @property timeMs When the fix was taken, phone wall clock, Unix epoch milliseconds.
 */
data class PhoneFix(
    val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
) {
    companion object {
        /**
         * @return The fix [location] describes, with its time taken from its elapsed realtime when
         * it has one (the GNSS time in [Location.getTime] can disagree with the phone clock).
         */
        @JvmStatic
        fun from(location: Location, nowWallMs: Long, nowElapsedMs: Long): PhoneFix {
            val fixElapsedMs = location.elapsedRealtimeNanos / NANOS_PER_MILLI
            val timeMs = if (fixElapsedMs > 0) nowWallMs - (nowElapsedMs - fixElapsedMs) else location.time
            return PhoneFix(
                timeMs = timeMs,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                speed = if (location.hasSpeed()) location.speed else null,
            )
        }

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * Recent phone fixes, for placing a Scout record where it was heard (a port of the iOS
 * `LocationHistory`). Records a Scout buffered while out of range arrive late; the fix nearest to
 * their capture time still places them correctly, because the phone kept its location updates.
 *
 * Not thread safe; one owner thread calls it.
 */
class LocationHistory(private val capacity: Int = DEFAULT_CAPACITY) {
    private val samples = ArrayList<PhoneFix>()

    /** The number of fixes kept. */
    val size: Int get() = samples.size

    /** Adds [fix] in time order. A fix with the same time as the newest one replaces nothing. */
    fun append(fix: PhoneFix) {
        val last = samples.lastOrNull()
        when {
            last == null || fix.timeMs > last.timeMs -> samples.add(fix)
            fix.timeMs == last.timeMs -> return
            else -> {
                val index = samples.indexOfFirst { it.timeMs > fix.timeMs }.takeIf { it >= 0 } ?: samples.size
                samples.add(index, fix)
            }
        }
        // Trim in chunks so appends stay cheap.
        if (samples.size > capacity + capacity / TRIM_CHUNK_DIVISOR) {
            samples.subList(0, samples.size - capacity).clear()
        }
    }

    /** @return The fix nearest in time to [timeMs], or null when there is none. */
    fun nearest(timeMs: Long): PhoneFix? {
        if (samples.isEmpty()) return null
        var low = 0
        var high = samples.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (samples[mid].timeMs < timeMs) low = mid + 1 else high = mid
        }
        if (low == 0) return samples[0]
        if (low == samples.size) return samples[low - 1]
        val before = samples[low - 1]
        val after = samples[low]
        return if (timeMs - before.timeMs <= after.timeMs - timeMs) before else after
    }

    /**
     * @return The fix nearest to [timeMs] when it is at most [maxGapMs] away, else null: a record
     * with no fix close to when it was heard is saved without a location rather than misplaced.
     */
    fun nearestWithin(timeMs: Long, maxGapMs: Long): PhoneFix? =
        nearest(timeMs)?.takeIf { abs(it.timeMs - timeMs) <= maxGapMs }

    fun clear() {
        samples.clear()
    }

    companion object {
        /** About 30 minutes of fixes at one a second, as on iOS. */
        const val DEFAULT_CAPACITY = 1800

        private const val TRIM_CHUNK_DIVISOR = 8
    }
}
