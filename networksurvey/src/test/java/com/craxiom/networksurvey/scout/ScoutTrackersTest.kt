package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.ApObservation
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.Record
import com.craxiom.networksurvey.board.v1.RecordBatch
import com.craxiom.networksurvey.scout.link.ReceivedRecordBatch
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.NearbyScoutStatus
import com.craxiom.networksurvey.scout.model.ScoutBattery
import com.craxiom.networksurvey.scout.model.ScoutDistance
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.scout.link.ScoutAdvertisement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drop accounting, catch-up detection, battery, IDs and the nearby row states. */
class ScoutTrackersTest {

    private fun status(records: Int, raw: Int = 0, rx: Int = 0, uptimeMs: Long = 10_000) = BoardStatus.newBuilder()
        .setDroppedRecords(records).setDroppedRawFrames(raw).setDroppedRxFrames(rx).setUptimeMs(uptimeMs).build()

    private fun batch(boardTimeMs: Long, receivedAtMs: Long, records: Int = 2, synced: Boolean = true): ReceivedRecordBatch {
        val builder = RecordBatch.newBuilder().setTimeSynced(synced)
        repeat(records) { builder.addRecords(Record.newBuilder().setAp(ApObservation.newBuilder().setBoardTimeMs(boardTimeMs))) }
        return ReceivedRecordBatch(0, 0, builder.build(), receivedAtMs)
    }

    @Test
    fun drops_countOnlyTheIncreaseSinceTheBaseline() {
        val tracker = ScoutDropTracker()
        assertFalse(tracker.onStatus(status(records = 3, rx = 17)))
        assertFalse(tracker.current.dataMissed)

        assertFalse(tracker.onStatus(status(records = 3, rx = 17, uptimeMs = 11_000)))
        assertTrue(tracker.onStatus(status(records = 5, rx = 20, uptimeMs = 12_000)))
        assertEquals(ScoutDrops(records = 2, rxFrames = 3), tracker.current)
        assertTrue(tracker.current.dataMissed)
    }

    @Test
    fun drops_rxFramesAloneAreNotDataMissed() {
        val tracker = ScoutDropTracker()
        tracker.onStatus(status(records = 0, rx = 0))
        tracker.onStatus(status(records = 0, rx = 500, uptimeMs = 11_000))
        assertFalse(tracker.current.dataMissed)
    }

    @Test
    fun drops_aBoardRestartCountsFromZero() {
        val tracker = ScoutDropTracker()
        tracker.onStatus(status(records = 10, uptimeMs = 60_000))
        tracker.onStatus(status(records = 4, uptimeMs = 2_000))
        assertEquals(4L, tracker.current.records)

        tracker.reset()
        tracker.onNotification(missed = 3, nowMs = 1_000)
        assertEquals(ScoutDrops(missedNotifications = 3, linkLossSignificant = true), tracker.current)
        assertTrue(tracker.current.dataMissed)
    }

    @Test
    fun drops_oneLostNotificationInAMinuteIsCountedButNotDataMissed() {
        // The hardware run: 1 lost in 194 notifications, no board drops
        val tracker = ScoutDropTracker()
        repeat(100) { tracker.onNotification(missed = 0, nowMs = it * 300L) }
        tracker.onNotification(missed = 1, nowMs = 30_000)
        repeat(93) { tracker.onNotification(missed = 0, nowMs = 30_300L + it * 300L) }
        assertEquals(1L, tracker.current.missedNotifications)
        assertFalse(tracker.current.dataMissed)
    }

    @Test
    fun drops_lossesAboveOnePercentOfTheLastMinuteAreDataMissed() {
        val tracker = ScoutDropTracker()
        repeat(600) { tracker.onNotification(missed = 0, nowMs = it * 100L) }
        // Two lost against about 600 expected in the minute: well under 1 percent
        tracker.onNotification(missed = 1, nowMs = 60_000)
        tracker.onNotification(missed = 1, nowMs = 60_100)
        assertFalse(tracker.current.dataMissed)
        // Eight lost against about 608: above 1 percent
        tracker.onNotification(missed = 6, nowMs = 60_200)
        assertTrue(tracker.current.dataMissed)
        // It stays set for the capture, like the board's own drops
        repeat(1_000) { tracker.onNotification(missed = 0, nowMs = 60_300L + it * 100L) }
        assertTrue(tracker.current.dataMissed)
    }

    @Test
    fun drops_lossesOlderThanTheWindowDoNotAddUp() {
        val tracker = ScoutDropTracker()
        tracker.onNotification(missed = 1, nowMs = 0)
        tracker.onNotification(missed = 1, nowMs = ScoutDropTracker.LOSS_WINDOW_MS + 1)
        assertEquals(2L, tracker.current.missedNotifications)
        assertFalse(tracker.current.dataMissed)
    }

    @Test
    fun catchUp_lateBatchesStartItAndALiveOneEndsIt() {
        val tracker = ScoutCatchUpTracker()
        assertFalse(tracker.onBatch(batch(boardTimeMs = 1_000, receivedAtMs = 2_000)))
        assertNull(tracker.snapshot)

        tracker.onRingFill(60)
        assertTrue(tracker.onBatch(batch(boardTimeMs = 10_000, receivedAtMs = 120_000, records = 12)))
        tracker.onBatch(batch(boardTimeMs = 20_000, receivedAtMs = 121_000, records = 5))
        assertEquals(ScoutCatchUpTracker.Snapshot(backlogRecords = 17, ringFillPercent = 60), tracker.snapshot)

        assertTrue(tracker.onBatch(batch(boardTimeMs = 121_500, receivedAtMs = 122_000)))
        assertNull(tracker.snapshot)
    }

    @Test
    fun catchUp_ignoresBatchesWithBootTimes() {
        val tracker = ScoutCatchUpTracker()
        assertFalse(tracker.onBatch(batch(boardTimeMs = 15_000, receivedAtMs = 1_800_000_000_000, synced = false)))
        assertNull(tracker.snapshot)
    }

    @Test
    fun battery_followsTheLiPoCurve() {
        assertEquals(0, ScoutBattery.percent(3200))
        assertEquals(0, ScoutBattery.percent(3300))
        assertEquals(50, ScoutBattery.percent(3800))
        assertEquals(65, ScoutBattery.percent(3900))
        assertEquals(86, ScoutBattery.percent(4050))
        assertEquals(100, ScoutBattery.percent(4200))
        assertEquals(100, ScoutBattery.percent(4400))
        assertNull(ScoutBattery.percent(BoardStatus.getDefaultInstance()))
    }

    @Test
    fun scoutId_isNsPlusTheIdentityAddress() {
        val bytes = byteArrayOf(0x02, 0x00, 0x5E, 0x00, 0xAB.toByte(), 0xCD.toByte())
        assertEquals(ScoutId("NS-02005E00ABCD"), ScoutId.fromBtAddress(bytes))
        assertEquals(ScoutId("NS-02005E00ABCD"), ScoutId.fromAddress("02:00:5e:00:ab:cd"))
        assertEquals("ABCD", ScoutId.shortIdFromAddress("02:00:5E:00:AB:CD"))
        assertNull(ScoutId.fromBtAddress(ByteArray(5)))
        assertNull(ScoutId.fromAddress("not an address"))
    }

    @Test
    fun nearby_rowStatesFollowTheAdvertisementFlags() {
        fun nearby(pairable: Boolean, hasBond: Boolean, major: Int = 1, pairedHere: Boolean = false, rssi: Int = -50) =
            NearbyScout(
                "02:00:5E:00:AB:CD", "ABCD", rssi,
                ScoutAdvertisement(major, pairable, hasBond, BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1),
                pairedHere,
            )

        assertEquals(NearbyScoutStatus.READY_TO_PAIR, nearby(pairable = true, hasBond = false).status)
        assertEquals(NearbyScoutStatus.READY_TO_PAIR, nearby(pairable = true, hasBond = true).status)
        assertEquals(NearbyScoutStatus.PAIRED_WITH_ANOTHER_PHONE, nearby(pairable = false, hasBond = true).status)
        assertFalse(nearby(pairable = false, hasBond = true).status.canPair)
        assertEquals(NearbyScoutStatus.NEEDS_FIRMWARE_UPDATE, nearby(pairable = true, hasBond = false, major = 0).status)
        assertEquals(NearbyScoutStatus.APP_UPDATE_NEEDED, nearby(pairable = true, hasBond = false, major = 2).status)
        assertEquals(NearbyScoutStatus.PAIRED_HERE, nearby(pairable = false, hasBond = true, pairedHere = true).status)
        assertEquals(NearbyScoutStatus.WAS_RESET, nearby(pairable = true, hasBond = false, pairedHere = true).status)
        assertEquals("NS-ABCD", nearby(pairable = true, hasBond = false).name)
    }

    @Test
    fun distance_wordsFromRssi() {
        assertEquals(ScoutDistance.VERY_CLOSE, ScoutDistance.of(-60))
        assertEquals(ScoutDistance.NEARBY, ScoutDistance.of(-61))
        assertEquals(ScoutDistance.NEARBY, ScoutDistance.of(-75))
        assertEquals(ScoutDistance.WEAK_SIGNAL, ScoutDistance.of(-76))
    }
}
