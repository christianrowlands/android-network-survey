package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.HelloResult
import com.craxiom.networksurvey.board.v1.OtaAckStatus
import com.craxiom.networksurvey.board.v1.OtaBeginResult
import com.craxiom.networksurvey.board.v1.ResultCode
import com.craxiom.networksurvey.scout.link.ScoutLinkException
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.link.ScoutProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Installs a verified [FirmwarePackage] on the Scout (protocol.md section 10): OtaBegin, the chunk
 * transfer ([OtaChunkSender]), OtaEnd, then waits for the restart, reconnects and checks the
 * version, so a board that went back to its old image is reported as a rollback.
 *
 * A dropped link, a stalled transfer or a board that lost the transfer state is resumed by
 * reconnecting and sending the same OtaBegin again; the board answers with the offset to continue
 * from. Canceling the calling coroutine before OtaEnd sends OtaAbort. A port of the iOS app's
 * `FirmwareInstaller`.
 */
class FirmwareInstaller(
    private val port: ScoutOtaPort,
    private val pkg: FirmwarePackage,
    private val appVersion: String,
    private val timing: Timing = Timing(),
) {
    /** How long each step may take. Tests shorten them. */
    data class Timing(
        val connectAttemptMs: Long = 15_000L,
        /** How long to try to reconnect after one interruption. */
        val reconnectBudgetMs: Long = 60_000L,
        /** How long to wait for the link to drop after OtaEnd before forcing a new one. */
        val restartWaitMs: Long = 15_000L,
        /** How long the board may take to restart and reconnect. */
        val returnBudgetMs: Long = 90_000L,
        val ackTimeoutMs: Long = OtaChunkSender.DEFAULT_ACK_TIMEOUT_MS,
        val maxInterruptions: Int = 5,
        /** When only the board's confirmation can tell (the same version reinstalled): how long to keep saying Hello. */
        val confirmWaitMs: Long = 20_000L,
        val confirmRetryMs: Long = 2_000L,
    )

    /** How the board looks after the restart. */
    enum class RestartVerdict {
        INSTALLED,

        /** Only `ota_image_confirmed` can decide, and it has not come yet. */
        AWAITING_CONFIRMATION,
    }

    private enum class EndOutcome { OK, INCOMPLETE, NO_ANSWER }

    private var interruptions = 0
    private var transferStarted = false
    private lateinit var link: ScoutOtaLink

    /** The link whose setup Hello was already judged; later checks on it send a new Hello. */
    private var setupHelloUsedOn: ScoutOtaLink? = null

    /**
     * Runs the install. [onPhase] hears each step.
     *
     * @throws FirmwareUpdateError When the install fails; [FirmwareUpdateError.isResumable] says
     * whether running it again with the same package continues the transfer.
     */
    suspend fun run(onPhase: (FirmwareInstallPhase) -> Unit): FirmwareInstallResult {
        try {
            return install(onPhase)
        } catch (e: CancellationException) {
            if (transferStarted) abortTransfer()
            throw e
        } catch (e: FirmwareUpdateError) {
            throw endWithoutResume(e)
        } catch (e: ScoutLinkException) {
            throw endWithoutResume(classify(e))
        } catch (e: RuntimeException) {
            throw endWithoutResume(FirmwareUpdateError.ProtocolError(e.message ?: e.javaClass.simpleName))
        }
    }

    /**
     * A failure that can't resume ends the transfer on the board too (OtaAbort), so it surveys again
     * at once instead of waiting 5 minutes in its OTA state. A resumable one keeps the partial image.
     */
    private suspend fun endWithoutResume(error: FirmwareUpdateError): FirmwareUpdateError {
        if (transferStarted && !error.isResumable) abortTransfer()
        return error
    }

    private suspend fun install(onPhase: (FirmwareInstallPhase) -> Unit): FirmwareInstallResult {
        if (pkg.image.size > FirmwareLimits.MAX_IMAGE_BYTES) throw FirmwareUpdateError.ImageTooLarge()
        onPhase(FirmwareInstallPhase.Connecting)
        link = port.awaitLink(timing.connectAttemptMs) ?: throw FirmwareUpdateError.NotConnected()
        pkg.checkCompatible(link.info)
        val previous = link.firmwareVersion

        var endConfirmed: Boolean
        while (true) {
            transfer(onPhase)
            onPhase(FirmwareInstallPhase.Verifying)
            // From here the board may switch images: a cancel must not send OtaAbort
            transferStarted = false
            when (end()) {
                EndOutcome.OK -> endConfirmed = true
                EndOutcome.NO_ANSWER -> endConfirmed = false
                EndOutcome.INCOMPLETE -> {
                    // The board lost part of the image (it restarted): send the rest again
                    transferStarted = true
                    interrupted(onPhase)
                    continue
                }
            }
            break
        }

        onPhase(FirmwareInstallPhase.Restarting)
        waitForRestart()
        onPhase(FirmwareInstallPhase.Confirming)
        val hello = confirmedHello(previous, endConfirmed)
        return FirmwareInstallResult(previous, hello.firmwareVersion, hello.otaImageConfirmed, hello.compatible)
    }

    /** OtaBegin and the chunk transfer, resuming after interruptions. */
    private suspend fun transfer(onPhase: (FirmwareInstallPhase) -> Unit) {
        val total = pkg.image.size
        while (true) {
            try {
                val parameters = begin()
                transferStarted = true
                onPhase(FirmwareInstallPhase.Transferring(Integer.toUnsignedLong(parameters.startOffset).toInt().coerceAtMost(total), total))
                OtaChunkSender(link, pkg.image, parameters, timing.ackTimeoutMs).run {
                    onPhase(FirmwareInstallPhase.Transferring(it, total))
                }
                return
            } catch (e: ScoutLinkException) {
                if (needsUser(e)) throw classify(e)
                Timber.i("Scout firmware transfer interrupted: %s", e.failure)
            } catch (e: OtaTransferException) {
                when {
                    e.kind == OtaTransferException.Kind.STALLED -> Unit
                    e.status == OtaAckStatus.OTA_ACK_STATUS_NOT_ACTIVE -> Unit
                    e.status == OtaAckStatus.OTA_ACK_STATUS_WRITE_FAILED -> throw FirmwareUpdateError.FlashWriteFailed()
                    else -> throw FirmwareUpdateError.ProtocolError(e.message ?: e.kind.name)
                }
                Timber.i("Scout firmware transfer restarts: %s", e.message)
            }
            interrupted(onPhase)
        }
    }

    private suspend fun begin(): OtaBeginResult {
        link.clearAcks()
        link.enableOta()
        val response = link.send(ScoutProtocol.otaBegin(pkg.image.size, pkg.sha256))
        return when (response.result) {
            ResultCode.RESULT_CODE_OK -> if (response.hasOtaBegin()) {
                response.otaBegin
            } else {
                throw FirmwareUpdateError.ProtocolError("OtaBegin without parameters")
            }

            ResultCode.RESULT_CODE_INVALID_ARGUMENT -> throw FirmwareUpdateError.ImageTooLarge()
            ResultCode.RESULT_CODE_BUSY -> throw FirmwareUpdateError.BoardBusy()
            else -> throw FirmwareUpdateError.BoardRejected(describe(response))
        }
    }

    private suspend fun end(): EndOutcome {
        val response = try {
            link.send(ScoutProtocol.otaEnd())
        } catch (e: ScoutLinkException) {
            if (needsUser(e)) throw classify(e)
            // The board may already be restarting; Hello decides
            return EndOutcome.NO_ANSWER
        }
        return when (response.result) {
            ResultCode.RESULT_CODE_OK -> EndOutcome.OK
            ResultCode.RESULT_CODE_OTA_VERIFY_FAILED -> throw FirmwareUpdateError.VerifyFailed()
            ResultCode.RESULT_CODE_INVALID_STATE -> EndOutcome.INCOMPLETE
            else -> throw FirmwareUpdateError.BoardRejected(describe(response))
        }
    }

    /** Counts an interruption and gets a link to continue on. */
    private suspend fun interrupted(onPhase: (FirmwareInstallPhase) -> Unit) {
        interruptions++
        if (interruptions > timing.maxInterruptions) throw FirmwareUpdateError.ConnectionLost()
        onPhase(FirmwareInstallPhase.Reconnecting(interruptions))
        link = port.awaitLink(timing.reconnectBudgetMs) ?: throw FirmwareUpdateError.ConnectionLost()
        pkg.checkCompatible(link.info)
    }

    /** Waits for the board to drop the link after OtaEnd (it restarts about a second later), else drops it. */
    private suspend fun waitForRestart() {
        if (!link.isOpen) return
        if (!link.awaitLost(timing.restartWaitMs) && link.isOpen) port.dropLink()
    }

    /** Reconnects after the restart and says Hello, repeating it while only the confirmation can tell. */
    private suspend fun confirmedHello(previous: String, endConfirmed: Boolean): HelloResult {
        var waitedMs = 0L
        while (true) {
            val hello = helloAfterRestart()
            when (checkRestart(hello, pkg.version, previous, endConfirmed)) {
                RestartVerdict.INSTALLED -> return hello
                RestartVerdict.AWAITING_CONFIRMATION -> {
                    if (waitedMs + timing.confirmRetryMs > timing.confirmWaitMs) {
                        throw if (endConfirmed) FirmwareUpdateError.NotConfirmed(hello.firmwareVersion) else FirmwareUpdateError.ConnectionLost()
                    }
                    delay(timing.confirmRetryMs)
                    waitedMs += timing.confirmRetryMs
                }
            }
        }
    }

    private suspend fun helloAfterRestart(): HelloResult {
        val current = port.awaitLink(timing.returnBudgetMs) ?: throw FirmwareUpdateError.BoardDidNotReturn()
        link = current
        // The link's setup said Hello already; the board reports the confirmation on that first one only
        current.hello?.takeIf { setupHelloUsedOn !== current }?.let {
            setupHelloUsedOn = current
            return it
        }
        setupHelloUsedOn = current
        val response = try {
            current.send(ScoutProtocol.hello(appVersion))
        } catch (e: ScoutLinkException) {
            if (needsUser(e)) throw classify(e)
            throw FirmwareUpdateError.BoardDidNotReturn()
        }
        val answered = response.result == ResultCode.RESULT_CODE_OK || response.result == ResultCode.RESULT_CODE_INCOMPATIBLE
        if (!answered || !response.hasHello()) throw FirmwareUpdateError.ProtocolError("Hello: ${describe(response)}")
        return response.hello
    }

    private suspend fun abortTransfer() = withContext(NonCancellable) {
        if (!::link.isInitialized || !link.isOpen) return@withContext
        withTimeoutOrNull(ScoutProtocol.CONTROL_TIMEOUT_MS) {
            runCatching { link.send(ScoutProtocol.otaAbort()) }
        }
    }

    companion object {
        /**
         * Judges the board after the restart (protocol.md section 10, step 5). The version decides
         * when it differs from the previous: the previous version means the board went back to its
         * old image, even after a successful OtaEnd. `ota_image_confirmed` is used only when the
         * version can't tell (the same version reinstalled), because the board keeps it in memory only.
         *
         * @throws FirmwareUpdateError When the board is not on the expected version.
         */
        @JvmStatic
        fun checkRestart(hello: HelloResult, expected: String, previous: String, endConfirmed: Boolean): RestartVerdict {
            val found = hello.firmwareVersion
            if (expected != previous) {
                if (found == expected) return RestartVerdict.INSTALLED
                if (found == previous) throw if (endConfirmed) FirmwareUpdateError.RolledBack(found) else FirmwareUpdateError.ConnectionLost()
                throw FirmwareUpdateError.UnexpectedVersion(expected, found)
            }
            if (found != expected) throw FirmwareUpdateError.UnexpectedVersion(expected, found)
            // Only a compatible Hello carries the confirmation; another protocol major can't be checked further
            if (!hello.compatible || hello.otaImageConfirmed) return RestartVerdict.INSTALLED
            return RestartVerdict.AWAITING_CONFIRMATION
        }

        /** Failures a reconnect can't fix, so the install stops at once instead of resuming. */
        private val NEEDS_USER = setOf(
            ScoutLinkFailure.BOND_MISSING, ScoutLinkFailure.PAIRING_REJECTED,
            ScoutLinkFailure.BLUETOOTH_UNAVAILABLE, ScoutLinkFailure.PERMISSION_MISSING, ScoutLinkFailure.SERVICE_MISSING,
        )

        private fun needsUser(e: ScoutLinkException) = e.failure in NEEDS_USER

        /** @return The update error for a link failure that ended the install. */
        @JvmStatic
        fun classify(e: ScoutLinkException): FirmwareUpdateError = when (e.failure) {
            ScoutLinkFailure.BOND_MISSING, ScoutLinkFailure.PAIRING_REJECTED -> FirmwareUpdateError.PairingLost()
            ScoutLinkFailure.BLUETOOTH_UNAVAILABLE, ScoutLinkFailure.PERMISSION_MISSING -> FirmwareUpdateError.NotConnected()
            // A board without the OTA characteristic (a third-party build) can only be updated over USB
            ScoutLinkFailure.SERVICE_MISSING -> FirmwareUpdateError.BoardRejected(e.message ?: "no OTA characteristic")
            ScoutLinkFailure.MALFORMED -> FirmwareUpdateError.ProtocolError(e.message ?: "malformed")
            else -> FirmwareUpdateError.ConnectionLost()
        }

        private fun describe(response: ControlResponse): String =
            if (response.message.isEmpty()) response.result.name else "${response.result.name}: ${response.message}"
    }
}
