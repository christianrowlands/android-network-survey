package com.craxiom.networksurvey.ui.survey

import android.content.res.Resources
import androidx.annotation.StringRes
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.status.ScoutOutage
import com.craxiom.networksurvey.ui.survey.components.labelRes
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.SurveyEvent
import com.craxiom.networksurvey.ui.common.CountFormat

/**
 * The words for the Survey screen's snackbars and attention items. Plain functions over
 * [Resources] so the snackbar (outside composition) and the plates (inside it) say exactly the
 * same thing.
 */
object SurveyEventMessages {

    /** @return The snackbar text for [event], or null when the system already shows feedback. */
    fun message(resources: Resources, event: SurveyEvent): String? = when (event) {
        is SurveyEvent.Started -> when {
            event.skipped > 0 -> resources.getQuantityString(R.plurals.survey_started_skipped, event.skipped, event.skipped)
            event.missionId != null -> resources.getString(R.string.survey_started_mission, event.missionId)
            else -> resources.getString(R.string.survey_started)
        }

        is SurveyEvent.CouldNotStart -> if (event.problems.isEmpty()) {
            resources.getString(R.string.survey_could_not_start_generic)
        } else {
            resources.getString(R.string.survey_could_not_start, join(resources, event.problems.map { itemText(resources, it) }))
        }

        is SurveyEvent.Stopped -> CountFormat.plural(resources, R.plurals.survey_stopped, event.records)

        SurveyEvent.Applied -> resources.getString(R.string.survey_applied)
        is SurveyEvent.OrganizationChanged -> {
            val parts = (event.addedTypes.map { it.labelRes } + event.addedDestinations.map { it.labelRes })
                .map { resources.getString(R.string.survey_org_changed_added, resources.getString(it)) } +
                (event.removedTypes.map { it.labelRes } + event.removedDestinations.map { it.labelRes })
                    .map { resources.getString(R.string.survey_org_changed_stopped, resources.getString(it)) }
            resources.getString(R.string.survey_org_changed, parts.joinToString(resources.getString(R.string.survey_list_separator)))
        }

        SurveyEvent.OrganizationStopped -> resources.getString(R.string.survey_org_stopped)
        SurveyEvent.PhoneStateFollowsCellular -> resources.getString(R.string.survey_phone_state_follows_cellular)
        SurveyEvent.MissionIdCopied -> resources.getString(R.string.mission_id_copied)
        is SurveyEvent.SetupSaved -> resources.getString(R.string.survey_setup_saved, event.name)
        SurveyEvent.UploadWaitsForNetwork -> resources.getString(R.string.survey_upload_waits_for_network)
        SurveyEvent.UploadNeedsNetwork -> resources.getString(R.string.survey_upload_needs_network)
        is SurveyEvent.UploadBlockedByOrganization -> resources.getString(
            if (event.destination == Destination.COMMUNITY) R.string.community_not_allowed else R.string.ns_analytics_upload_status_mdm_blocked,
        )

        SurveyEvent.WifiFromPhone -> resources.getString(R.string.scout_snackbar_phone)
        is SurveyEvent.WifiFromScout -> resources.getString(R.string.scout_snackbar_scout, event.scoutName)
        SurveyEvent.WifiSourceLocked -> resources.getString(R.string.wifi_scout_stop_to_switch)
    }

    /** @return The snackbar's action label for [event], or null when it has none. */
    fun actionLabel(resources: Resources, event: SurveyEvent): String? = when (event) {
        SurveyEvent.WifiFromPhone -> resources.getString(R.string.mqtt_undo)
        else -> null
    }

    /** The one-line problem for a skipped record type or destination. */
    fun itemText(resources: Resources, item: AttentionItemUi): String = when (item) {
        is AttentionItemUi.Type -> {
            val name = resources.getString(item.type.labelRes)
            when (item.reason) {
                SkipReason.RADIO_OFF -> resources.getString(R.string.survey_item_radio_off, name)
                SkipReason.PERMISSION_MISSING -> resources.getString(R.string.survey_item_permission, name)
                SkipReason.NEEDS_SCOUT -> resources.getString(R.string.survey_item_needs_scout, name)
                SkipReason.NEEDS_LOG_FILES -> resources.getString(R.string.survey_item_needs_log_files, name)
                else -> resources.getString(R.string.survey_item_failed, name)
            }
        }

        is AttentionItemUi.Delivery ->
            DestinationStatusText.problem(resources, item.destination, item.status.word, item.status.detail)

        is AttentionItemUi.Scout -> scoutItemText(resources, item)

        is AttentionItemUi.Dest -> {
            val name = resources.getString(item.destination.labelRes)
            when (item.reason) {
                SkipReason.NOT_CONFIGURED -> resources.getString(R.string.survey_item_not_set_up, name)
                SkipReason.NOT_REGISTERED -> resources.getString(R.string.survey_item_not_connected)
                SkipReason.NO_UPLOAD_TARGET -> resources.getString(R.string.survey_item_no_upload_target)
                else -> resources.getString(R.string.survey_item_failed, name)
            }
        }
    }

    /**
     * Why the Scout's types aren't being recorded, naming each one the survey runs: "NS-ABCD is out
     * of range, so Wi-Fi, Wi-Fi clients and Raw frames aren't being recorded". Wi-Fi alone keeps the
     * singular sentence.
     */
    fun scoutItemText(resources: Resources, item: AttentionItemUi.Scout): String {
        if (item.types == listOf(RecordType.WIFI) && !item.alsoBluetooth) {
            return resources.getString(scoutItemRes(item.outage, several = false), item.scoutName)
        }
        val names = join(resources, scoutTypeNames(resources, item))
        return if (item.outage == ScoutOutage.BLUETOOTH_OFF) {
            resources.getString(R.string.scout_item_bluetooth_off_types, names)
        } else {
            resources.getString(scoutItemRes(item.outage, several = true), item.scoutName, names)
        }
    }

    /**
     * @return The names of what [item] leaves out, in order: Wi-Fi ("Wi-Fi from NS-ABCD" when
     * Bluetooth is off, since the Scout isn't named otherwise), Wi-Fi clients, Raw frames, then
     * Bluetooth when it is left out too.
     */
    fun scoutTypeNames(resources: Resources, item: AttentionItemUi.Scout): List<String> {
        val names = item.types.map { type ->
            if (type == RecordType.WIFI && item.outage == ScoutOutage.BLUETOOTH_OFF) {
                resources.getString(R.string.survey_type_wifi_from, item.scoutName)
            } else {
                resources.getString(type.labelRes)
            }
        }
        return if (item.alsoBluetooth) names + resources.getString(RecordType.BLUETOOTH.labelRes) else names
    }

    private fun scoutItemRes(outage: ScoutOutage, several: Boolean): Int = when (outage) {
        ScoutOutage.OUT_OF_RANGE -> if (several) R.string.scout_item_out_of_range_types else R.string.scout_item_out_of_range
        ScoutOutage.BLUETOOTH_OFF -> if (several) R.string.scout_item_bluetooth_off_types else R.string.scout_item_bluetooth_off
        ScoutOutage.PERMISSION_MISSING -> if (several) R.string.scout_item_permission_types else R.string.scout_item_permission
        ScoutOutage.NEEDS_FIRMWARE_UPDATE -> if (several) R.string.scout_item_firmware_types else R.string.scout_item_firmware
        ScoutOutage.APP_UPDATE_NEEDED -> if (several) R.string.scout_item_app_update_types else R.string.scout_item_app_update
        ScoutOutage.PAIRING_LOST -> if (several) R.string.scout_item_pairing_lost_types else R.string.scout_item_pairing_lost
        ScoutOutage.CANT_CONNECT -> if (several) R.string.scout_item_cant_connect_types else R.string.scout_item_cant_connect
        ScoutOutage.UPDATING_FIRMWARE -> if (several) R.string.scout_item_updating_types else R.string.scout_item_updating
    }

    /** "A, B and C". */
    fun join(resources: Resources, names: List<String>, @StringRes last: Int = R.string.survey_list_last): String = when (names.size) {
        0 -> ""
        1 -> names.first()
        else -> resources.getString(
            last,
            names.dropLast(1).joinToString(resources.getString(R.string.survey_list_separator)),
            names.last(),
        )
    }
}
