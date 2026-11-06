package com.craxiom.networksurvey.scout.wifi

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.model.ScoutId
import kotlin.math.abs

/**
 * Turns a Scout's board timestamps into phone wall clock time (a port of the iOS `BoardClock`).
 *
 * After SetTime a board reports Unix epoch milliseconds (`time_synced`). Before that, or after a
 * board reset, it reports milliseconds since it booted; the offset to wall time is then learned
 * from its status, which arrives about once a second. Without either, the time the phone received
 * the record is used.
 *
 * Not thread safe; one owner thread calls it.
 */
class BoardClock(
    /** A synced time further than this from the receive time is not trusted. */
    private val maxSkewMs: Long = DEFAULT_MAX_SKEW_MS,
) {
    /** Wall time minus board time, per Scout whose clock is not synced. */
    private val offsets = HashMap<ScoutId, Long>()

    /** Learns the offset of an unsynced board from a status received at [receivedAtMs]. */
    fun noteStatus(status: BoardStatus, scoutId: ScoutId, receivedAtMs: Long) {
        if (status.timeSynced || status.boardTimeMs == 0L) {
            offsets.remove(scoutId)
        } else {
            offsets[scoutId] = receivedAtMs - status.boardTimeMs
        }
    }

    /**
     * @return The wall time, Unix epoch milliseconds, of a record stamped [boardTimeMs] by
     * [scoutId] and received at [receivedAtMs].
     */
    fun wallTimeMs(boardTimeMs: Long, synced: Boolean, scoutId: ScoutId, receivedAtMs: Long): Long {
        if (boardTimeMs <= 0L) return receivedAtMs
        if (synced) {
            return if (abs(boardTimeMs - receivedAtMs) <= maxSkewMs) boardTimeMs else receivedAtMs
        }
        val offset = offsets[scoutId] ?: return receivedAtMs
        // A record cannot be from the future; clamp small clock jitter.
        return minOf(offset + boardTimeMs, receivedAtMs)
    }

    companion object {
        /** 24 hours, as on iOS. */
        const val DEFAULT_MAX_SKEW_MS = 24L * 60 * 60 * 1000
    }
}
