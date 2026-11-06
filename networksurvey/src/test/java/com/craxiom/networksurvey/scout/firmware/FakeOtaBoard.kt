package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.HelloResult
import com.craxiom.networksurvey.board.v1.OtaAck
import com.craxiom.networksurvey.board.v1.OtaAckStatus
import com.craxiom.networksurvey.board.v1.OtaBeginResult
import com.craxiom.networksurvey.board.v1.ResultCode
import com.craxiom.networksurvey.scout.link.ScoutLinkException
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/**
 * A Scout with a scriptable OTA receiver that follows protocol.md section 10 the way the firmware
 * does (a port of the iOS app's `MockOtaBoard`): OtaBegin resumes the same image, acks every
 * window/2 bytes and at the end, OUT_OF_ORDER once per gap, a resend ending at `next_offset` gets
 * an OK ack, one running past it is trimmed, other resends get an OK ack at most once a second,
 * OtaEnd checks the SHA-256 and restarts the board a second later. Runs on the test's virtual time.
 *
 * @param imageVersion The version the image installs, reported after a successful restart.
 */
class FakeOtaBoard(
    private val scope: TestScope,
    var version: String = "0.1.0",
    private val imageVersion: String = "0.2.0",
    private val maxChunk: Int = 240,
    private val window: Int = 2048,
    private val mtu: Int = 247,
) : ScoutOtaPort {
    /** Faults to inject; write and ack numbers are 1-based over the whole test. */
    class Faults {
        val dropWrites = mutableSetOf<Int>()
        val dropAcks = mutableSetOf<Int>()
        val disconnectOnWrites = mutableSetOf<Int>()
        var rebootOnDisconnect = false
        var failedConnectsAfterDrop = 0
        var corruptOffset: Int? = null
        var writeFailedAtOffset: Int? = null
        var rollBack = false
        var staysOffAfterRestart = false
        var silentOtaEnd = false
        var incompatibleAfterRestart = false
        var unconfirmedHellos = 0
        var forgetsConfirmation = false
    }

    val faults = Faults()

    /** The model the next link reports. */
    var model: BoardModel = BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1
    var writes = 0
    var acks = 0
    var aborts = 0
    var hellos = 0
    val begins = mutableListOf<Int>()
    val commands = mutableListOf<String>()

    /** The most bytes ever written past the last ack the board sent. */
    var maxAhead = 0
    var installed: ByteArray? = null

    private var current: Link? = null
    private var received = ByteArrayOutputStream()
    private var otaSize = 0
    private var otaSha = ByteArray(0)
    private var active = false
    private var lastAck = 0
    private var outOfOrderSent = false
    private var notActiveSent = false
    private var lastSkipAckMs = Long.MIN_VALUE / 2
    private var failedConnectsLeft = 0
    private var justUpdated = false
    private var installedOnce = false
    private var restarted = false

    private val compatible: Boolean get() = !(faults.incompatibleAfterRestart && installedOnce)

    override suspend fun awaitLink(timeoutMs: Long): ScoutOtaLink? = withTimeoutOrNull(timeoutMs) { connect() }

    private suspend fun connect(): Link {
        while (true) {
            current?.takeIf { it.isOpen }?.let { return it }
            if (faults.staysOffAfterRestart && restarted) awaitCancellation()
            delay(RECONNECT_MS)
            if (failedConnectsLeft > 0) {
                failedConnectsLeft--
                continue
            }
            current = Link(setupHello())
        }
    }

    override fun dropLink() = drop(reboot = false)

    /** Connects at once, as when the update screen opens with the Scout already linked. */
    fun connectNow() {
        current = Link(setupHello())
    }

    private fun drop(reboot: Boolean) {
        current?.lost?.complete(Unit)
        current = null
        failedConnectsLeft = faults.failedConnectsAfterDrop
        if (reboot) resetTransfer()
    }

    private fun resetTransfer() {
        received = ByteArrayOutputStream()
        active = false
        lastAck = 0
    }

    private fun info(): BoardInfo = FirmwareTestData.info(version = version, model = model, major = if (compatible) 1 else 2)

    /** The link's setup Hello, as the session sends it; none for another protocol major. */
    private fun setupHello(): HelloResult? = if (compatible) hello() else null

    private fun hello(): HelloResult {
        hellos++
        commands += "hello"
        if (justUpdated && faults.forgetsConfirmation) justUpdated = false
        val confirmed = if (justUpdated && faults.unconfirmedHellos > 0) {
            faults.unconfirmedHellos--
            false
        } else {
            (justUpdated && compatible).also { if (compatible) justUpdated = false }
        }
        return HelloResult.newBuilder()
            .setProtocolMajor(if (compatible) 1 else 2)
            .setCompatible(compatible)
            .setFirmwareVersion(version)
            .setOtaImageConfirmed(confirmed)
            .build()
    }

    private fun restartSoon() {
        scope.launch {
            delay(RESTART_MS)
            drop(reboot = true)
            if (!faults.rollBack) version = imageVersion
            justUpdated = !faults.rollBack
            installedOnce = true
            restarted = true
        }
    }

    private fun handle(link: Link, request: ControlRequest.Builder): ControlResponse {
        val response = ControlResponse.newBuilder().setRequestId(request.requestId).setResult(ResultCode.RESULT_CODE_OK)
        when (request.commandCase) {
            ControlRequest.CommandCase.HELLO -> {
                val hello = hello()
                response.setHello(hello).setResult(if (hello.compatible) ResultCode.RESULT_CODE_OK else ResultCode.RESULT_CODE_INCOMPATIBLE)
            }

            ControlRequest.CommandCase.OTA_BEGIN -> {
                commands += "begin"
                val begin = request.otaBegin
                if (!(active && otaSize == begin.imageSize && otaSha.contentEquals(begin.sha256.toByteArray()))) resetTransfer()
                otaSize = begin.imageSize
                otaSha = begin.sha256.toByteArray()
                active = true
                outOfOrderSent = false
                notActiveSent = false
                begins += received.size()
                response.setOtaBegin(
                    OtaBeginResult.newBuilder().setMaxChunkSize(maxChunk).setWindowBytes(window).setStartOffset(received.size()),
                )
            }

            ControlRequest.CommandCase.OTA_END -> {
                commands += "end"
                if (!active || received.size() != otaSize) return response.setResult(ResultCode.RESULT_CODE_INVALID_STATE).build()
                active = false
                if (!FirmwarePackage.sha256(received.toByteArray()).contentEquals(otaSha)) {
                    resetTransfer()
                    return response.setResult(ResultCode.RESULT_CODE_OTA_VERIFY_FAILED).build()
                }
                installed = received.toByteArray()
                restartSoon()
                if (faults.silentOtaEnd) throw ScoutLinkException(ScoutLinkFailure.TIMEOUT, "no answer to OTA_END")
            }

            ControlRequest.CommandCase.OTA_ABORT -> {
                commands += "abort"
                aborts++
                resetTransfer()
            }

            else -> response.setResult(ResultCode.RESULT_CODE_UNSUPPORTED)
        }
        if (!link.isOpen) throw ScoutLinkException(ScoutLinkFailure.LINK_LOST, "link lost")
        return response.build()
    }

    private fun write(link: Link, chunk: ByteArray) {
        writes++
        if (writes in faults.disconnectOnWrites) {
            drop(reboot = faults.rebootOnDisconnect)
            throw ScoutLinkException(ScoutLinkFailure.LINK_LOST, "link lost")
        }
        if (writes in faults.dropWrites) return
        if (!active) {
            if (!notActiveSent) ack(link, OtaAckStatus.OTA_ACK_STATUS_NOT_ACTIVE)
            notActiveSent = true
            return
        }
        val offset = (0 until 4).fold(0) { value, i -> value or ((chunk[i].toInt() and 0xFF) shl (8 * i)) }
        val data = chunk.copyOfRange(4, chunk.size)
        val next = received.size()
        maxAhead = maxOf(maxAhead, offset + data.size - lastAck)
        when {
            offset == next -> accept(link, offset, data)
            offset > next -> if (!outOfOrderSent) {
                outOfOrderSent = true
                ack(link, OtaAckStatus.OTA_ACK_STATUS_OUT_OF_ORDER)
            }

            offset + data.size == next -> ack(link, OtaAckStatus.OTA_ACK_STATUS_OK)
            offset + data.size > next -> accept(link, next, data.copyOfRange(next - offset, data.size))
            scope.currentTime - lastSkipAckMs >= SKIP_ACK_INTERVAL_MS -> {
                lastSkipAckMs = scope.currentTime
                ack(link, OtaAckStatus.OTA_ACK_STATUS_OK)
            }
        }
    }

    private fun accept(link: Link, offset: Int, data: ByteArray) {
        if (offset + data.size > otaSize) {
            active = false
            return ack(link, OtaAckStatus.OTA_ACK_STATUS_OVERFLOW)
        }
        faults.writeFailedAtOffset?.let {
            if (it in offset until offset + data.size) {
                active = false
                return ack(link, OtaAckStatus.OTA_ACK_STATUS_WRITE_FAILED)
            }
        }
        faults.corruptOffset?.let { if (it in offset until offset + data.size) data[it - offset] = data[it - offset].inc() }
        received.write(data)
        outOfOrderSent = false
        if (received.size() - lastAck >= window / 2 || received.size() == otaSize) ack(link, OtaAckStatus.OTA_ACK_STATUS_OK)
    }

    private fun ack(link: Link, status: OtaAckStatus) {
        acks++
        if (status == OtaAckStatus.OTA_ACK_STATUS_OK) lastAck = received.size()
        if (acks in faults.dropAcks) return
        link.acks.trySend(OtaAck.newBuilder().setNextOffset(received.size()).setStatus(status).build())
    }

    /** One connection to the fake board. */
    inner class Link(override val hello: HelloResult?) : ScoutOtaLink {
        val acks = Channel<OtaAck>(Channel.UNLIMITED)
        val lost = CompletableDeferred<Unit>()

        override val info: BoardInfo = info()
        override val maxWriteBytes: Int = mtu - 3
        override val isOpen: Boolean get() = !lost.isCompleted

        override suspend fun enableOta() = Unit

        override fun clearAcks() {
            while (acks.tryReceive().isSuccess) Unit
        }

        override suspend fun send(request: ControlRequest.Builder, timeoutMs: Long): ControlResponse {
            if (!isOpen) throw ScoutLinkException(ScoutLinkFailure.LINK_LOST, "not connected")
            return handle(this, request)
        }

        override suspend fun writeOta(chunk: ByteArray) {
            // Air time, so a transfer takes virtual time and can be canceled halfway
            delay(WRITE_MS)
            if (!isOpen) throw ScoutLinkException(ScoutLinkFailure.LINK_LOST, "not connected")
            write(this, chunk)
        }

        override suspend fun nextAck(timeoutMs: Long): OtaAck? {
            acks.tryReceive().getOrNull()?.let { return it }
            if (!isOpen) throw ScoutLinkException(ScoutLinkFailure.LINK_LOST, "link lost")
            return withTimeoutOrNull(timeoutMs) {
                select {
                    acks.onReceive { it }
                    lost.onAwait { throw ScoutLinkException(ScoutLinkFailure.LINK_LOST, "link lost") }
                }
            }
        }

        override suspend fun awaitLost(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) { lost.await() } != null
    }

    private companion object {
        const val RECONNECT_MS = 500L
        const val WRITE_MS = 2L
        const val RESTART_MS = 1_000L
        const val SKIP_ACK_INTERVAL_MS = 1_000L
    }
}
