package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.Record
import com.craxiom.networksurvey.scout.link.ReceivedRecordBatch

/**
 * Tells "Catching up" apart from live traffic. A board that loses its phone keeps surveying into
 * its ring buffer, and after a reconnect the buffered records flow first, stamped with the time
 * they were heard. While the newest record of each arriving batch is more than [lagThresholdMs]
 * older than its arrival, the Scout is catching up; the first batch that is close to live ends
 * it. Batches whose times are not synced (milliseconds since boot) cannot be judged and are
 * skipped.
 *
 * Used on the Scout thread only.
 */
class ScoutCatchUpTracker(private val lagThresholdMs: Long = DEFAULT_LAG_THRESHOLD_MS) {
    /** What the Catching up state shows. */
    data class Snapshot(val backlogRecords: Int, val ringFillPercent: Int)

    private var catchingUp = false
    private var backlogRecords = 0
    private var ringFillPercent = 0

    /** The current catch-up, null when the traffic is live. */
    val snapshot: Snapshot? get() = if (catchingUp) Snapshot(backlogRecords, ringFillPercent) else null

    /** Forgets any catch-up, for a new link or a new capture. */
    fun reset() {
        catchingUp = false
        backlogRecords = 0
        ringFillPercent = 0
    }

    /** Records the board's ring buffer fill from its latest status. */
    fun onRingFill(percent: Int) {
        ringFillPercent = percent
    }

    /** @return True when [received] changed whether the Scout is catching up. */
    fun onBatch(received: ReceivedRecordBatch): Boolean {
        if (!received.batch.timeSynced) return false
        val newest = received.batch.recordsList.maxOfOrNull(::boardTimeMs) ?: return false
        val late = received.receivedAtMs - newest > lagThresholdMs
        val before = catchingUp
        if (late) {
            catchingUp = true
            backlogRecords += received.batch.recordsCount
        } else {
            catchingUp = false
            backlogRecords = 0
        }
        return before != catchingUp
    }

    companion object {
        /** Records this much older than their arrival were buffered on the board. */
        const val DEFAULT_LAG_THRESHOLD_MS = 5_000L

        /** @return The `board_time_ms` of whichever record [record] holds, 0 when it holds none. */
        @JvmStatic
        fun boardTimeMs(record: Record): Long = when (record.kindCase) {
            Record.KindCase.AP -> record.ap.boardTimeMs
            Record.KindCase.CLIENT -> record.client.boardTimeMs
            Record.KindCase.PROBE -> record.probe.boardTimeMs
            Record.KindCase.BLE -> record.ble.boardTimeMs
            Record.KindCase.GPS -> record.gps.boardTimeMs
            Record.KindCase.KIND_NOT_SET, null -> 0L
        }
    }
}
