package com.craxiom.networksurvey.scout.fake

import com.craxiom.networksurvey.board.v1.BoardState
import com.craxiom.networksurvey.board.v1.ConfigureResult
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.HelloResult
import com.craxiom.networksurvey.board.v1.PingResult
import com.craxiom.networksurvey.board.v1.RecordBatch
import com.craxiom.networksurvey.board.v1.ResultCode
import com.craxiom.networksurvey.scout.link.ScoutCharacteristic
import com.craxiom.networksurvey.scout.link.ScoutClock
import com.craxiom.networksurvey.scout.link.ScoutFraming
import com.craxiom.networksurvey.scout.link.ScoutLinkException
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.link.ScoutTransport
import com.craxiom.networksurvey.scout.link.ScoutTransportListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A debug-only [ScoutTransport] that behaves like a board and replays the firmware's wire
 * fixtures: Info from `board_info.bin`, Status about once a second, and while started the Records
 * fixture frames in a loop (plus raw frames when subscribed). Control requests get the answers a
 * board gives. Frames are re-sequenced per subscription, as a board does, and synced record times
 * are moved to now so the records read as live.
 *
 * Everything goes through the real framing, decoder, control channel and session, so tests and
 * development exercise the same path as hardware. Never in release builds (src/debug).
 */
class FakeScoutTransport(
    private val scope: CoroutineScope,
    private val fixtures: ScoutFixtures,
    private val behavior: Behavior = Behavior(),
    private val clock: ScoutClock = ScoutClock.SYSTEM,
    /** The phone's bond with the fake board, shared by every transport so reconnects find it. */
    private val bond: Bond = Bond(behavior.phoneBonded),
) : ScoutTransport {

    /** Whether the phone holds a bond with the fake board. */
    class Bond(@Volatile var bonded: Boolean)

    /** How the fake board behaves, for tests. */
    data class Behavior(
        /** The phone already holds a bond (a reconnect) instead of pairing first. */
        val phoneBonded: Boolean = false,
        /** The user taps Pair when Android asks. */
        val acceptPairing: Boolean = true,
        /** The protocol major the board reports in Info and Hello. */
        val protocolMajor: Int? = null,
        /** When set, [open] fails with this. */
        val openFailure: ScoutLinkFailure? = null,
        /** The negotiated MTU. */
        val mtu: Int = DEFAULT_MTU,
        /** Gap between Records frames while started. */
        val recordIntervalMs: Long = DEFAULT_RECORD_INTERVAL_MS,
        /** Gap between Status messages. */
        val statusIntervalMs: Long = DEFAULT_STATUS_INTERVAL_MS,
        /** Skip every Nth Records sequence number (0 never), to show up as missed notifications. */
        val skipEveryNthRecordsFrame: Int = 0,
        /** Control requests the board refuses, with the result it answers. */
        val refused: Map<ControlRequest.CommandCase, ResultCode> = emptyMap(),
    )

    private var listener: ScoutTransportListener? = null
    private var started = false
    private var closed = false
    private val subscribed = HashSet<ScoutCharacteristic>()
    private val sequences = HashMap<ScoutCharacteristic, Int>()
    private val jobs = ArrayList<Job>()

    /** Every control request the fake received, in order. */
    val requests = ArrayList<ControlRequest>()

    /** True once the link was closed, by either side. */
    val isClosed: Boolean get() = closed

    /** True while the fake board is surveying. */
    val isStarted: Boolean get() = started

    private val boardMajor: Int get() = behavior.protocolMajor ?: fixtures.boardInfo.protocolMajor

    override suspend fun open(address: String, listener: ScoutTransportListener): Int {
        behavior.openFailure?.let { throw ScoutLinkException(it, "fake open failure") }
        this.listener = listener
        return behavior.mtu
    }

    override suspend fun readInfo(): ByteArray = fixtures.boardInfo.toBuilder().setProtocolMajor(boardMajor).build().toByteArray()

    override suspend fun ensureBonded(allowPairing: Boolean) {
        if (bond.bonded) return
        if (!allowPairing) throw ScoutLinkException(ScoutLinkFailure.BOND_MISSING, "fake has no bond")
        if (!behavior.acceptPairing) throw ScoutLinkException(ScoutLinkFailure.PAIRING_REJECTED, "fake pairing refused")
        bond.bonded = true
    }

    override suspend fun setNotify(characteristic: ScoutCharacteristic, enabled: Boolean) {
        requireOpen()
        if (!bond.bonded) throw ScoutLinkException(ScoutLinkFailure.PAIRING_REJECTED, "fake is not bonded")
        if (enabled) {
            subscribed.add(characteristic)
            sequences[characteristic] = 0
            if (characteristic == ScoutCharacteristic.STATUS) launchLoop(behavior.statusIntervalMs) { sendStatus() }
        } else {
            subscribed.remove(characteristic)
        }
    }

    override suspend fun writeControl(request: ByteArray) {
        requireOpen()
        val parsed = ControlRequest.parseFrom(request)
        requests.add(parsed)
        val response = answer(parsed)
        scope.launch {
            delay(RESPONSE_DELAY_MS)
            notify(ScoutCharacteristic.CONTROL, listOf(response.toByteArray()))
        }
    }

    /** The fake board has no OTA receiver; firmware updates are tested with their own fake link. */
    override suspend fun writeOta(chunk: ByteArray) = Unit

    override fun close() {
        closed = true
        started = false
        jobs.forEach { it.cancel() }
        jobs.clear()
        listener = null
    }

    /** Ends the link as a board walking out of range would. */
    fun dropLink(failure: ScoutLinkFailure = ScoutLinkFailure.LINK_LOST) {
        val owner = listener ?: return
        close()
        owner.onLinkLost(failure)
    }

    private fun answer(request: ControlRequest): ControlResponse {
        val response = ControlResponse.newBuilder().setRequestId(request.requestId).setResult(ResultCode.RESULT_CODE_OK)
        behavior.refused[request.commandCase]?.let { return response.setResult(it).build() }
        when (request.commandCase) {
            ControlRequest.CommandCase.HELLO -> {
                val compatible = request.hello.protocolMajor == boardMajor
                if (!compatible) response.result = ResultCode.RESULT_CODE_INCOMPATIBLE
                response.setHello(
                    HelloResult.newBuilder().setProtocolMajor(boardMajor).setCompatible(compatible)
                        .setFirmwareVersion(fixtures.boardInfo.firmwareVersion)
                )
            }

            ControlRequest.CommandCase.CONFIGURE -> response.setConfigure(
                ConfigureResult.newBuilder().addAllChannels(DEFAULT_CHANNELS).setRole(request.configure.role)
            )

            ControlRequest.CommandCase.START -> startCapture()
            ControlRequest.CommandCase.STOP -> started = false
            ControlRequest.CommandCase.PING -> response.setPing(
                PingResult.newBuilder().setNonce(request.ping.nonce).setBoardTimeMs(clock.wallMs()).setTimeSynced(true)
            )

            ControlRequest.CommandCase.SET_TIME, ControlRequest.CommandCase.ENTER_PAIRING_MODE,
            ControlRequest.CommandCase.FORGET_BONDS -> Unit

            else -> response.result = ResultCode.RESULT_CODE_UNSUPPORTED
        }
        return response.build()
    }

    private fun startCapture() {
        if (started) return
        started = true
        var frameIndex = 0
        launchLoop(behavior.recordIntervalMs) {
            if (!started) return@launchLoop
            val frame = fixtures.recordFrames[frameIndex % fixtures.recordFrames.size]
            frameIndex++
            notify(ScoutCharacteristic.RECORDS, frame.map(::restamp))
            notify(ScoutCharacteristic.RAW, listOf(fixtures.rawFrame))
        }
    }

    private fun sendStatus() {
        val state = if (started) BoardState.BOARD_STATE_SURVEYING else BoardState.BOARD_STATE_IDLE
        val status = fixtures.boardStatus.toBuilder().setBoardTimeMs(clock.wallMs()).setState(state).setMtu(behavior.mtu)
        notify(ScoutCharacteristic.STATUS, listOf(status.build().toByteArray()))
    }

    /** Moves the synced record times of one encoded `RecordBatch` to now. */
    private fun restamp(message: ByteArray): ByteArray {
        val batch = RecordBatch.parseFrom(message)
        if (!batch.timeSynced) return message
        val now = clock.wallMs()
        val out = batch.toBuilder()
        out.recordsBuilderList.forEach { record ->
            when {
                record.hasAp() -> record.apBuilder.boardTimeMs = now
                record.hasClient() -> record.clientBuilder.boardTimeMs = now
                record.hasProbe() -> record.probeBuilder.boardTimeMs = now
                record.hasBle() -> record.bleBuilder.boardTimeMs = now
                record.hasGps() -> record.gpsBuilder.boardTimeMs = now
            }
        }
        return out.build().toByteArray()
    }

    private fun notify(characteristic: ScoutCharacteristic, messages: List<ByteArray>) {
        val owner = listener ?: return
        if (closed || characteristic !in subscribed) return
        var sequence = sequences.getOrDefault(characteristic, 0)
        val skip = behavior.skipEveryNthRecordsFrame
        if (characteristic == ScoutCharacteristic.RECORDS && skip > 0 && sequence > 0 && sequence % skip == 0) sequence++
        sequences[characteristic] = sequence + 1
        owner.onNotification(characteristic, ScoutFraming.encode(sequence, messages), clock.wallMs())
    }

    private fun launchLoop(intervalMs: Long, tick: () -> Unit) {
        jobs.add(scope.launch {
            while (isActive && !closed) {
                delay(intervalMs)
                tick()
            }
        })
    }

    private fun requireOpen() {
        if (closed || listener == null) throw ScoutLinkException(ScoutLinkFailure.LINK_LOST, "fake is not connected")
    }

    companion object {
        const val DEFAULT_MTU = 517
        const val DEFAULT_RECORD_INTERVAL_MS = 250L
        const val DEFAULT_STATUS_INTERVAL_MS = 1_000L
        const val RESPONSE_DELAY_MS = 20L

        /** What a C5 hops when Configure lists no channels (a subset, enough for the fake). */
        val DEFAULT_CHANNELS = listOf(1, 6, 11, 36, 40, 44, 48, 149, 153, 157, 161)
    }
}
