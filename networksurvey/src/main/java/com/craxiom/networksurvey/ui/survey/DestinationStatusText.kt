package com.craxiom.networksurvey.ui.survey

import android.content.res.Resources
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.status.StatusDetail
import com.craxiom.networksurvey.survey.status.StatusWord
import com.craxiom.networksurvey.survey.status.WaitReason
import com.craxiom.networksurvey.ui.survey.components.labelRes
import com.craxiom.networksurvey.ui.common.CountFormat

/**
 * Words a destination status for the Survey screen, the destination pages and the survey
 * notification, so all three say the same thing. Plain [Resources], so the notification can use it.
 */
object DestinationStatusText {

    /** @return The status word. */
    fun word(resources: Resources, word: StatusWord): String = resources.getString(
        when (word) {
            StatusWord.SAVING -> R.string.survey_status_saving
            StatusWord.PARTLY_SAVING -> R.string.survey_status_partly_saving
            StatusWord.UPLOADING -> R.string.survey_status_uploading
            StatusWord.QUEUED -> R.string.survey_status_queued
            StatusWord.UP_TO_DATE -> R.string.survey_status_up_to_date
            StatusWord.WAITING_FOR_INTERNET -> R.string.survey_status_waiting_for_internet
            StatusWord.NOT_UPLOADING -> R.string.survey_status_not_uploading
            StatusWord.WILL_RETRY -> R.string.survey_status_will_retry
            StatusWord.QUOTA_PAUSED, StatusWord.PAUSED -> R.string.survey_status_paused
            StatusWord.CONNECTING -> R.string.survey_status_connecting
            StatusWord.CANT_CONNECT -> R.string.survey_status_cant_connect
            StatusWord.SIGN_IN_FAILED -> R.string.survey_status_sign_in_failed
            StatusWord.SETTINGS_INVALID -> R.string.survey_status_settings_invalid
            StatusWord.LIVE -> R.string.survey_status_live
            StatusWord.RECONNECTING -> R.string.survey_status_reconnecting
            StatusWord.DROPPING -> R.string.survey_status_dropping
            StatusWord.COLLECTING -> R.string.survey_status_collecting
            StatusWord.KEY_REJECTED -> R.string.survey_status_key_rejected
            // The uploader's own word for it
            StatusWord.UPLOAD_FAILED -> R.string.uploader_failure
        }
    )

    /**
     * @param address    The broker or server, for MQTT and gRPC details.
     * @param formatTime Formats a time of day ("09:12").
     * @param nowMs      The current time, for "next upload in about 6 min".
     * @return The detail line, or null for [StatusDetail.Default] (the destination's own
     * description, which the caller words).
     */
    fun detail(
        resources: Resources,
        detail: StatusDetail,
        address: String,
        formatTime: (Long) -> String,
        nowMs: Long,
    ): String? = when (detail) {
        StatusDetail.Default -> null
        is StatusDetail.FilesFailed -> resources.getString(R.string.survey_status_files_failed, typeNames(resources, detail.types))
        is StatusDetail.Uploading -> if (detail.count > 0) {
            CountFormat.plural(resources, R.plurals.survey_status_uploading_count, detail.count)
        } else {
            resources.getString(R.string.survey_status_uploading)
        }

        is StatusDetail.Waiting -> dotted(resources, waitingCount(resources, detail.count), waitReason(resources, detail.reason, formatTime, nowMs))
        is StatusDetail.NothingWaiting -> detail.lastUploadMs?.let {
            dotted(resources, resources.getString(R.string.survey_status_nothing_waiting), resources.getString(R.string.survey_status_last_upload_at, formatTime(it)))
        } ?: resources.getString(R.string.survey_status_nothing_waiting)

        is StatusDetail.QuotaReached -> dotted(resources, resources.getString(R.string.survey_status_quota_reached), waitingCount(resources, detail.count))
        is StatusDetail.StreamConnecting -> dotted(
            resources,
            resources.getString(R.string.survey_status_not_connected_yet, address),
            sendCount(resources, detail.waiting),
        )

        is StatusDetail.StreamUnreachable -> dotted(
            resources,
            resources.getString(R.string.survey_status_cant_reach_since, address, formatTime(detail.sinceMs)),
            sendCount(resources, detail.waiting),
        )

        is StatusDetail.StreamLost -> dotted(
            resources,
            resources.getString(R.string.survey_status_lost_at, formatTime(detail.sinceMs)),
            sendCount(resources, detail.waiting),
        )

        StatusDetail.SignInRejected -> resources.getString(R.string.survey_status_sign_in_rejected)
        StatusDetail.SettingsInvalid -> resources.getString(R.string.survey_status_settings_invalid_detail)
        StatusDetail.QueueFull -> resources.getString(R.string.survey_status_queue_full)
        is StatusDetail.UploadsFailed -> dotted(
            resources,
            resources.getQuantityString(R.plurals.survey_status_uploads_failed, detail.failures, detail.failures),
            resources.getString(R.string.survey_status_next_try_at, formatTime(detail.retryAtMs)),
        )

        StatusDetail.KeyRejected -> resources.getString(R.string.survey_status_key_rejected_detail)
        is StatusDetail.UploadFailed -> uploadFailed(resources, detail)
        is StatusDetail.UploadRetrying -> uploadRetrying(resources, detail, formatTime, nowMs)
    }

    /**
     * @return The one-line problem for a destination whose records aren't arriving, for the
     * attention plate.
     */
    fun problem(resources: Resources, destination: Destination, word: StatusWord, detail: StatusDetail): String {
        val name = resources.getString(destination.labelRes)
        return when (word) {
            StatusWord.CANT_CONNECT -> resources.getString(R.string.survey_item_cant_connect, name)
            StatusWord.SIGN_IN_FAILED -> resources.getString(R.string.survey_status_sign_in_rejected)
            StatusWord.SETTINGS_INVALID -> resources.getString(R.string.survey_status_settings_invalid_detail)
            StatusWord.PARTLY_SAVING -> resources.getString(
                R.string.survey_item_files_failed,
                typeNames(resources, (detail as? StatusDetail.FilesFailed)?.types.orEmpty()),
            )

            StatusWord.KEY_REJECTED -> resources.getString(R.string.survey_status_key_rejected_detail)
            StatusWord.DROPPING -> resources.getString(R.string.survey_item_dropping, name)
            else -> resources.getString(R.string.notification_health_connecting, name, word(resources, word))
        }
    }

    private fun waitingCount(resources: Resources, count: Int): String = if (count > 0) {
        CountFormat.plural(resources, R.plurals.survey_status_waiting, count)
    } else {
        resources.getString(R.string.survey_status_nothing_waiting)
    }

    private fun sendCount(resources: Resources, count: Int): String? =
        if (count > 0) CountFormat.plural(resources, R.plurals.survey_status_waiting_to_send, count) else null

    private fun waitReason(resources: Resources, reason: WaitReason, formatTime: (Long) -> String, nowMs: Long): String = when (reason) {
        is WaitReason.NextUpload -> reason.atMs?.takeIf { it > nowMs }?.let {
            val minutes = ((it - nowMs + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(1)
            resources.getQuantityString(R.plurals.survey_status_next_upload_in, minutes, minutes)
        } ?: resources.getQuantityString(R.plurals.survey_status_uploads_every, reason.intervalMinutes, reason.intervalMinutes)

        WaitReason.UploadStarting -> resources.getString(R.string.survey_status_upload_starting)
        WaitReason.AutoUploadOff -> resources.getString(R.string.survey_status_auto_upload_off)
        WaitReason.LastUploadFailed -> resources.getString(R.string.survey_status_last_upload_failed)
        WaitReason.Offline -> resources.getString(R.string.survey_status_back_online)
        is WaitReason.EveryRecords -> resources.getString(R.string.survey_status_every_records, reason.records)
        WaitReason.OnWifi -> resources.getString(R.string.survey_status_on_wifi)
        is WaitReason.NextTry -> if (reason.atMs > nowMs) {
            resources.getString(R.string.survey_status_next_try_at, formatTime(reason.atMs))
        } else {
            resources.getString(R.string.survey_status_upload_starting)
        }

        WaitReason.NextCommunitySurvey -> resources.getString(R.string.survey_status_next_community_survey)
        WaitReason.NextSurvey -> resources.getString(R.string.survey_status_next_survey)
    }

    /**
     * @return The detail for the "Still to do" panel, whose title already gives the count: why the
     * records wait, or what the upload is doing.
     */
    fun waitingDetail(resources: Resources, detail: StatusDetail, formatTime: (Long) -> String, nowMs: Long): String = when (detail) {
        is StatusDetail.Waiting -> waitReason(resources, detail.reason, formatTime, nowMs).replaceFirstChar { it.titlecase(resources.configuration.locales[0]) }
        is StatusDetail.QuotaReached -> resources.getString(R.string.survey_status_quota_reached)
        is StatusDetail.UploadFailed -> uploadFailed(resources, detail)
        is StatusDetail.UploadRetrying -> uploadRetrying(resources, detail, formatTime, nowMs)
        else -> resources.getString(R.string.survey_status_uploading)
    }

    private fun uploadFailed(resources: Resources, detail: StatusDetail.UploadFailed): String =
        resources.getString(R.string.survey_status_upload_failed_detail, detail.message)

    /** "Last upload failed: OpenCelliD: Server error · next try at 12:43". */
    private fun uploadRetrying(resources: Resources, detail: StatusDetail.UploadRetrying, formatTime: (Long) -> String, nowMs: Long): String =
        dotted(
            resources,
            resources.getString(R.string.survey_status_upload_failed_detail, detail.message),
            waitReason(resources, detail.next, formatTime, nowMs),
        )

    private fun dotted(resources: Resources, vararg parts: String?): String =
        parts.filterNotNull().filter { it.isNotBlank() }.joinToString(resources.getString(R.string.survey_dot_separator))

    private fun typeNames(resources: Resources, types: Set<RecordType>): String =
        SurveyEventMessages.join(resources, RecordType.entries.filter { it in types }.map { resources.getString(it.labelRes) })

    private const val MILLIS_PER_MINUTE = 60_000L
}
