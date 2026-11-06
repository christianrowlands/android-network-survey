package com.craxiom.networksurvey.ui.survey.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.status.ScoutOutage
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.survey.SurveyEventMessages
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.AttentionPlateUi
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The plate for an NS Scout that is the Wi-Fi source and can't deliver. Before Start it says what
 * won't be recorded and offers "Use phone Wi-Fi instead" (Start is never blocked); while recording
 * it says what isn't being recorded, in the notification's words, and the source stays locked.
 * Out of range and a firmware update are amber (they end by themselves); the rest need the user and are red.
 */
@Composable
fun ScoutPlate(plate: AttentionPlateUi.Scout, actions: AttentionPlateActions, modifier: Modifier = Modifier) {
    val item = plate.item
    val name = item.scoutName
    val status = NsTheme.colors.status
    val colors = if (item.outage == ScoutOutage.OUT_OF_RANGE || item.outage == ScoutOutage.UPDATING_FIRMWARE) status.warning else status.danger
    val title = if (plate.recording) attentionItemText(item) else readyTitle(item.outage, name)
    val body = if (plate.recording) recordingBody(item) else readyBody(item)
    Plate(colors = colors, icon = icon(item.outage), title = title, body = body, modifier = modifier) {
        when (item.outage) {
            ScoutOutage.BLUETOOTH_OFF, ScoutOutage.PERMISSION_MISSING ->
                NsTextAction(scoutActionText(item.outage), { actions.onFixItem(item) })

            ScoutOutage.APP_UPDATE_NEEDED -> if (!plate.recording) UsePhoneAction(actions)

            // The update runs over Bluetooth before Start; while recording it is blocked, so the Scout page explains
            ScoutOutage.NEEDS_FIRMWARE_UPDATE -> if (plate.recording) {
                NsTextAction(stringResource(R.string.scout_action_scout_page), actions::onOpenScout)
            } else {
                NsTextAction(stringResource(R.string.scout_fw_title), actions::onUpdateScoutFirmware)
                UsePhoneAction(actions)
            }

            else -> {
                if (!plate.recording && item.outage == ScoutOutage.OUT_OF_RANGE) UsePhoneAction(actions)
                NsTextAction(stringResource(R.string.scout_action_scout_page), actions::onOpenScout)
                if (!plate.recording && item.outage != ScoutOutage.OUT_OF_RANGE) UsePhoneAction(actions)
            }
        }
    }
}

@Composable
private fun UsePhoneAction(actions: AttentionPlateActions) {
    NsTextAction(stringResource(R.string.wifi_scout_action_use_phone), actions::onUsePhoneWifi)
}

@DrawableRes
private fun icon(outage: ScoutOutage): Int = when (outage) {
    ScoutOutage.OUT_OF_RANGE, ScoutOutage.PERMISSION_MISSING, ScoutOutage.CANT_CONNECT -> R.drawable.ic_sensors_off
    ScoutOutage.BLUETOOTH_OFF -> R.drawable.ic_bluetooth_disabled
    ScoutOutage.NEEDS_FIRMWARE_UPDATE, ScoutOutage.APP_UPDATE_NEEDED, ScoutOutage.UPDATING_FIRMWARE -> R.drawable.ic_system_update
    ScoutOutage.PAIRING_LOST -> R.drawable.ic_link_off
}

@Composable
private fun readyTitle(outage: ScoutOutage, name: String): String = when (outage) {
    ScoutOutage.OUT_OF_RANGE -> stringResource(R.string.wifi_scout_not_connected_title, name)
    ScoutOutage.BLUETOOTH_OFF -> stringResource(R.string.scout_word_bluetooth_off)
    ScoutOutage.PERMISSION_MISSING -> stringResource(R.string.scout_word_permission)
    ScoutOutage.NEEDS_FIRMWARE_UPDATE -> stringResource(R.string.wifi_scout_firmware_title, name)
    ScoutOutage.APP_UPDATE_NEEDED -> stringResource(R.string.scout_detail_app_update, name)
    ScoutOutage.PAIRING_LOST -> stringResource(R.string.wifi_scout_pairing_lost_title, name)
    ScoutOutage.CANT_CONNECT -> stringResource(R.string.wifi_scout_cant_connect_title, name)
    ScoutOutage.UPDATING_FIRMWARE -> stringResource(R.string.wifi_scout_updating_title, name)
}

/** What won't be recorded, naming each type: "Wi-Fi, Wi-Fi clients and Raw frames won't be recorded until it connects." */
@Composable
private fun readyBody(item: AttentionItemUi.Scout): String {
    val resources = LocalResources.current
    val types = SurveyEventMessages.join(resources, SurveyEventMessages.scoutTypeNames(resources, item))
    return when (item.outage) {
        ScoutOutage.OUT_OF_RANGE -> stringResource(R.string.scout_plate_not_connected_body_types, types)
        ScoutOutage.BLUETOOTH_OFF -> stringResource(R.string.scout_plate_bluetooth_body, types)
        ScoutOutage.PERMISSION_MISSING -> stringResource(R.string.scout_plate_permission_body, item.scoutName, types)
        ScoutOutage.NEEDS_FIRMWARE_UPDATE -> stringResource(R.string.scout_plate_firmware_body, types)
        ScoutOutage.APP_UPDATE_NEEDED -> stringResource(R.string.scout_plate_app_update_body, types)
        ScoutOutage.PAIRING_LOST -> stringResource(R.string.scout_plate_pairing_lost_body, types)
        ScoutOutage.CANT_CONNECT -> stringResource(R.string.scout_plate_cant_connect_body, types)
        ScoutOutage.UPDATING_FIRMWARE -> stringResource(R.string.scout_plate_updating_body, types)
    }
}

/**
 * While recording the title already says it all, except why being out of range loses little and
 * what fixes a Scout that can't connect.
 */
@Composable
private fun recordingBody(item: AttentionItemUi.Scout): String? = when (item.outage) {
    ScoutOutage.OUT_OF_RANGE -> stringResource(R.string.scout_plate_out_of_range_body)
    ScoutOutage.CANT_CONNECT -> stringResource(R.string.scout_detail_cant_connect, item.scoutName)
    else -> null
}
