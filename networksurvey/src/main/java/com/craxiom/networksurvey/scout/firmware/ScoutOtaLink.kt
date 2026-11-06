package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.HelloResult
import com.craxiom.networksurvey.board.v1.OtaAck
import com.craxiom.networksurvey.scout.link.ScoutProtocol

/**
 * One set-up link to the Scout, as the firmware installer uses it: Control requests, OTA writes
 * and the OTA acks of this link. Calls throw `ScoutLinkException` when the link ends.
 */
interface ScoutOtaLink {
    val info: BoardInfo

    /** The Hello answer from the link's setup, null when the board speaks another protocol major. */
    val hello: HelloResult?

    /** The most bytes one OTA write may carry: the ATT MTU minus the 3-byte header, at most 512. */
    val maxWriteBytes: Int

    /** False once the link ended. */
    val isOpen: Boolean

    /** The firmware the board runs, from Hello or Info. */
    val firmwareVersion: String get() = hello?.firmwareVersion?.takeIf { it.isNotEmpty() } ?: info.firmwareVersion

    /** Subscribes to OTA acks (once per link). */
    suspend fun enableOta()

    /** Forgets acks still queued from an earlier transfer on this link. */
    fun clearAcks()

    suspend fun send(request: ControlRequest.Builder, timeoutMs: Long = ScoutProtocol.timeoutFor(request)): ControlResponse

    /** Writes one OTA chunk (offset and image bytes) without response. */
    suspend fun writeOta(chunk: ByteArray)

    /** @return The next OTA ack, or null when none came within [timeoutMs]. Throws when the link ends. */
    suspend fun nextAck(timeoutMs: Long): OtaAck?

    /** @return True when the link ended within [timeoutMs]. */
    suspend fun awaitLost(timeoutMs: Long): Boolean
}

/**
 * What the firmware installer needs from the Scout manager while it holds the Scout: a set-up link
 * when there is one, and a way to drop it so the next one is fresh. The manager reconnects on its
 * own (with its backoff) while the update holds the Scout.
 */
interface ScoutOtaPort {
    /**
     * @return The set-up link, waiting up to [timeoutMs] for one; null when none came.
     * @throws FirmwareUpdateError.PairingLost When the link needs the user to pair again.
     * @throws FirmwareUpdateError.NotConnected When Bluetooth is off or the permission is missing.
     */
    suspend fun awaitLink(timeoutMs: Long): ScoutOtaLink?

    /** Ends the current link; the manager reconnects. */
    fun dropLink()
}

/** Where a firmware install is. */
sealed interface FirmwareInstallPhase {
    data object Connecting : FirmwareInstallPhase

    /** The image is going out; [sent] bytes are acknowledged. */
    data class Transferring(val sent: Int, val total: Int) : FirmwareInstallPhase {
        /** How much of the image was acknowledged, 0 to 1. */
        val fraction: Float get() = if (total > 0) sent.toFloat() / total else 0f

        /** [fraction] as a whole percentage. */
        val percent: Int get() = (fraction * PERCENT).toInt()

        private companion object {
            const val PERCENT = 100
        }
    }

    /** The link dropped during the transfer; reconnecting to resume. */
    data class Reconnecting(val attempt: Int) : FirmwareInstallPhase

    /** OtaEnd sent: the board checks the SHA-256 and the image. */
    data object Verifying : FirmwareInstallPhase

    /** The board restarts into the new image; waiting for it to come back. */
    data object Restarting : FirmwareInstallPhase

    /** Reconnected: Hello reports the running version. */
    data object Confirming : FirmwareInstallPhase
}

/**
 * A finished install.
 *
 * @property imageConfirmed The board confirmed the new image on this Hello.
 * @property compatible The new firmware speaks this app's protocol major.
 */
data class FirmwareInstallResult(
    val previousVersion: String,
    val newVersion: String,
    val imageConfirmed: Boolean,
    val compatible: Boolean,
)
