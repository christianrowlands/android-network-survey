package com.craxiom.networksurvey.scout.model

/**
 * The one Scout status every surface shows (Survey row, Scout page, Signals, the More subtitle and
 * the notification), so the words never disagree. The cases follow the plan's vocabulary table;
 * the UI phase owns the strings. Every case but [NotPaired] carries the paired Scout for its name.
 */
sealed interface ScoutState {
    /** The paired Scout, null only for [NotPaired]. */
    val scout: PairedScout?

    /** True when Wi-Fi from the Scout can be recorded right now. */
    val isUsable: Boolean get() = false

    /** No Scout is paired: every Scout surface stays hidden except the More row. */
    data object NotPaired : ScoutState {
        override val scout: PairedScout? = null
    }

    /** "Wi-Fi from this phone · NS-XXXX not in use": the user chose the phone as the source. */
    data class NotInUse(override val scout: PairedScout) : ScoutState

    /** "Allow Nearby devices": BLUETOOTH_CONNECT is missing. */
    data class PermissionMissing(override val scout: PairedScout) : ScoutState

    /** "Bluetooth is off". */
    data class BluetoothOff(override val scout: PairedScout) : ScoutState

    /** "NS-XXXX · Updating firmware". */
    data class UpdatingFirmware(override val scout: PairedScout) : ScoutState

    /**
     * "Pairing lost": the Scout no longer has this phone's bond (erased, reflashed or paired with
     * another phone), or Android dropped it. Fixed by forgetting it in Android's Bluetooth
     * settings and pairing again, with the BOOT tip. Reconnects stop until then.
     */
    data class PairingLost(override val scout: PairedScout) : ScoutState

    /** Pairing was refused or not answered; repeats the BOOT tip. */
    data class PairingRefused(override val scout: PairedScout) : ScoutState

    /** "NS-XXXX · Needs a firmware update": the Scout speaks an older protocol major. */
    data class NeedsFirmwareUpdate(override val scout: PairedScout, val firmwareVersion: String) : ScoutState

    /** "Update Network Survey to use NS-XXXX": the Scout speaks a newer protocol major. */
    data class AppUpdateNeeded(override val scout: PairedScout) : ScoutState

    /** Nothing needs the Scout right now, so the app is not connected ("Not connected"). */
    data class NotConnected(override val scout: PairedScout) : ScoutState

    /** "NS-XXXX · Connecting": the first attempts, within the 30 s connect grace. */
    data class Connecting(override val scout: PairedScout) : ScoutState

    /**
     * "NS-XXXX · Out of range": still not connected after the grace. [batteryMayBeEmpty] when the
     * last battery reading was 10% or less.
     */
    data class OutOfRange(override val scout: PairedScout, val batteryMayBeEmpty: Boolean) : ScoutState

    /**
     * "NS-XXXX · Can't connect": the Scout answers (the link comes up) but setup keeps failing, so
     * it is not out of range. The detail asks the user to unplug it for 5 seconds. Reconnects go on.
     */
    data class CantConnect(override val scout: PairedScout) : ScoutState

    /**
     * "NS-XXXX · Connected · 82%" (Ready) or "NS-XXXX · Ch 36 · hearing 74 frames/s" (Recording).
     *
     * @property batteryPercent Null when the board reports no battery (USB powered).
     * @property channel The channel the board is on, 0 when idle.
     * @property framesPerSecond 802.11 frames heard over the last second, deliberately not records.
     * @property capturing True while the board surveys for a survey or a live screen.
     * @property dataMissed "Some data missed": the board dropped records or raw frames, or Records
     * notifications were lost, since this capture started.
     */
    data class Connected(
        override val scout: PairedScout,
        val batteryPercent: Int?,
        val channel: Int,
        val framesPerSecond: Int,
        val capturing: Boolean,
        val dataMissed: Boolean,
    ) : ScoutState {
        override val isUsable: Boolean get() = true
    }

    /**
     * "NS-XXXX · Catching up · 1,240 buffered": after a reconnect the board sends what it buffered
     * while out of range. [backlogRecords] counts the late records received so far.
     */
    data class CatchingUp(
        override val scout: PairedScout,
        val backlogRecords: Int,
        val ringFillPercent: Int,
        val dataMissed: Boolean,
    ) : ScoutState {
        override val isUsable: Boolean get() = true
    }
}
