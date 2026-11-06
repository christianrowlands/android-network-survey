package com.craxiom.networksurvey.scout.raw

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.RawFrame
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.ScoutRawFrames
import com.craxiom.networksurvey.scout.wifi.BoardClock

/** One raw frame ready for the pcapng file: which Scout heard it, and when (Unix epoch microseconds). */
class StampedRawFrame(val scoutId: ScoutId, val timestampUs: Long, val frame: RawFrame)

/**
 * Gives each raw frame from a Scout its wall clock capture time and keeps it within the board's
 * effective snaplen. Board time becomes wall time through the same [BoardClock] the record
 * pipelines use (a synced board reports Unix time; an unsynced one is placed from its status), with
 * the board's microseconds kept. Frames heard before [start] are dropped, so a file never holds
 * frames from before the survey.
 *
 * The Scout thread, which delivers both raw frames and status, is its only caller apart from [start].
 */
class RawFrameStamper {
    private val clock = BoardClock()
    @Volatile
    private var floorMs = 0L

    /** Starts a capture at [nowMs]: frames heard earlier are dropped. */
    fun start(nowMs: Long) {
        floorMs = nowMs
    }

    fun onStatus(scoutId: ScoutId, status: BoardStatus, receivedAtMs: Long) = clock.noteStatus(status, scoutId, receivedAtMs)

    /**
     * @param snaplen The effective snaplen from the board's `ConfigureResult`, 0 when unknown.
     * @return The frames of [raw] heard since the start, stamped, each cut to [snaplen].
     */
    fun stamp(raw: ScoutRawFrames, snaplen: Int): List<StampedRawFrame> = raw.frames.mapNotNull { frame ->
        val timeUs = captureTimeUs(frame, raw)
        if (timeUs < floorMs * MICROS_PER_MILLI) null else StampedRawFrame(raw.scoutId, timeUs, withinSnaplen(frame, snaplen))
    }

    private fun captureTimeUs(frame: RawFrame, raw: ScoutRawFrames): Long {
        val boardTimeUs = frame.boardTimeUs
        if (boardTimeUs <= 0L) return raw.receivedAtMs * MICROS_PER_MILLI
        val wallMs = clock.wallTimeMs(boardTimeUs / MICROS_PER_MILLI, frame.timeSynced, raw.scoutId, raw.receivedAtMs)
        return wallMs * MICROS_PER_MILLI + boardTimeUs % MICROS_PER_MILLI
    }

    companion object {
        private const val MICROS_PER_MILLI = 1_000L

        /**
         * @return [frame] with its data cut to [snaplen] bytes (0 means no limit); the on-air length
         * stays, so a reader sees the frame was cut. The board already applies its snaplen; this only
         * guards against a board that sends more than it reported.
         */
        fun withinSnaplen(frame: RawFrame, snaplen: Int): RawFrame {
            if (snaplen <= 0 || frame.data.size() <= snaplen) return frame
            return frame.toBuilder()
                .setData(frame.data.substring(0, snaplen))
                .setOrigLen(maxOf(frame.origLen, frame.data.size()))
                .build()
        }
    }
}
