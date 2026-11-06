package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource

/**
 * What stops Wi-Fi from the NS Scout from reaching the Wi-Fi screens, in the order the list
 * reports it. Each has one fix: [ALLOW_NEARBY] asks for the Nearby devices permission,
 * [BLUETOOTH_OFF] opens Bluetooth settings, [FIRMWARE_UPDATE] opens the firmware update, the other
 * update cases and [OUT_OF_RANGE] open the Scout page, and the rest (including [CANT_CONNECT],
 * whose sentence says to unplug the Scout) offer phone Wi-Fi instead,
 * except while a survey records (the source is locked then, so they open the Scout page too).
 * Phone Wi-Fi being off is never one of them: the Scout does not use it.
 */
enum class ScoutWifiBlock {
    ALLOW_NEARBY,
    BLUETOOTH_OFF,
    FIRMWARE_UPDATE,
    APP_UPDATE,
    UPDATING_FIRMWARE,
    PAIRING_LOST,
    CONNECTING,
    NOT_CONNECTED,

    /** The Scout answers but setup keeps failing; the sentence says to unplug it for 5 seconds. */
    CANT_CONNECT,

    /** The Scout dropped out while a survey records; the survey says "Out of range" too. */
    OUT_OF_RANGE;

    /** @return The fix the block's one button offers; never phone Wi-Fi while a survey records. */
    fun fix(recording: Boolean): ScoutWifiFix = when (this) {
        ALLOW_NEARBY -> ScoutWifiFix.ALLOW_NEARBY
        BLUETOOTH_OFF -> ScoutWifiFix.TURN_ON_BLUETOOTH
        FIRMWARE_UPDATE -> ScoutWifiFix.UPDATE_FIRMWARE
        APP_UPDATE, UPDATING_FIRMWARE, OUT_OF_RANGE -> ScoutWifiFix.SCOUT_PAGE
        PAIRING_LOST, CONNECTING, NOT_CONNECTED, CANT_CONNECT -> if (recording) ScoutWifiFix.SCOUT_PAGE else ScoutWifiFix.USE_PHONE_WIFI
    }
}

/** The one button a [ScoutWifiBlock] shows. */
enum class ScoutWifiFix { ALLOW_NEARBY, TURN_ON_BLUETOOTH, UPDATE_FIRMWARE, SCOUT_PAGE, USE_PHONE_WIFI }

/**
 * The Scout as the Wi-Fi list's source.
 *
 * @property name The Scout's name, "NS-ABCD", which the strip puts before the scan count.
 * @property block What keeps its Wi-Fi off the list, or null while it can deliver.
 * @property recording True while a survey records, which locks the source.
 */
data class WifiScoutUi(val name: String, val block: ScoutWifiBlock?, val recording: Boolean = false)

/**
 * Turns the Scout's state into what the Wi-Fi screens show. Pure, so the precedence is tested on the
 * JVM. The Scout state already orders permission, Bluetooth, firmware update in progress, pairing
 * problems and protocol mismatch ahead of the link, so a block here follows that order.
 */
object WifiScoutSource {

    /**
     * @param recording True while a survey records: the source is locked, and the Scout dropping
     * out reads "Out of range", as on the Survey tab.
     * @return The Scout as the source, or null when Wi-Fi comes from the phone (or no Scout is paired).
     */
    @JvmStatic
    @JvmOverloads
    fun of(state: ScoutState, source: WifiSource, recording: Boolean = false): WifiScoutUi? {
        if (source != WifiSource.SCOUT) return null
        val scout = state.scout ?: return null
        return when (state) {
            // The phone is the source after all (the state and the source raced); show the phone's list
            is ScoutState.NotInUse, ScoutState.NotPaired -> null
            else -> WifiScoutUi(scout.name, block(state, recording), recording)
        }
    }

    /** @return What keeps the Scout's Wi-Fi off the list in [state], or null when it is coming in. */
    @JvmStatic
    @JvmOverloads
    fun block(state: ScoutState, recording: Boolean = false): ScoutWifiBlock? = when (state) {
        is ScoutState.Connected, is ScoutState.CatchingUp, is ScoutState.NotInUse, ScoutState.NotPaired -> null
        is ScoutState.PermissionMissing -> ScoutWifiBlock.ALLOW_NEARBY
        is ScoutState.BluetoothOff -> ScoutWifiBlock.BLUETOOTH_OFF
        is ScoutState.NeedsFirmwareUpdate -> ScoutWifiBlock.FIRMWARE_UPDATE
        is ScoutState.AppUpdateNeeded -> ScoutWifiBlock.APP_UPDATE
        is ScoutState.UpdatingFirmware -> ScoutWifiBlock.UPDATING_FIRMWARE
        is ScoutState.PairingLost, is ScoutState.PairingRefused -> ScoutWifiBlock.PAIRING_LOST
        is ScoutState.Connecting -> ScoutWifiBlock.CONNECTING
        is ScoutState.OutOfRange -> if (recording) ScoutWifiBlock.OUT_OF_RANGE else ScoutWifiBlock.NOT_CONNECTED
        is ScoutState.NotConnected -> ScoutWifiBlock.NOT_CONNECTED
        is ScoutState.CantConnect -> ScoutWifiBlock.CANT_CONNECT
    }

    /**
     * @return True when the throttle notice may suggest an NS Scout: no Scout is paired and the
     * organization allows one.
     */
    @JvmStatic
    fun suggestsScout(state: ScoutState, allowed: Boolean): Boolean = allowed && state == ScoutState.NotPaired

    /**
     * @return What stops the phone's scan, as the Wi-Fi screens should see it: nothing while the
     * Scout is the source, since the phone's permission, location and Wi-Fi do not stop the Scout.
     */
    @JvmStatic
    fun phoneScanState(phone: WifiScanState, source: WifiSource, scoutPaired: Boolean): WifiScanState =
        if (source == WifiSource.SCOUT && scoutPaired) WifiScanState.READY else phone
}
