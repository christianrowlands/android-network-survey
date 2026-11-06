package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ForgetBonds
import com.craxiom.networksurvey.board.v1.Hello
import com.craxiom.networksurvey.board.v1.OtaAbort
import com.craxiom.networksurvey.board.v1.OtaBegin
import com.craxiom.networksurvey.board.v1.OtaEnd
import com.craxiom.networksurvey.board.v1.Ping
import com.craxiom.networksurvey.board.v1.ProtocolVersion
import com.craxiom.networksurvey.board.v1.SetTime
import com.craxiom.networksurvey.board.v1.Start
import com.craxiom.networksurvey.board.v1.Stop
import com.google.protobuf.ByteString
import java.nio.charset.StandardCharsets

/** How a board's protocol major version compares with this app's (protocol.md section 11). */
enum class ScoutCompatibility {
    /** Same major: the board works with this app. */
    COMPATIBLE,

    /** The board speaks an older major: it needs a firmware update. */
    NEEDS_FIRMWARE_UPDATE,

    /** The board speaks a newer major: Network Survey needs an update. */
    NEEDS_APP_UPDATE;

    companion object {
        /** @return How a board speaking [boardMajor] fits this app. */
        @JvmStatic
        fun of(boardMajor: Int): ScoutCompatibility = when {
            boardMajor == ScoutProtocol.APP_PROTOCOL_MAJOR -> COMPATIBLE
            boardMajor < ScoutProtocol.APP_PROTOCOL_MAJOR -> NEEDS_FIRMWARE_UPDATE
            else -> NEEDS_APP_UPDATE
        }
    }
}

/**
 * The app's side of the Scout control protocol: its protocol version, the control timeouts and
 * builders for the requests that carry no survey settings. Request IDs are set by
 * [ScoutControlChannel] when a request is sent.
 */
object ScoutProtocol {
    /** The protocol major version this app speaks, from the generated proto. */
    const val APP_PROTOCOL_MAJOR: Int = ProtocolVersion.PROTOCOL_VERSION_MAJOR_VALUE

    /** The protocol minor version this app speaks, from the generated proto. */
    const val APP_PROTOCOL_MINOR: Int = ProtocolVersion.PROTOCOL_VERSION_MINOR_VALUE

    /** How long to wait for a control response (protocol.md section 6). */
    const val CONTROL_TIMEOUT_MS = 5_000L

    /** `OtaEnd` reads the whole image back before it answers. */
    const val OTA_END_TIMEOUT_MS = 30_000L

    /** @return The response timeout for [request]: 30 s for `OtaEnd`, 5 s for everything else. */
    @JvmStatic
    fun timeoutFor(request: ControlRequest.Builder): Long =
        if (request.commandCase == ControlRequest.CommandCase.OTA_END) OTA_END_TIMEOUT_MS else CONTROL_TIMEOUT_MS

    /** @return A Hello carrying this app's protocol version and [appVersion] cut to the board's limit. */
    @JvmStatic
    fun hello(appVersion: String): ControlRequest.Builder = ControlRequest.newBuilder().setHello(
        Hello.newBuilder()
            .setProtocolMajor(APP_PROTOCOL_MAJOR)
            .setProtocolMinor(APP_PROTOCOL_MINOR)
            .setAppVersion(truncateUtf8(appVersion, ScoutGatt.APP_VERSION_MAX_BYTES))
    )

    /** @return A SetTime with the phone time [unixTimeMs]. */
    @JvmStatic
    fun setTime(unixTimeMs: Long): ControlRequest.Builder =
        ControlRequest.newBuilder().setSetTime(SetTime.newBuilder().setUnixTimeMs(unixTimeMs))

    /** @return A Ping with [nonce], used to measure the round trip before SetTime. */
    @JvmStatic
    fun ping(nonce: Int): ControlRequest.Builder = ControlRequest.newBuilder().setPing(Ping.newBuilder().setNonce(nonce))

    /** @return A Start request. */
    @JvmStatic
    fun start(): ControlRequest.Builder = ControlRequest.newBuilder().setStart(Start.getDefaultInstance())

    /** @return A Stop request. */
    @JvmStatic
    fun stop(): ControlRequest.Builder = ControlRequest.newBuilder().setStop(Stop.getDefaultInstance())

    /** @return A ForgetBonds request: the board deletes every bond and disconnects. */
    @JvmStatic
    fun forgetBonds(): ControlRequest.Builder = ControlRequest.newBuilder().setForgetBonds(ForgetBonds.getDefaultInstance())

    /** @return An OtaBegin for an image of [imageSize] bytes with SHA-256 [sha256] (protocol.md section 10). */
    @JvmStatic
    fun otaBegin(imageSize: Int, sha256: ByteArray): ControlRequest.Builder = ControlRequest.newBuilder()
        .setOtaBegin(OtaBegin.newBuilder().setImageSize(imageSize).setSha256(ByteString.copyFrom(sha256)))

    /** @return An OtaEnd: the board checks the image and restarts into it. */
    @JvmStatic
    fun otaEnd(): ControlRequest.Builder = ControlRequest.newBuilder().setOtaEnd(OtaEnd.getDefaultInstance())

    /** @return An OtaAbort: the board discards a transfer and returns to idle. */
    @JvmStatic
    fun otaAbort(): ControlRequest.Builder = ControlRequest.newBuilder().setOtaAbort(OtaAbort.getDefaultInstance())

    /**
     * @return [value] cut to at most [maxBytes] UTF-8 bytes, never in the middle of a character, so
     * the board never answers `INVALID_ARGUMENT` for an over-long string.
     */
    @JvmStatic
    fun truncateUtf8(value: String, maxBytes: Int): String {
        if (value.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return value
        val out = StringBuilder()
        var used = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val chars = Character.charCount(codePoint)
            val bytes = value.substring(index, index + chars).toByteArray(StandardCharsets.UTF_8).size
            if (used + bytes > maxBytes) break
            out.appendCodePoint(codePoint)
            used += bytes
            index += chars
        }
        return out.toString()
    }
}
