package com.craxiom.networksurvey.scout.raw

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.ScoutRawFrames
import com.craxiom.networksurvey.scout.model.ScoutRecordListener
import timber.log.Timber

/**
 * The paired NS Scout's raw 802.11 frames. While it runs it holds a capture with raw frames on,
 * which is what makes the link subscribe to the Raw characteristic and set the raw fields of
 * `Configure` (the board's defaults: management frames only, its own snaplen and rate limit). Its
 * owner runs it only while Raw frames and Log files are both on and the Scout is the Wi-Fi source.
 *
 * Frames are stamped on the Scout thread ([RawFrameStamper], cheap) and handed to [onFrames], which
 * must pass them to its own writer thread: no file work happens on the Scout thread. Each Raw
 * notification the link lost is reported to [onLost] as one frame, the least it can have held.
 */
class ScoutRawSource @JvmOverloads constructor(
    private val manager: ScoutManager,
    private val onFrames: (List<StampedRawFrame>) -> Unit,
    private val onLost: (Int) -> Unit,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : ScoutRecordListener {
    private val stamper = RawFrameStamper()

    @Volatile
    var isRunning = false
        private set

    @Volatile
    private var paused = false

    fun start() {
        if (isRunning) return
        isRunning = true
        paused = false
        stamper.start(wallClock())
        manager.addRecordListener(this)
        manager.requestCapture(this, CAPTURE)
        Timber.i("NS Scout raw frames started")
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        manager.release(this)
        manager.removeRecordListener(this)
        Timber.i("NS Scout raw frames stopped")
    }

    /** Battery or backpressure pause: the board stops sending raw frames until [resume]. */
    fun pause() {
        if (!isRunning || paused) return
        paused = true
        manager.release(this)
    }

    fun resume() {
        if (!paused) return
        paused = false
        if (isRunning) manager.requestCapture(this, CAPTURE)
    }

    override fun onScoutStatus(scoutId: ScoutId, status: BoardStatus) = stamper.onStatus(scoutId, status, wallClock())

    override fun onScoutRawFrames(frames: ScoutRawFrames) {
        if (!isRunning || paused) return
        if (frames.missedNotifications > 0) onLost(frames.missedNotifications)
        val snaplen = manager.linkDetails.value.configured?.rawSnaplen ?: 0
        val stamped = stamper.stamp(frames, snaplen)
        if (stamped.isNotEmpty()) onFrames(stamped)
    }

    companion object {
        /** Raw frames only; access points, clients and probes come from their own sources' captures. */
        val CAPTURE = ScoutCapture(wifiAccessPoints = false, rawFrames = true)
    }
}
