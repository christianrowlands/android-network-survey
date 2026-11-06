package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.ConfigureResult
import com.craxiom.networksurvey.board.v1.OtaAck
import com.craxiom.networksurvey.board.v1.Record
import com.craxiom.networksurvey.scout.link.ReceivedRawFrames
import com.craxiom.networksurvey.scout.link.ReceivedRecordBatch
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.link.ScoutClock
import com.craxiom.networksurvey.scout.link.ScoutCompatibility
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.link.ScoutLinkInfo
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutBattery
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.ScoutLinkDetails
import com.craxiom.networksurvey.scout.model.ScoutRawFrames
import com.craxiom.networksurvey.scout.model.ScoutRecordListener
import com.craxiom.networksurvey.scout.model.ScoutRecords
import com.craxiom.networksurvey.scout.model.WifiSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * What the Scout manager knows about the link, kept apart from the manager's demand and pairing
 * logic: the link phase and its connect grace, compatibility, the latest status and battery,
 * pairing problems, data missed and catch-up. It turns [ScoutLinkController] events into this
 * state and into [ScoutRecordListener] calls, and assembles [ScoutStateInputs].
 *
 * Used on the Scout thread only.
 */
internal class ScoutLinkState(private val clock: ScoutClock) {
    /** What the manager hears back. */
    sealed interface Event {
        /** A link finished setup. */
        data class Ready(val info: ScoutLinkInfo) : Event

        /** Something about the link changed that the state should show. */
        data object Changed : Event
    }

    private val catchUp = ScoutCatchUpTracker()
    private val drops = ScoutDropTracker()
    private val mutableDetails = MutableStateFlow(ScoutLinkDetails())
    private var connectingSinceMs: Long? = null
    private var compatibility: ScoutCompatibility? = null
    private var lastBatteryPercent: Int? = null

    /** Attempts in a row that reached the Scout and then failed to finish setting up. */
    private var failedAfterConnect = 0

    /** The board refused the wanted capture on this link and hasn't taken it since. */
    private var captureRejected = false

    /** Details for the Scout page. */
    val details: StateFlow<ScoutLinkDetails> = mutableDetails.asStateFlow()

    var phase: ScoutLinkPhase = ScoutLinkPhase.IDLE
        private set

    /** A problem only the user can fix; reconnects stay off while it is set. */
    var issue: ScoutIssue? = null
        private set

    /** A new capture starts: data missed and catch-up count from zero. */
    fun startCapture() {
        drops.reset()
        catchUp.reset()
        mutableDetails.value = mutableDetails.value.copy(drops = drops.current)
    }

    fun clearIssue() {
        issue = null
    }

    /** The Scout was removed: forget everything learned from it. */
    fun forgetScout() {
        issue = null
        compatibility = null
        lastBatteryPercent = null
        catchUp.reset()
        drops.reset()
        mutableDetails.value = ScoutLinkDetails()
    }

    /** @return The mapper inputs for the current link and the given surroundings. */
    fun inputs(
        scout: PairedScout?,
        source: WifiSource,
        hasPermission: Boolean,
        bluetoothOn: Boolean,
        capturing: Boolean,
    ): ScoutStateInputs {
        val details = mutableDetails.value
        return ScoutStateInputs(
            scout = scout,
            source = source,
            hasPermission = hasPermission,
            bluetoothOn = bluetoothOn,
            issue = issue,
            compatibility = compatibility,
            firmwareVersion = details.firmwareVersion,
            phase = phase,
            connectingSinceMs = connectingSinceMs,
            nowMs = clock.elapsedMs(),
            status = details.status,
            lastBatteryPercent = lastBatteryPercent,
            capturing = capturing,
            catchUp = catchUp.snapshot,
            dataMissed = drops.current.dataMissed,
            failedAfterConnect = failedAfterConnect,
            captureRejected = captureRejected,
        )
    }

    /**
     * @return The controller callbacks: they update this state, report to [onEvent] and hand
     * records to [listeners] stamped with the paired Scout's ID from [scoutId]. OTA acks go to
     * [otaAcks], for a firmware update.
     */
    fun events(
        onEvent: (Event) -> Unit,
        listeners: Collection<ScoutRecordListener>,
        scoutId: () -> ScoutId?,
        otaAcks: (OtaAck) -> Unit = {},
    ): ScoutLinkController.Events = object : ScoutLinkController.Events {
        override fun onPhase(phase: ScoutLinkPhase) {
            if (phase == this@ScoutLinkState.phase) return
            connectingSinceMs = if (phase == ScoutLinkPhase.CONNECTING) clock.elapsedMs() else null
            if (phase == ScoutLinkPhase.IDLE) {
                failedAfterConnect = 0
                captureRejected = false
                // A link the app closed reports no loss; its MTU and status must not outlive it
                mutableDetails.value = mutableDetails.value.copy(status = null, mtu = 0, capturing = false)
            }
            this@ScoutLinkState.phase = phase
            onEvent(Event.Changed)
        }

        override fun onReady(info: ScoutLinkInfo) {
            failedAfterConnect = 0
            captureRejected = false
            // Whatever this link is catching up on, it says so itself once a capture runs on it
            catchUp.reset()
            compatibility = info.compatibility
            mutableDetails.value = mutableDetails.value.copy(info = info.info, firmwareVersion = info.firmwareVersion, mtu = info.mtu)
            onEvent(Event.Ready(info))
        }

        override fun onLinkLost(failure: ScoutLinkFailure) {
            captureRejected = false
            mutableDetails.value = mutableDetails.value.copy(status = null, mtu = 0, capturing = false)
            onEvent(Event.Changed)
        }

        override fun onAttemptFailed(reachedScout: Boolean) {
            val count = if (reachedScout) failedAfterConnect + 1 else 0
            if (count == failedAfterConnect) return
            failedAfterConnect = count
            onEvent(Event.Changed)
        }

        override fun onNeedsUser(failure: ScoutLinkFailure) {
            issue = if (failure == ScoutLinkFailure.PAIRING_REJECTED) ScoutIssue.PAIRING_REFUSED else ScoutIssue.PAIRING_LOST
            onEvent(Event.Changed)
        }

        override fun onCaptureChanged(capture: ScoutCapture?, configured: ConfigureResult?) {
            captureRejected = false
            if (capture == null) catchUp.reset()
            mutableDetails.value = mutableDetails.value.copy(capturing = capture != null, configured = configured)
            onEvent(Event.Changed)
        }

        override fun onCaptureRejected() {
            captureRejected = true
            onEvent(Event.Changed)
        }

        override fun onRecords(batch: ReceivedRecordBatch) {
            drops.onNotification(batch.missedNotifications, clock.elapsedMs())
            // With no capture running nothing would ever end a catch-up (Stop's last batch can be late)
            val catchUpChanged = mutableDetails.value.capturing && catchUp.onBatch(batch)
            if (batch.missedNotifications > 0 || catchUpChanged) {
                mutableDetails.value = mutableDetails.value.copy(drops = drops.current)
                onEvent(Event.Changed)
            }
            val id = scoutId() ?: return
            val records = split(id, batch)
            listeners.forEach { listener -> deliver { listener.onScoutRecords(records) } }
        }

        override fun onRawFrames(frames: ReceivedRawFrames) {
            val id = scoutId() ?: return
            val raw = ScoutRawFrames(id, frames.receivedAtMs, frames.frames, frames.missedNotifications)
            listeners.forEach { listener -> deliver { listener.onScoutRawFrames(raw) } }
        }

        override fun onStatus(status: BoardStatus) {
            ScoutBattery.percent(status)?.let { lastBatteryPercent = it }
            catchUp.onRingFill(status.ringFillPercent)
            drops.onStatus(status)
            mutableDetails.value = mutableDetails.value.copy(status = status, drops = drops.current, lastHeardAtMs = clock.wallMs())
            onEvent(Event.Changed)
            val id = scoutId() ?: return
            listeners.forEach { listener -> deliver { listener.onScoutStatus(id, status) } }
        }

        override fun onOtaAck(ack: OtaAck) = otaAcks(ack)
    }

    /** Runs one listener call; a failing listener must not break the link or the others. */
    private inline fun deliver(call: () -> Unit) {
        try {
            call()
        } catch (e: RuntimeException) {
            Timber.e(e, "A Scout record listener failed")
        }
    }

    private fun split(id: ScoutId, received: ReceivedRecordBatch): ScoutRecords {
        val records = received.batch.recordsList
        return ScoutRecords(
            scoutId = id,
            receivedAtMs = received.receivedAtMs,
            timeSynced = received.batch.timeSynced,
            accessPoints = records.filter { it.kindCase == Record.KindCase.AP }.map { it.ap },
            clients = records.filter { it.kindCase == Record.KindCase.CLIENT }.map { it.client },
            probes = records.filter { it.kindCase == Record.KindCase.PROBE }.map { it.probe },
            missedNotifications = received.missedNotifications,
        )
    }
}
