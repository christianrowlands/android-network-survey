package com.craxiom.networksurvey.scout.model

import com.craxiom.networksurvey.board.v1.ApObservation
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.ClientObservation
import com.craxiom.networksurvey.board.v1.ProbeObservation
import com.craxiom.networksurvey.board.v1.RawFrame

/**
 * The Wi-Fi records of one `RecordBatch` from a Scout, split by kind. BLE observations and GPS
 * fixes are left out: Android keeps Bluetooth and location from the phone.
 */
class ScoutRecords(
    val scoutId: ScoutId,
    /** Phone wall clock time the batch arrived, Unix epoch milliseconds. */
    val receivedAtMs: Long,
    /** True: every `board_time_ms` is Unix epoch milliseconds. False: milliseconds since the board booted. */
    val timeSynced: Boolean,
    val accessPoints: List<ApObservation>,
    val clients: List<ClientObservation>,
    val probes: List<ProbeObservation>,
    /** Records notifications lost on the link just before this batch. */
    val missedNotifications: Int,
)

/** The raw 802.11 frames of one Raw notification from a Scout. */
class ScoutRawFrames(
    val scoutId: ScoutId,
    /** Phone wall clock time the notification arrived, Unix epoch milliseconds. */
    val receivedAtMs: Long,
    val frames: List<RawFrame>,
    /** Raw notifications lost on the link just before this one. */
    val missedNotifications: Int,
)

/**
 * Receives what the paired Scout captures. Every callback runs on the Scout thread, which also
 * decodes the link, so implementations hand heavy work (files, the network) to their own thread.
 */
interface ScoutRecordListener {
    /** One batch of access point, client and probe observations. */
    fun onScoutRecords(records: ScoutRecords) {}

    /** Raw frames, only while a capture asked for them. */
    fun onScoutRawFrames(frames: ScoutRawFrames) {}

    /** The board's status, about once a second while connected. */
    fun onScoutStatus(scoutId: ScoutId, status: BoardStatus) {}
}
