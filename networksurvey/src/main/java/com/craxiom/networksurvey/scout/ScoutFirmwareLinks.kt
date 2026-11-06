package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.HelloResult
import com.craxiom.networksurvey.board.v1.OtaAck
import com.craxiom.networksurvey.scout.firmware.FirmwareUpdateError
import com.craxiom.networksurvey.scout.firmware.ScoutOtaLink
import com.craxiom.networksurvey.scout.firmware.ScoutOtaPort
import com.craxiom.networksurvey.scout.link.ScoutGatt
import com.craxiom.networksurvey.scout.link.ScoutLinkException
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.link.ScoutLinkInfo
import com.craxiom.networksurvey.scout.link.ScoutSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/** Why the firmware update can't get a link until the user acts. */
internal enum class FirmwareLinkBlock {
    /** The Scout or Android lost the bond, or pairing was refused. */
    PAIRING_LOST,

    /** Bluetooth is off or the Nearby devices permission is missing. */
    NO_BLUETOOTH,
}

/**
 * The firmware update's view of the Scout link ([ScoutOtaPort]): the manager reports every state
 * change through [update], and the installer waits here for a set-up link. Each link gets its own
 * [SessionOtaLink], so acks and the lost signal of an old link never reach a new one.
 *
 * [update], [onAck] and [dropLink] run on the Scout thread, as does the installer.
 */
internal class ScoutFirmwareLinks(private val dropCurrentLink: () -> Unit) : ScoutOtaPort {
    private sealed interface Availability {
        data object Down : Availability
        class Up(val link: SessionOtaLink) : Availability
        class Blocked(val block: FirmwareLinkBlock) : Availability
    }

    private val availability = MutableStateFlow<Availability>(Availability.Down)
    private var current: SessionOtaLink? = null

    /**
     * The manager's latest view: [session] is the controller's link (set up when [ready]), [block]
     * a reason no link can come until the user acts.
     */
    fun update(session: ScoutSession?, ready: Boolean, block: FirmwareLinkBlock?) {
        val live = session?.takeIf { ready && it.isOpen }
        val info = live?.linkInfo
        if (current?.session !== live) {
            current?.markLost()
            current = if (live != null && info != null) SessionOtaLink(live, info) else null
        }
        val link = current
        availability.value = when {
            block != null -> Availability.Blocked(block)
            link != null -> Availability.Up(link)
            else -> Availability.Down
        }
    }

    /** An OTA ack from the board, for the current link. */
    fun onAck(ack: OtaAck) {
        current?.deliver(ack)
    }

    override suspend fun awaitLink(timeoutMs: Long): ScoutOtaLink? = withTimeoutOrNull(timeoutMs) {
        when (val state = availability.first { (it is Availability.Up && it.link.isOpen) || it is Availability.Blocked }) {
            is Availability.Up -> state.link
            is Availability.Blocked -> throw when (state.block) {
                FirmwareLinkBlock.PAIRING_LOST -> FirmwareUpdateError.PairingLost()
                FirmwareLinkBlock.NO_BLUETOOTH -> FirmwareUpdateError.NotConnected()
            }

            Availability.Down -> null
        }
    }

    override fun dropLink() {
        current?.markLost()
        dropCurrentLink()
    }
}

/** One controller link as a [ScoutOtaLink]: its session, its acks and when it ended. */
internal class SessionOtaLink(val session: ScoutSession, private val linkInfo: ScoutLinkInfo) : ScoutOtaLink {
    private val acks = Channel<OtaAck>(Channel.UNLIMITED)
    private val lost = CompletableDeferred<Unit>()

    override val info: BoardInfo get() = linkInfo.info
    override val hello: HelloResult? get() = linkInfo.hello
    override val maxWriteBytes: Int get() = ScoutGatt.maxWriteBytes(linkInfo.mtu)
    override val isOpen: Boolean get() = !lost.isCompleted && session.isOpen

    override suspend fun enableOta() = session.enableOta()

    override fun clearAcks() {
        while (acks.tryReceive().isSuccess) Unit
    }

    override suspend fun send(request: ControlRequest.Builder, timeoutMs: Long): ControlResponse = session.send(request, timeoutMs)

    override suspend fun writeOta(chunk: ByteArray) = session.writeOta(chunk)

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

    fun deliver(ack: OtaAck) {
        acks.trySend(ack)
    }

    fun markLost() {
        lost.complete(Unit)
    }
}
