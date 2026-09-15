package com.craxiom.networksurvey.ui.activesurvey

import com.craxiom.networksurvey.ui.activesurvey.model.SurveyTrackBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the segmentation, thinning, and capping rules behind the Survey Monitor map trail. The
 * most important of these is the gap split: without it the trail draws a straight line across
 * ground the device never travelled whenever collection pauses.
 */
class SurveyTrackBuilderTest {
    // Roughly 111,320 meters per degree of latitude at the equator
    private fun latFor(meters: Double) = meters / 111_320.0

    private fun builder(
        gapMs: Long = 30_000L,
        thinning: Int = 10,
        max: Int = SurveyTrackBuilder.MAX_POINTS,
    ) = SurveyTrackBuilder(gapThresholdMs = gapMs, thinningMeters = thinning, maxPoints = max)

    @Test
    fun `a fix after the gap threshold starts a new segment`() {
        val track = builder()
        track.add(0.0, 0.0, 0)
        track.add(latFor(20.0), 0.0, 10_000)
        // 40 seconds of silence, past the 30 second threshold
        track.add(latFor(40.0), 0.0, 50_000)
        track.add(latFor(60.0), 0.0, 60_000)

        val segments = track.segments()
        assertEquals("the gap should split the trail in two", 2, segments.size)
        assertEquals(2, segments[0].size)
        assertEquals(2, segments[1].size)
    }

    @Test
    fun `startNewSegment splits even when the time gap is small`() {
        val track = builder()
        track.add(0.0, 0.0, 0)
        track.add(latFor(20.0), 0.0, 1_000)

        track.startNewSegment()

        track.add(latFor(40.0), 0.0, 2_000)
        track.add(latFor(60.0), 0.0, 3_000)

        assertEquals("a resume should split regardless of elapsed time", 2, track.segments().size)
    }

    @Test
    fun `a single missed fix does not split the segment`() {
        val track = builder()
        track.add(0.0, 0.0, 0)
        track.add(latFor(20.0), 0.0, 10_000)
        // One missed update at a 10 second rate, still inside the threshold
        track.add(latFor(40.0), 0.0, 29_000)

        val segments = track.segments()
        assertEquals("ordinary jitter must not shatter the trail", 1, segments.size)
        assertEquals(3, segments[0].size)
    }

    @Test
    fun `fixes closer than the thinning distance are dropped`() {
        val track = builder(thinning = 10)
        assertTrue(track.add(0.0, 0.0, 0))
        assertFalse("4 m apart is below the 10 m threshold", track.add(latFor(4.0), 0.0, 1_000))
        assertTrue("12 m apart clears the threshold", track.add(latFor(12.0), 0.0, 2_000))

        assertEquals(1, track.segments().size)
        assertEquals(2, track.segments()[0].size)
    }

    @Test
    fun `the cap evicts the oldest segment first`() {
        val track = builder(max = 6)

        // Three segments of three points each, split by explicit breaks
        var time = 0L
        var meters = 0.0
        repeat(3) { segment ->
            if (segment > 0) track.startNewSegment()
            repeat(3) {
                meters += 20.0
                time += 1_000
                track.add(latFor(meters), 0.0, time)
            }
        }

        val segments = track.segments()
        val retained = segments.sumOf { it.size }
        assertTrue("retained $retained should not exceed the cap", retained <= 6)
        assertEquals("the newest segments survive", 2, segments.size)
    }

    @Test
    fun `segments with a single point are not returned`() {
        val track = builder()
        track.add(0.0, 0.0, 0)

        assertTrue("a lone point cannot be drawn as a line", track.segments().isEmpty())

        track.add(latFor(20.0), 0.0, 1_000)
        assertEquals(1, track.segments().size)
    }

    @Test
    fun `the gap threshold scales with the location update rate`() {
        // A 5 second rate stays on the floor, a 25 second rate scales past it
        assertEquals(30_000L, SurveyTrackBuilder.gapThresholdFor(5_000))
        assertEquals(125_000L, SurveyTrackBuilder.gapThresholdFor(25_000))
    }

    @Test
    fun `clear forgets the trail`() {
        val track = builder()
        track.add(0.0, 0.0, 0)
        track.add(latFor(20.0), 0.0, 1_000)
        assertEquals(1, track.segments().size)

        track.clear()

        assertTrue(track.segments().isEmpty())
    }
}
