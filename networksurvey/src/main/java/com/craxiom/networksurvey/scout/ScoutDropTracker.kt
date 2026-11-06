package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.BoardStatus
import java.util.ArrayDeque

/** Data a Scout missed since the current capture started, for "Some data missed" and its details. */
data class ScoutDrops(
    /** Records the board dropped (ring full, or too large for the MTU). */
    val records: Long = 0,
    /** Raw frames the board dropped (rate limit or buffer). */
    val rawFrames: Long = 0,
    /** 802.11 frames lost before parsing on the board; dense areas raise it, so it is shown only. */
    val rxFrames: Long = 0,
    /** Records notifications lost on the link (sequence gaps). */
    val missedNotifications: Long = 0,
    /**
     * True once the link lost enough Records notifications to matter: see
     * [ScoutDropTracker.onNotification]. A stray lost notification is counted but not flagged.
     */
    val linkLossSignificant: Boolean = false,
) {
    /**
     * "Some data missed": the board dropped records or raw frames, or the link lost a significant
     * share of notifications, since the capture started.
     */
    val dataMissed: Boolean get() = records > 0 || rawFrames > 0 || linkLossSignificant
}

/**
 * Works out new drops from the board's since-boot counters (protocol.md section 7: they only grow
 * until the board restarts). The first status after [reset] sets the baseline. The baseline is
 * kept across reconnects, so records the ring buffer dropped while the Scout was out of range
 * count too. A board restart (uptime going down, or a counter going down) counts from zero.
 *
 * Lost Records notifications only make "Some data missed" true when they are a real share of
 * the link: at least [MIN_LOST_NOTIFICATIONS] lost and more than [LOST_SHARE_PERCENT] percent of
 * the notifications expected in the last [LOSS_WINDOW_MS]. A BLE stack that gives up on one frame
 * now and then (seen on hardware: 1 lost in 194, with no board drops) is counted but not flagged.
 * Once flagged it stays flagged for the capture, like the board's own drops.
 *
 * Used on the Scout thread only.
 */
class ScoutDropTracker {
    private var baseline: BoardStatus? = null
    private var drops = ScoutDrops()

    /** One entry per received notification in the window: when, and how many were lost before it. */
    private val window = ArrayDeque<LongArray>()
    private var windowLost = 0L

    /** The drops since the last [reset]. */
    val current: ScoutDrops get() = drops

    /** Starts counting from zero, for a new capture. */
    fun reset() {
        baseline = null
        drops = ScoutDrops()
        window.clear()
        windowLost = 0
    }

    /** @return True when [status] raised a drop counter. */
    fun onStatus(status: BoardStatus): Boolean {
        val previous = baseline
        baseline = status
        if (previous == null) return false
        val restarted = status.uptimeMs < previous.uptimeMs
        val before = drops
        drops = drops.copy(
            records = drops.records + delta(previous.droppedRecords, status.droppedRecords, restarted),
            rawFrames = drops.rawFrames + delta(previous.droppedRawFrames, status.droppedRawFrames, restarted),
            rxFrames = drops.rxFrames + delta(previous.droppedRxFrames, status.droppedRxFrames, restarted),
        )
        return drops != before
    }

    /**
     * Notes one received Records notification at [nowMs] (monotonic) with the [missed] ones the
     * link lost just before it.
     */
    fun onNotification(missed: Int, nowMs: Long) {
        window.addLast(longArrayOf(nowMs, missed.toLong()))
        windowLost += missed
        while (window.isNotEmpty() && nowMs - window.first()[0] > LOSS_WINDOW_MS) {
            windowLost -= window.removeFirst()[1]
        }
        if (missed <= 0) return
        val expected = window.size + windowLost
        val significant = drops.linkLossSignificant ||
            (windowLost >= MIN_LOST_NOTIFICATIONS && windowLost * PERCENT > expected * LOST_SHARE_PERCENT)
        drops = drops.copy(missedNotifications = drops.missedNotifications + missed, linkLossSignificant = significant)
    }

    companion object {
        /** The span lost notifications are judged over. */
        const val LOSS_WINDOW_MS = 60_000L

        /** Fewer lost notifications than this in the window never count as data missed. */
        const val MIN_LOST_NOTIFICATIONS = 2L

        /** The share of expected notifications, in percent, the window's losses must exceed. */
        const val LOST_SHARE_PERCENT = 1L

        private const val PERCENT = 100L
    }

    private fun delta(previous: Int, current: Int, restarted: Boolean): Long {
        val before = Integer.toUnsignedLong(previous)
        val now = Integer.toUnsignedLong(current)
        return if (restarted || now < before) now else now - before
    }
}
