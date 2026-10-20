package com.craxiom.networksurvey.ui.survey

import android.content.res.Resources
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.ui.survey.components.labelRes
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.SurveyEvent
import java.text.NumberFormat

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

        is SurveyEvent.Stopped -> resources.getQuantityString(
            R.plurals.survey_stopped,
            event.records.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            NumberFormat.getIntegerInstance().format(event.records),
        )

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
        SurveyEvent.UploadBlockedByOrganization -> resources.getString(R.string.ns_analytics_upload_status_mdm_blocked)
    }

    /** The one-line problem for a skipped record type or destination. */
    fun itemText(resources: Resources, item: AttentionItemUi): String = when (item) {
        is AttentionItemUi.Type -> {
            val name = resources.getString(item.type.labelRes)
            when (item.reason) {
                SkipReason.RADIO_OFF -> resources.getString(R.string.survey_item_radio_off, name)
                SkipReason.PERMISSION_MISSING -> resources.getString(R.string.survey_item_permission, name)
                else -> resources.getString(R.string.survey_item_failed, name)
            }
        }

        is AttentionItemUi.Delivery ->
            DestinationStatusText.problem(resources, item.destination, item.status.word, item.status.detail)

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

    /** "A, B and C". */
    fun join(resources: Resources, names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names.first()
        else -> resources.getString(
            R.string.survey_list_last,
            names.dropLast(1).joinToString(resources.getString(R.string.survey_list_separator)),
            names.last(),
        )
    }
}
