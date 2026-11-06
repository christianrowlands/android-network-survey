package com.craxiom.networksurvey.scout.firmware

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants

/** The ongoing notification of [FirmwareUpdateService]: the install's step and how much was sent. */
internal object FirmwareUpdateNotification {
    /**
     * What the notification says.
     *
     * @property percent How much of the image was sent; null while the step has no measure.
     */
    data class Content(val text: String, val percent: Int?)

    private const val MAX_PERCENT = 100

    /** @return What the notification says during [phase]. */
    fun content(resources: Resources, phase: FirmwareInstallPhase): Content = when (phase) {
        FirmwareInstallPhase.Connecting -> Content(resources.getString(R.string.scout_fw_step_send), null)
        is FirmwareInstallPhase.Transferring -> Content(resources.getString(R.string.scout_fw_step_send), phase.percent)
        is FirmwareInstallPhase.Reconnecting -> Content(resources.getString(R.string.scout_fw_reconnecting, phase.attempt), null)
        FirmwareInstallPhase.Verifying -> Content(resources.getString(R.string.scout_fw_step_verify), null)
        FirmwareInstallPhase.Restarting -> Content(resources.getString(R.string.scout_fw_step_restart), null)
        FirmwareInstallPhase.Confirming -> Content(resources.getString(R.string.scout_fw_step_confirm), null)
    }

    fun build(context: Context, content: Content): Notification {
        val open = Intent(context, NetworkSurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return NotificationCompat.Builder(context, NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.scout_fw_notification_title))
            .setContentText(content.text)
            .setSmallIcon(R.drawable.ic_system_update)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(MAX_PERCENT, content.percent ?: 0, content.percent == null)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, FirmwareConstants.NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }
}
