package com.craxiom.networksurvey.ui.survey.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.engine.LinkFailure
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.survey.SurveyEventMessages
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.AttentionPlateUi
import com.craxiom.networksurvey.ui.theme.NsColorPair
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.ROW_ICON

/** What each attention plate can ask the screen to do. */
interface AttentionPlateActions {
    fun onChangeBatteryThreshold()
    fun onAdjustQueueLimit()
    fun onDisableQueueLimit()
    fun onOpenOrganizationSettings()
    fun onOpenDestination(destination: Destination)
    fun onFixItem(item: AttentionItemUi)
}

/**
 * One plate above the Recording card. Everything the old Dashboard spread across the battery,
 * queue and location cards collapses into this: one sentence of what is wrong and one action.
 */
@Composable
fun AttentionPlate(plate: AttentionPlateUi, actions: AttentionPlateActions, modifier: Modifier = Modifier) {
    val status = NsTheme.colors.status
    when (plate) {
        is AttentionPlateUi.BatteryPaused -> Plate(
            colors = status.warning,
            icon = R.drawable.ic_battery_alert,
            title = stringResource(R.string.survey_plate_battery_title, plate.level),
            body = stringResource(R.string.survey_plate_battery_body, plate.threshold),
            modifier = modifier,
        ) { BatteryThresholdAction(plate.thresholdSetByOrganization, actions) }

        is AttentionPlateUi.BatteryLow -> Plate(
            colors = status.warning,
            icon = R.drawable.ic_battery_alert,
            title = stringResource(R.string.survey_plate_battery_low_title, plate.level),
            body = stringResource(R.string.survey_plate_battery_low_body, plate.threshold),
            modifier = modifier,
        ) { BatteryThresholdAction(plate.thresholdSetByOrganization, actions) }

        is AttentionPlateUi.MqttOnlyPaused -> Plate(
            colors = status.warning,
            icon = R.drawable.ic_cloud_off,
            title = stringResource(R.string.survey_plate_mqtt_paused_title),
            body = stringResource(
                if (plate.limitSetByOrganization) R.string.survey_plate_mqtt_paused_body_organization else R.string.survey_plate_mqtt_paused_body
            ),
            modifier = modifier,
        ) {
            if (plate.limitSetByOrganization) {
                // Changing the limit here would do nothing: the organization's value always wins
                NsTextAction(stringResource(R.string.organization_settings_title), actions::onOpenOrganizationSettings)
            } else {
                NsTextAction(stringResource(R.string.survey_plate_adjust_limit), actions::onAdjustQueueLimit)
                NsTextAction(stringResource(R.string.survey_plate_disable_limit), actions::onDisableQueueLimit)
            }
        }

        is AttentionPlateUi.MqttDropping -> Plate(
            colors = status.warning,
            icon = R.drawable.ic_cloud_off,
            title = stringResource(R.string.survey_plate_dropping_title),
            body = stringResource(R.string.survey_plate_dropping_body, destinationNames(plate.stillReceiving)),
            modifier = modifier,
        ) {
            NsTextAction(
                text = stringResource(R.string.survey_plate_dropping_action),
                onClick = { actions.onOpenDestination(Destination.MQTT) },
            )
        }

        is AttentionPlateUi.Stalled -> Plate(
            colors = status.danger,
            icon = R.drawable.ic_hourglass_empty,
            title = pluralStringResource(R.plurals.survey_plate_stall_title, plate.minutes, plate.minutes),
            body = stringResource(R.string.survey_plate_stall_body),
            modifier = modifier,
        )

        is AttentionPlateUi.SurveyEnded -> Plate(
            colors = status.danger,
            icon = R.drawable.ic_cloud_off,
            title = stringResource(R.string.survey_plate_ended_title),
            body = when (plate.failure) {
                LinkFailure.SIGN_IN_REJECTED -> stringResource(R.string.survey_plate_ended_sign_in)
                LinkFailure.INVALID_SETTINGS -> stringResource(R.string.survey_plate_ended_settings)
                LinkFailure.UNREACHABLE -> stringResource(R.string.survey_item_cant_connect, stringResource(plate.destination.labelRes))
            },
            modifier = modifier,
        ) {
            NsTextAction(
                text = stringResource(R.string.survey_plate_ended_action, stringResource(plate.destination.labelRes)),
                onClick = { actions.onOpenDestination(plate.destination) },
            )
        }

        is AttentionPlateUi.NeedsAttention -> Plate(
            colors = status.warning,
            icon = R.drawable.ic_info,
            title = pluralStringResource(R.plurals.survey_plate_attention_title, plate.items.size, plate.items.size),
            body = null,
            modifier = modifier,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                plate.items.forEach { item -> AttentionItemRow(item) { actions.onFixItem(item) } }
            }
        }
    }
}

@Composable
private fun Plate(
    colors: NsColorPair,
    @DrawableRes icon: Int,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = NsTheme.shapes.panel,
        color = colors.container,
    ) {
        Row(modifier = Modifier.padding(NsTheme.spacing.l), horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.content, modifier = Modifier.size(ROW_ICON))
            Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                if (body != null) {
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Text buttons pad their label; pull the row back so the first label lines up with the body
                val buttonInset = ButtonDefaults.TextButtonContentPadding.calculateStartPadding(LocalLayoutDirection.current)
                Row(Modifier.offset(x = -buttonInset)) { actions() }
            }
        }
    }
}

/** Changing the threshold, or where the organization's value is shown when it sets it. */
@Composable
private fun BatteryThresholdAction(setByOrganization: Boolean, actions: AttentionPlateActions) {
    if (setByOrganization) {
        NsTextAction(stringResource(R.string.organization_settings_title), actions::onOpenOrganizationSettings)
    } else {
        NsTextAction(stringResource(R.string.survey_plate_battery_action), actions::onChangeBatteryThreshold)
    }
}

@Composable
private fun AttentionItemRow(item: AttentionItemUi, onFix: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = attentionItemText(item),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        attentionActionText(item)?.let { NsTextAction(it, onFix) }
    }
}

/** The one-line problem for an item; the same words the "Couldn't start" snackbar uses. */
@Composable
fun attentionItemText(item: AttentionItemUi): String =
    SurveyEventMessages.itemText(LocalResources.current, item)

@Composable
private fun attentionActionText(item: AttentionItemUi): String? = when (item) {
    is AttentionItemUi.Type -> when {
        item.reason == SkipReason.RADIO_OFF && item.type in RADIO_TYPES ->
            stringResource(R.string.survey_action_turn_on, stringResource(item.type.labelRes))

        item.reason == SkipReason.PERMISSION_MISSING -> stringResource(R.string.survey_action_allow)
        else -> stringResource(R.string.survey_action_try_again)
    }

    is AttentionItemUi.Delivery -> stringResource(R.string.survey_action_open)

    is AttentionItemUi.Dest -> when (item.reason) {
        SkipReason.NOT_CONFIGURED -> stringResource(R.string.survey_action_set_up)
        SkipReason.NOT_REGISTERED -> stringResource(R.string.survey_action_connect)
        SkipReason.NO_UPLOAD_TARGET -> stringResource(R.string.survey_action_choose)
        else -> stringResource(R.string.survey_action_try_again)
    }
}

/** The record types whose radio the screen can offer to turn on. */
val RADIO_TYPES = setOf(RecordType.WIFI, RecordType.BLUETOOTH)
