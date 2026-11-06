package com.craxiom.networksurvey.ui.wifi.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.signals.SignalsStateMessage
import com.craxiom.networksurvey.ui.wifi.model.ScoutWifiBlock
import com.craxiom.networksurvey.ui.wifi.model.ScoutWifiFix
import com.craxiom.networksurvey.ui.wifi.model.WifiScoutUi

/** The fixes the Wi-Fi list offers when the NS Scout is the source and can't deliver. */
interface ScoutWifiFixes {
    /** Asks for the Nearby devices permission (or opens the app settings once it is denied for good). */
    fun onAllowNearby() {}

    fun onTurnOnBluetooth() {}

    fun onOpenScoutPage() {}

    /** Opens the Scout's firmware update. */
    fun onUpdateFirmware() {}

    /** Makes the phone the Wi-Fi source; never offered while a survey records. */
    fun onUsePhoneWifi() {}
}

/**
 * What keeps the NS Scout's Wi-Fi off the list, in place of the rows: a headline naming the Scout,
 * one sentence, and the one button that fixes it. While a survey records the button is never
 * phone Wi-Fi (the source is locked), and the sentence says the survey keeps going.
 *
 * @param nearbyAccess Whether the Nearby devices permission can still be asked for.
 */
@Composable
fun WifiScoutBlocked(scout: WifiScoutUi, nearbyAccess: PermissionAccess, fixes: ScoutWifiFixes) {
    val block = scout.block ?: return
    val words = words(block, scout.recording)
    val (action, onAction) = when (block.fix(scout.recording)) {
        ScoutWifiFix.ALLOW_NEARBY -> if (nearbyAccess == PermissionAccess.BLOCKED) {
            stringResource(R.string.open_settings) to fixes::onAllowNearby
        } else {
            stringResource(R.string.survey_location_allow) to fixes::onAllowNearby
        }

        ScoutWifiFix.TURN_ON_BLUETOOTH -> stringResource(R.string.wifi_scout_action_turn_on_bluetooth) to fixes::onTurnOnBluetooth
        ScoutWifiFix.UPDATE_FIRMWARE -> stringResource(R.string.scout_fw_title) to fixes::onUpdateFirmware
        ScoutWifiFix.SCOUT_PAGE -> stringResource(R.string.wifi_scout_action_scout_page) to fixes::onOpenScoutPage
        ScoutWifiFix.USE_PHONE_WIFI -> stringResource(R.string.wifi_scout_action_use_phone) to fixes::onUsePhoneWifi
    }
    SignalsStateMessage(
        icon = words.icon,
        title = stringResource(words.title, scout.name),
        text = stringResource(words.body, scout.name),
        action = action,
        onAction = onAction,
    )
}

/** @return The short word the scan strip shows for [block]. */
@StringRes
fun scoutStripWord(block: ScoutWifiBlock): Int = when (block) {
    ScoutWifiBlock.ALLOW_NEARBY -> R.string.cellular_state_permission
    ScoutWifiBlock.BLUETOOTH_OFF -> R.string.scout_word_bluetooth_off
    ScoutWifiBlock.FIRMWARE_UPDATE, ScoutWifiBlock.APP_UPDATE, ScoutWifiBlock.UPDATING_FIRMWARE -> R.string.wifi_scout_strip_update
    ScoutWifiBlock.PAIRING_LOST -> R.string.scout_word_pairing_lost
    ScoutWifiBlock.CONNECTING -> R.string.scout_word_connecting
    ScoutWifiBlock.NOT_CONNECTED -> R.string.scout_word_not_connected
    ScoutWifiBlock.OUT_OF_RANGE -> R.string.scout_word_out_of_range
    ScoutWifiBlock.CANT_CONNECT -> R.string.scout_word_cant_connect
}

private class BlockWords(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val body: Int)

private fun words(block: ScoutWifiBlock, recording: Boolean): BlockWords = when (block) {
    ScoutWifiBlock.ALLOW_NEARBY -> BlockWords(R.drawable.ic_info, R.string.scout_word_permission, R.string.wifi_scout_allow_body)
    ScoutWifiBlock.BLUETOOTH_OFF ->
        BlockWords(R.drawable.ic_bluetooth_disabled, R.string.scout_word_bluetooth_off, R.string.wifi_scout_bluetooth_off_body)

    ScoutWifiBlock.FIRMWARE_UPDATE ->
        BlockWords(R.drawable.ic_system_update, R.string.wifi_scout_firmware_title, R.string.wifi_scout_firmware_body)

    ScoutWifiBlock.APP_UPDATE ->
        BlockWords(R.drawable.ic_system_update, R.string.scout_detail_app_update, R.string.wifi_scout_app_update_body)

    ScoutWifiBlock.UPDATING_FIRMWARE ->
        BlockWords(R.drawable.ic_system_update, R.string.wifi_scout_updating_title, R.string.wifi_scout_updating_body)

    ScoutWifiBlock.PAIRING_LOST -> BlockWords(
        R.drawable.ic_sensors_off,
        R.string.wifi_scout_pairing_lost_title,
        if (recording) R.string.wifi_scout_pairing_lost_recording_body else R.string.wifi_scout_pairing_lost_body,
    )

    ScoutWifiBlock.CONNECTING -> BlockWords(R.drawable.ic_sensors, R.string.wifi_scout_connecting_title, R.string.wifi_scout_connecting_body)
    ScoutWifiBlock.NOT_CONNECTED ->
        BlockWords(R.drawable.ic_sensors_off, R.string.wifi_scout_not_connected_title, R.string.wifi_scout_not_connected_body)

    ScoutWifiBlock.OUT_OF_RANGE ->
        BlockWords(R.drawable.ic_sensors_off, R.string.wifi_scout_out_of_range_title, R.string.wifi_scout_recording_reconnect_body)

    ScoutWifiBlock.CANT_CONNECT ->
        BlockWords(R.drawable.ic_sensors_off, R.string.wifi_scout_cant_connect_title, R.string.scout_detail_cant_connect)
}
