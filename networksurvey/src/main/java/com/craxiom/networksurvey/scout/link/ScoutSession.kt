package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.ConfigureResult
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.HelloResult
import com.craxiom.networksurvey.board.v1.OtaAck
import com.craxiom.networksurvey.board.v1.ResultCode
import com.craxiom.networksurvey.scout.model.ScoutId
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import kotlin.random.Random

/** Receives a [ScoutSession]'s decoded traffic, on the Scout thread. */
interface ScoutSessionListener {
    fun onRecords(batch: ReceivedRecordBatch)
    fun onRawFrames(frames: ReceivedRawFrames)
    fun onStatus(status: BoardStatus)
    fun onOtaAck(ack: OtaAck)

    /** The link ended; the session is closed. */
    fun onLinkLost(failure: ScoutLinkFailure)
}

/** What a session learned while setting up the link. */
data class ScoutLinkInfo(
    val info: BoardInfo,
    /** The negotiated ATT MTU. */
    val mtu: Int,
    /** The board's Hello answer, null when the Info read already showed another protocol major. */
    val hello: HelloResult?,
    val compatibility: ScoutCompatibility,
) {
    /** The firmware version from Hello, or from Info before Hello. */
    val firmwareVersion: String get() = hello?.firmwareVersion?.takeIf { it.isNotEmpty() } ?: info.firmwareVersion
}

/** Wall clock and monotonic time for the session, so tests can drive both. */
interface ScoutClock {
    /** Unix epoch milliseconds. */
    fun wallMs(): Long

    /** Monotonic milliseconds, for intervals. */
    fun elapsedMs(): Long

    companion object {
        /** The system clocks. */
        val SYSTEM: ScoutClock = SystemScoutClock
    }
}

/** [ScoutClock] on the system clocks. */
private object SystemScoutClock : ScoutClock {
    private const val NANOS_PER_MILLI = 1_000_000L

    override fun wallMs(): Long = System.currentTimeMillis()
    override fun elapsedMs(): Long = System.nanoTime() / NANOS_PER_MILLI
}

/**
 * One connection to one Scout on top of a [ScoutTransport]: the Android connection order (MTU,
 * connection priority, Info and the protocol check, explicit bonding, subscriptions, Hello,
 * SetTime), then Configure and Start or Stop as the capture changes. Decodes every notification
 * through [ScoutNotificationDecoder] and hands the result to the listener.
 *
 * Used on the Scout thread only. Raw and OTA are subscribed only when asked for: raw frames cost
 * about 25 KB/s of link budget.
 */
class ScoutSession(
    private val transport: ScoutTransport,
    private val appVersion: String,
    private val listener: ScoutSessionListener,
    private val clock: ScoutClock = ScoutClock.SYSTEM,
) {
    private val decoder = ScoutNotificationDecoder()
    private val control = ScoutControlChannel { transport.writeControl(it) }
    private var closed = false

    /** What the link set up, null until [establish] finished. */
    var linkInfo: ScoutLinkInfo? = null
        private set

    /** The capture the board is running, null while it is stopped. */
    var activeCapture: ScoutCapture? = null
        private set

    /** The board's answer to the last Configure. */
    var configureResult: ConfigureResult? = null
        private set

    private var rawSubscribed = false
    private var otaSubscribed = false

    /**
     * True once this link's Stop was answered. A new link can't tell: a board that lost its phone
     * keeps surveying, and the 5 minute limit no longer applies once a phone is back.
     */
    private var knownStopped = false

    private val transportListener = object : ScoutTransportListener {
        override fun onNotification(characteristic: ScoutCharacteristic, value: ByteArray, receivedAtMs: Long) {
            handleNotification(characteristic, value, receivedAtMs)
        }

        override fun onLinkLost(failure: ScoutLinkFailure) {
            if (closed) return
            closed = true
            control.fail(ScoutLinkException(failure, "link lost"))
            listener.onLinkLost(failure)
        }
    }

    /**
     * Connects and sets the link up. A board speaking another protocol major is still bonded and
     * kept connected with only Control subscribed, so its firmware can be updated; the result then
     * says so and no capture may be applied.
     *
     * @param allowPairing True for a pairing the user started: a missing bond is created and
     * Android shows its prompt. Reconnects pass false, so they never prompt.
     * @throws ScoutLinkException When any step fails; the transport is closed then.
     */
    suspend fun establish(address: String, allowPairing: Boolean): ScoutLinkInfo {
        try {
            val mtu = transport.open(address, transportListener)
            if (mtu < ScoutGatt.MIN_RECORDS_MTU) Timber.w("Scout MTU is %d, below what Records need", mtu)
            val info = readInfo()
            Timber.i(
                "Scout Info: %s (%s) firmware %s protocol %d.%d model %s chip %s bands %s, MTU %d",
                info.shortId, ScoutId.fromBtAddress(info.btAddress.toByteArray()), info.firmwareVersion, info.protocolMajor, info.protocolMinor, info.boardModel, info.chip,
                info.bandsList, mtu,
            )
            val infoCompatibility = ScoutCompatibility.of(info.protocolMajor)
            transport.ensureBonded(allowPairing)
            subscribe(ScoutCharacteristic.CONTROL)
            if (infoCompatibility != ScoutCompatibility.COMPATIBLE) {
                return ScoutLinkInfo(info, mtu, hello = null, infoCompatibility).also { linkInfo = it }
            }
            subscribe(ScoutCharacteristic.STATUS)
            subscribe(ScoutCharacteristic.RECORDS)
            val hello = sayHello()
            val compatibility = if (hello.compatible) ScoutCompatibility.COMPATIBLE else ScoutCompatibility.of(hello.protocolMajor)
            if (compatibility == ScoutCompatibility.COMPATIBLE) syncTime()
            return ScoutLinkInfo(info, mtu, hello, compatibility).also { linkInfo = it }
        } catch (e: ScoutLinkException) {
            close()
            throw e
        } catch (e: CancellationException) {
            close()
            throw e
        } catch (e: RuntimeException) {
            close()
            throw ScoutLinkException(ScoutLinkFailure.MALFORMED, "setup failed", e)
        }
    }

    /**
     * @return True when [capture] needs nothing sent: it is the running capture, or it is null and
     * the board is known to be stopped.
     */
    fun isApplied(capture: ScoutCapture?): Boolean = capture == activeCapture && (capture != null || knownStopped)

    /**
     * Applies [capture]: Configure (with the Raw subscription matching its raw frames choice) and
     * Start, or Stop when it is null. Nothing is sent when [isApplied].
     */
    suspend fun applyCapture(capture: ScoutCapture?) {
        check(linkInfo?.compatibility == ScoutCompatibility.COMPATIBLE) { "The Scout is not compatible" }
        if (isApplied(capture)) return
        if (capture == null) {
            control.sendExpectingOk(ScoutProtocol.stop())
            activeCapture = null
            knownStopped = true
            setRawSubscribed(false)
            return
        }
        setRawSubscribed(capture.rawFrames)
        val response = control.sendExpectingOk(capture.toConfigureRequest())
        configureResult = if (response.hasConfigure()) response.configure else null
        Timber.i("Scout configured: role %s channels %s", configureResult?.role, configureResult?.channelsList)
        if (activeCapture == null) control.sendExpectingOk(ScoutProtocol.start())
        activeCapture = capture
        knownStopped = false
    }

    /** Subscribes to OTA acks, for a firmware update. */
    suspend fun enableOta() {
        if (otaSubscribed) return
        subscribe(ScoutCharacteristic.OTA)
        otaSubscribed = true
    }

    /** Sends any other control request, for example `EnterPairingMode` or the OTA commands. */
    suspend fun send(
        request: ControlRequest.Builder,
        timeoutMs: Long = ScoutProtocol.timeoutFor(request),
    ): ControlResponse = control.send(request, timeoutMs)

    /** Writes one OTA chunk without response (after [enableOta] and an accepted `OtaBegin`). */
    suspend fun writeOta(chunk: ByteArray) {
        if (closed) throw ScoutLinkException(ScoutLinkFailure.LINK_LOST, "session closed")
        transport.writeOta(chunk)
    }

    /** True once the link to the Scout came up, even when a later setup step failed. */
    val reachedScout: Boolean get() = transport.reachedScout

    /** False once the session was closed or the link was lost. */
    val isOpen: Boolean get() = !closed

    /** Closes the link without telling the listener. */
    fun close() {
        if (closed) return
        closed = true
        control.fail(ScoutLinkException(ScoutLinkFailure.CANCELED, "session closed"))
        transport.close()
    }

    private suspend fun readInfo(): BoardInfo = try {
        BoardInfo.parseFrom(transport.readInfo())
    } catch (e: InvalidProtocolBufferException) {
        throw ScoutLinkException(ScoutLinkFailure.MALFORMED, "Info did not decode", e)
    }

    private suspend fun subscribe(characteristic: ScoutCharacteristic) {
        // The board restarts the characteristic's sequence numbers on the CCCD write.
        decoder.reset(characteristic)
        transport.setNotify(characteristic, true)
    }

    private suspend fun setRawSubscribed(enabled: Boolean) {
        if (enabled == rawSubscribed) return
        if (enabled) subscribe(ScoutCharacteristic.RAW) else transport.setNotify(ScoutCharacteristic.RAW, false)
        rawSubscribed = enabled
    }

    private suspend fun sayHello(): HelloResult {
        val response = control.send(ScoutProtocol.hello(appVersion))
        val ok = response.result == ResultCode.RESULT_CODE_OK || response.result == ResultCode.RESULT_CODE_INCOMPATIBLE
        if (!ok || !response.hasHello()) {
            throw ScoutLinkException(ScoutLinkFailure.REJECTED, "Hello answered ${response.result}")
        }
        Timber.i(
            "Scout Hello: %s, board protocol %d.%d, firmware %s, compatible %b", response.result,
            response.hello.protocolMajor, response.hello.protocolMinor, response.hello.firmwareVersion, response.hello.compatible,
        )
        return response.hello
    }

    /** SetTime with the phone time, moved ahead by half a measured Ping round trip. */
    private suspend fun syncTime() {
        val started = clock.elapsedMs()
        val ping = runCatching { control.send(ScoutProtocol.ping(Random.nextInt(1, Int.MAX_VALUE))) }.getOrNull()
        val halfTrip = if (ping?.result == ResultCode.RESULT_CODE_OK) (clock.elapsedMs() - started) / 2 else 0L
        control.sendExpectingOk(ScoutProtocol.setTime(clock.wallMs() + halfTrip))
        Timber.i("Scout time set, half round trip %d ms", halfTrip)
    }

    private fun handleNotification(characteristic: ScoutCharacteristic, value: ByteArray, receivedAtMs: Long) {
        if (closed) return
        try {
            when (characteristic) {
                ScoutCharacteristic.RECORDS -> decoder.decodeRecords(value, receivedAtMs).forEach(listener::onRecords)
                ScoutCharacteristic.STATUS -> decoder.decodeStatus(value).forEach(listener::onStatus)
                ScoutCharacteristic.RAW -> listener.onRawFrames(decoder.decodeRaw(value, receivedAtMs))
                ScoutCharacteristic.OTA -> decoder.decodeOtaAcks(value).forEach(listener::onOtaAck)
                ScoutCharacteristic.CONTROL -> decoder.decodeControl(value).forEach { control.onResponse(it) }
                ScoutCharacteristic.INFO -> Unit
            }
        } catch (e: ScoutFramingException) {
            Timber.w(e, "Dropping a bad %s frame from the Scout", characteristic)
        } catch (e: InvalidProtocolBufferException) {
            Timber.w(e, "Dropping an undecodable %s message from the Scout", characteristic)
        }
    }
}
