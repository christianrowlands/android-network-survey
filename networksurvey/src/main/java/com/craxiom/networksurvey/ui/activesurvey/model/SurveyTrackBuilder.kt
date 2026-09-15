package com.craxiom.networksurvey.ui.activesurvey.model

import com.craxiom.networksurvey.logging.db.DbUploadStore
import com.craxiom.networksurvey.logging.db.SurveyedPointGate

/**
 * One retained fix on the survey trail. Deliberately free of any Android or MapLibre type so the
 * builder can be exercised by a plain JVM unit test.
 */
data class TrackPoint(val latitude: Double, val longitude: Double)

/**
 * Accumulates the trail drawn over the Survey Monitor map.
 *
 * The trail and the survey point dots answer different questions: the trail is where the device
 * travelled, the dots are what was captured there and whether it was sent. The difference between
 * them is the useful part, because trail with no dots on it means nothing was recorded along that
 * stretch, whether from a poor location fix, no records of the selected kinds, a battery pause, or
 * a stalled scan.
 *
 * The trail is a list of segments rather than one polyline so that a break in collection never
 * draws a straight line along a path the device did not travel. A segment ends when:
 * - [startNewSegment] is called, which the view model does every time it re-registers with the
 *   service. That is exact rather than heuristic, because re-registration is the event that causes
 *   the gap when the screen is backgrounded and resumed.
 * - more than [gapThresholdMs] passes between fixes, which is the backstop for a location dropout
 *   that is not a lifecycle event.
 *
 * Fixes closer together than [thinningMeters] are dropped, which keeps standing still from piling
 * up hundreds of points on one spot, and the retained total is capped at [maxPoints] by evicting
 * the oldest segment first.
 *
 * Not thread safe; the view model calls it from the main thread only.
 */
class SurveyTrackBuilder(
    private var gapThresholdMs: Long = DEFAULT_GAP_THRESHOLD_MS,
    private val thinningMeters: Int = SurveyedPointGate.WALKING_THRESHOLD_METERS,
    private val maxPoints: Int = MAX_POINTS,
) {
    private val segments = ArrayDeque<MutableList<TrackPoint>>()
    private var lastFixTimeMs: Long? = null
    private var retained = 0

    /**
     * Updates how long a silence between fixes has to be before it counts as a break. The location
     * update rate is only known once the service has registered its listeners, and it changes when
     * the user changes a scan rate, so this is set rather than fixed at construction.
     */
    fun setGapThreshold(thresholdMs: Long) {
        gapThresholdMs = thresholdMs
    }

    /**
     * Ends the current segment so the next fix starts a fresh one. Safe to call repeatedly; it
     * never leaves an empty segment behind because segments are only created on append.
     */
    fun startNewSegment() {
        lastFixTimeMs = null
        if (segments.lastOrNull()?.isEmpty() == true) segments.removeLast()
    }

    /**
     * Offers a fix to the trail.
     *
     * @return true when the fix was retained, so the caller knows whether the rendered trail
     * actually changed.
     */
    fun add(latitude: Double, longitude: Double, timeMs: Long): Boolean {
        val previousFixTime = lastFixTimeMs
        val broken = previousFixTime == null || timeMs - previousFixTime > gapThresholdMs

        val current = if (broken) null else segments.lastOrNull()
        val anchor = current?.lastOrNull()
        if (anchor != null &&
            !DbUploadStore.hasMovedEnough(
                latitude,
                longitude,
                Pair(anchor.latitude, anchor.longitude),
                thinningMeters
            )
        ) {
            // Still count the fix as seen, otherwise standing still eventually looks like a gap
            lastFixTimeMs = timeMs
            return false
        }

        val segment = current ?: ArrayList<TrackPoint>().also { segments.addLast(it) }
        segment.add(TrackPoint(latitude, longitude))
        lastFixTimeMs = timeMs
        retained++
        trimToCap()
        return true
    }

    /**
     * The trail to draw, one entry per segment. Segments of a single point are dropped because a
     * polyline needs two, which keeps that guard from being forgotten at a call site.
     */
    fun segments(): List<List<TrackPoint>> =
        segments.filter { it.size >= 2 }.map { it.toList() }

    /** Forgets the whole trail, for the start of a new survey session. */
    fun clear() {
        segments.clear()
        lastFixTimeMs = null
        retained = 0
    }

    /**
     * Evicts whole segments oldest first, matching the policy the surveyed point table already
     * uses when it hits its own cap.
     */
    private fun trimToCap() {
        while (retained > maxPoints && segments.size > 1) {
            retained -= segments.removeFirst().size
        }

        // A single segment longer than the cap is trimmed from its front rather than dropped, so
        // the trail never vanishes entirely on one very long unbroken run.
        val only = segments.firstOrNull()
        if (retained > maxPoints && only != null) {
            repeat(minOf(retained - maxPoints, only.size)) { only.removeAt(0) }
            retained = segments.sumOf { it.size }
        }
    }

    companion object {
        /**
         * The floor for treating a silence between fixes as a break. Location updates are requested
         * at the smallest active scan rate, so callers scale this by that rate rather than relying
         * on the floor alone.
         */
        const val DEFAULT_GAP_THRESHOLD_MS = 30_000L

        /** How many location updates may be missed before the trail is considered broken. */
        const val GAP_SCAN_RATE_MULTIPLIER = 5

        /**
         * A safety valve rather than a limit a survey day should reach. Fix rate, not distance, is
         * the limiter: at a 20 second update rate a long drive yields only a few hundred fixes, so
         * a realistic day after thinning lands well under this.
         */
        const val MAX_POINTS = 10_000

        /**
         * The gap threshold to use for a given location update rate, which is the reason this is
         * not simply a constant.
         */
        fun gapThresholdFor(locationUpdateRateMs: Int): Long =
            maxOf(DEFAULT_GAP_THRESHOLD_MS, GAP_SCAN_RATE_MULTIPLIER * locationUpdateRateMs.toLong())
    }
}
