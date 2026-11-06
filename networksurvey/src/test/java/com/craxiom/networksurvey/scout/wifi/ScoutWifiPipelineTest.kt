package com.craxiom.networksurvey.scout.wifi

import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData.ap
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData.batch
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData.fix
import com.craxiom.networksurvey.scout.wifi.ScoutWifiTestData.scout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Scout record path: capture time, the fix nearest it, late records, the windows, the live
 * snapshot, and what is dropped from before the start.
 */
class ScoutWifiPipelineTest {

    private val pipeline = ScoutWifiPipeline().apply { start(scout, START) }

    private fun settings(surveyStartMs: Long? = START, liveViews: Boolean = false) = ScoutWifiPipeline.Settings(
        windowMs = WINDOW, sweepMs = SWEEP, maxFixGapMs = 30_000, surveyStartMs = surveyStartMs,
        liveViews = liveViews, snapshotIntervalMs = 5_000,
    )

    @Test
    fun `a live window gives one record per access point, the strongest, placed by the nearest fix`() {
        pipeline.onFix(fix(START + 1_000, latitude = 1.0))
        pipeline.onFix(fix(START + 8_000, latitude = 2.0))
        pipeline.onRecords(batch(START + 2_500, ap(1, -70, START + 2_000), ap(2, -60, START + 2_000)), WINDOW)
        pipeline.onRecords(batch(START + 7_500, ap(1, -50, START + 7_000)), WINDOW)

        val windows = pipeline.tick(START + WINDOW + 3_000, settings()).windows

        val beacons = windows.single()
        assertEquals(2, beacons.size)
        val first = beacons.first { it.observation.rssiMax == -50 }
        assertEquals(START + 7_000, first.captureTimeMs)
        assertEquals(2.0, first.fix!!.latitude, 0.0)
        assertFalse(first.late)
        assertEquals(1.0, beacons.first { it.observation.rssiMax == -60 }.fix!!.latitude, 0.0)
    }

    @Test
    fun `a backlog keeps the time and place it was heard and is marked late`() {
        // Out of range from +20 s to +80 s; the phone kept its fixes, the Scout kept its records
        for (second in 0..10) pipeline.onFix(fix(START + second * 10_000L, latitude = second.toDouble()))
        val heard = START + 41_000
        pipeline.onRecords(batch(START + 90_000, ap(1, -60, heard)), WINDOW)

        val beacon = pipeline.tick(START + 95_000, settings()).windows.single().single()

        assertEquals(heard, beacon.captureTimeMs)
        assertEquals(4.0, beacon.fix!!.latitude, 0.0)
        assertTrue(beacon.late)
    }

    @Test
    fun `a capture with no fix within the gap is saved without a location`() {
        pipeline.onFix(fix(START))
        pipeline.onRecords(batch(START + 40_500, ap(1, -60, START + 40_000)), WINDOW)

        val beacon = pipeline.tick(START + 60_000, settings()).windows.single().single()

        assertNull(beacon.fix)
    }

    @Test
    fun `records captured before the source or the survey started are dropped`() {
        pipeline.onRecords(batch(START + 1_000, ap(1, -60, START - 60_000)), WINDOW)
        pipeline.onRecords(batch(START + 6_000, ap(2, -60, START + 5_000)), WINDOW)
        pipeline.onRecords(batch(START + 16_000, ap(3, -60, START + 15_000)), WINDOW)

        // The survey started after the live view started the Scout
        val windows = pipeline.tick(START + 40_000, settings(surveyStartMs = START + 10_000)).windows

        assertEquals(listOf(3), windows.flatten().map { it.observation.bssid.byteAt(5).toInt() })
    }

    @Test
    fun `the live snapshot lists every access point heard within the last sweep, only for live views`() {
        pipeline.onRecords(batch(START + 1_000, ap(1, -60, START + 1_000)), WINDOW)
        pipeline.onRecords(batch(START + 9_000, ap(2, -60, START + 9_000), ap(1, -55, START + 9_000)), WINDOW)

        assertNull(pipeline.tick(START + 10_000, settings()).snapshot)
        val snapshot = pipeline.tick(START + 10_000, settings(liveViews = true)).snapshot!!
        assertEquals(2, snapshot.size)
        assertEquals(-55, snapshot.first { it.observation.bssid.byteAt(5).toInt() == 1 }.observation.rssiMax)

        // Not again before the snapshot interval, and an access point not heard for a sweep drops off
        assertNull(pipeline.tick(START + 12_000, settings(liveViews = true)).snapshot)
        pipeline.onRecords(batch(START + 30_000, ap(2, -60, START + 30_000)), WINDOW)
        val later = pipeline.tick(START + 30_000, settings(liveViews = true)).snapshot!!
        assertEquals(1, later.size)
    }

    @Test
    fun `without a live view the live list still forgets access points, so a long survey does not grow it`() {
        pipeline.onRecords(batch(START + 1_000, ap(1, -60, START + 1_000), ap(2, -60, START + 1_000)), WINDOW)
        pipeline.tick(START + 2_000, settings())
        assertEquals(2, pipeline.liveCount)

        pipeline.tick(START + 1_000 + SWEEP + 1, settings())

        assertEquals(0, pipeline.liveCount)
    }

    @Test
    fun `late records never reach the live snapshot`() {
        pipeline.onRecords(batch(START + 60_000, ap(1, -60, START + 50_000)), WINDOW)

        assertNull(pipeline.tick(START + 60_000, settings(liveViews = true)).snapshot)
    }

    @Test
    fun `records of another Scout are ignored`() {
        pipeline.start(scout.copy(id = "NS-02005E00FFFF"), START)
        pipeline.onRecords(batch(START + 2_000, ap(1, -60, START + 1_000)), WINDOW)

        assertTrue(pipeline.tick(START + 60_000, settings()).windows.isEmpty())
    }

    @Test
    fun `draining the open window saves it, but nothing from before the survey started even once the session ended`() {
        pipeline.onRecords(batch(START + 2_000, ap(1, -60, START + 1_000), ap(2, -60, START + 8_000)), WINDOW)

        // A tick while the survey runs notes its start; the window is still open
        assertTrue(pipeline.tick(START + 9_000, settings(surveyStartMs = START + 5_000)).windows.isEmpty())

        // The survey stopped first, so the host no longer knows its start
        val windows = pipeline.drainAll(settings(surveyStartMs = null))

        assertEquals(listOf(2), windows.flatten().map { it.observation.bssid.byteAt(5).toInt() })
        assertTrue("Nothing is left to emit twice", pipeline.drainAll(settings()).isEmpty())
    }

    @Test
    fun `peeking shows the open window and leaves it to close for the others`() {
        pipeline.onRecords(batch(START + 2_000, ap(1, -60, START + 1_000)), WINDOW)

        val peeked = pipeline.peekAll(settings())
        assertEquals(1, peeked.flatten().size)

        val closed = pipeline.tick(START + WINDOW + 3_000, settings()).windows
        assertEquals(1, closed.flatten().size)
    }

    @Test
    fun `a new capture forgets the earlier survey start`() {
        pipeline.onRecords(batch(START + 2_000, ap(1, -60, START + 1_000)), WINDOW)
        pipeline.tick(START + 3_000, settings(surveyStartMs = START + 5_000))
        pipeline.start(scout, START + 10_000)
        pipeline.onRecords(batch(START + 12_000, ap(2, -60, START + 11_000)), WINDOW)

        val windows = pipeline.drainAll(settings(surveyStartMs = null))

        assertEquals(listOf(2), windows.flatten().map { it.observation.bssid.byteAt(5).toInt() })
    }

    private companion object {
        // A multiple of the window, so windows start at START.
        const val START = 1_792_000_020_000L
        const val WINDOW = 15_000L
        const val SWEEP = 14_000L
    }
}
