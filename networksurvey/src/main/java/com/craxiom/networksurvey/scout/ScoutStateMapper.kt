package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.link.ScoutCompatibility
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutBattery
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource

/** Where the Scout link is. */
enum class ScoutLinkPhase {
    /** No link and none wanted: nothing needs the Scout. */
    IDLE,

    /** Connecting or reconnecting (with backoff) because something needs the Scout. */
    CONNECTING,

    /** Connected and set up (Hello and SetTime done, or only Control for an incompatible board). */
    READY,
}

/** A user-needed problem that stops reconnects until the user acts. */
enum class ScoutIssue {
    /** The Scout or Android lost the bond. */
    PAIRING_LOST,

    /** Pairing was refused or not answered. */
    PAIRING_REFUSED,
}

/** Everything [ScoutStateMapper] needs, gathered by the manager on the Scout thread. */
data class ScoutStateInputs(
    val scout: PairedScout?,
    val source: WifiSource,
    val hasPermission: Boolean,
    val bluetoothOn: Boolean,
    val updatingFirmware: Boolean = false,
    val issue: ScoutIssue? = null,
    /** From the current or last link, null before the first link. */
    val compatibility: ScoutCompatibility? = null,
    /** The firmware version from the current or last link. */
    val firmwareVersion: String = "",
    val phase: ScoutLinkPhase = ScoutLinkPhase.IDLE,
    /** Monotonic time the current run of connect attempts began, null unless connecting. */
    val connectingSinceMs: Long? = null,
    val nowMs: Long = 0,
    /** The latest status on this link, null before the first one. */
    val status: BoardStatus? = null,
    /** The last battery charge ever reported, kept across links for the Out of range hint. */
    val lastBatteryPercent: Int? = null,
    val capturing: Boolean = false,
    val catchUp: ScoutCatchUpTracker.Snapshot? = null,
    val dataMissed: Boolean = false,
    /** Connect attempts in a row that reached the Scout and then failed to finish setting up. */
    val failedAfterConnect: Int = 0,
    /** The link is up, but the board refused the capture that is wanted (retried with backoff). */
    val captureRejected: Boolean = false,
)

/**
 * Maps the Scout's situation to one [ScoutState]. Pure, so the precedence is unit tested: no
 * Scout, phone source, permission, Bluetooth, firmware update in progress, pairing problems,
 * protocol mismatch, then the link itself (with the 30 s connect grace before Out of range, and
 * Can't connect once [CANT_CONNECT_ATTEMPTS] attempts in a row reached the Scout and then failed).
 */
object ScoutStateMapper {
    /** How long connect attempts read as Connecting before they read as Out of range. */
    const val CONNECT_GRACE_MS = 30_000L

    /**
     * Attempts in a row that reach the Scout and fail before it reads Can't connect: the Scout is
     * near and answering, so "Out of range" would send the user looking in the wrong place.
     */
    const val CANT_CONNECT_ATTEMPTS = 3

    /** @return The state for [inputs]. */
    @JvmStatic
    fun map(inputs: ScoutStateInputs): ScoutState {
        val scout = inputs.scout ?: return ScoutState.NotPaired
        return when {
            inputs.source == WifiSource.PHONE -> ScoutState.NotInUse(scout)
            !inputs.hasPermission -> ScoutState.PermissionMissing(scout)
            !inputs.bluetoothOn -> ScoutState.BluetoothOff(scout)
            inputs.updatingFirmware -> ScoutState.UpdatingFirmware(scout)
            inputs.issue == ScoutIssue.PAIRING_LOST -> ScoutState.PairingLost(scout)
            inputs.issue == ScoutIssue.PAIRING_REFUSED -> ScoutState.PairingRefused(scout)
            inputs.compatibility == ScoutCompatibility.NEEDS_FIRMWARE_UPDATE ->
                ScoutState.NeedsFirmwareUpdate(scout, inputs.firmwareVersion.ifEmpty { scout.firmwareVersion })

            inputs.compatibility == ScoutCompatibility.NEEDS_APP_UPDATE -> ScoutState.AppUpdateNeeded(scout)
            else -> mapLink(scout, inputs)
        }
    }

    /** @return When a Connecting state turns into Out of range, or null when it never will by itself. */
    @JvmStatic
    fun graceEndsAtMs(inputs: ScoutStateInputs): Long? {
        val since = inputs.connectingSinceMs ?: return null
        if (inputs.phase != ScoutLinkPhase.CONNECTING || inputs.failedAfterConnect >= CANT_CONNECT_ATTEMPTS) return null
        val end = since + CONNECT_GRACE_MS
        return if (end > inputs.nowMs) end else null
    }

    private fun mapLink(scout: PairedScout, inputs: ScoutStateInputs): ScoutState = when (inputs.phase) {
        ScoutLinkPhase.IDLE -> ScoutState.NotConnected(scout)
        ScoutLinkPhase.CONNECTING -> {
            val since = inputs.connectingSinceMs ?: inputs.nowMs
            if (inputs.failedAfterConnect >= CANT_CONNECT_ATTEMPTS) {
                ScoutState.CantConnect(scout)
            } else if (inputs.nowMs - since < CONNECT_GRACE_MS) {
                ScoutState.Connecting(scout)
            } else {
                val battery = inputs.lastBatteryPercent
                ScoutState.OutOfRange(scout, batteryMayBeEmpty = battery != null && battery <= ScoutBattery.LOW_PERCENT)
            }
        }

        ScoutLinkPhase.READY -> {
            val catchUp = inputs.catchUp
            if (inputs.captureRejected) {
                // Connected but not capturing what is wanted: an outage, never a healthy Connected
                ScoutState.CantConnect(scout)
            } else if (catchUp != null) {
                ScoutState.CatchingUp(scout, catchUp.backlogRecords, catchUp.ringFillPercent, inputs.dataMissed)
            } else {
                val status = inputs.status
                ScoutState.Connected(
                    scout = scout,
                    batteryPercent = ScoutBattery.percent(status),
                    channel = status?.currentChannel ?: 0,
                    framesPerSecond = status?.rxFramesPerSec ?: 0,
                    capturing = inputs.capturing,
                    dataMissed = inputs.dataMissed,
                )
            }
        }
    }
}
