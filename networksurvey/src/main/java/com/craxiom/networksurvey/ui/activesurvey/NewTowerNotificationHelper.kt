package com.craxiom.networksurvey.ui.activesurvey

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import timber.log.Timber
import java.util.Date

/**
 * Helper for the "New tower found" alert and the summary that replaces it when the survey stops.
 *
 * Every find replaces the previous alert instead of stacking a new one, so a long community
 * survey never leaves the user with dozens of notifications to clear. The alert still sounds on
 * every replacement because it is the sound, not the row, that tells a driver something was found.
 * Expanded, it lists this session's newest finds. When the survey stops, a silent summary of the
 * session's finds replaces it, or it is removed when there were none. Both open the New towers page.
 */
object NewTowerNotificationHelper {

    /** How many finds the expanded alert lists before summarizing the rest as "+N more". */
    const val MAX_LINES = 5

    /**
     * Get the notification sound URI with fallback to system default.
     * First tries to find a custom sound in res/raw, then falls back to system default.
     */
    private fun getNotificationSoundUri(context: Context): Uri {
        return try {
            "android.resource://${context.packageName}/${R.raw.new_tower_alert}".toUri()
        } catch (_: Exception) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    /**
     * Create the notification channel for new tower alerts. Safe to call repeatedly.
     */
    fun createNotificationChannel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.new_tower_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.new_tower_alert_channel_description)
            enableVibration(true)
            setShowBadge(true)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            setSound(getNotificationSoundUri(context), audioAttributes)
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Show (or replace) the sounding new tower alert.
     *
     * @param newest This session's newest finds, newest first; the first is the tower just found.
     * At most [MAX_LINES] are listed.
     * @param sessionCount How many towers this session has found, including the newest.
     */
    fun showNewTowerNotification(context: Context, newest: List<NewTowerFindEntity>, sessionCount: Int) {
        val latest = newest.firstOrNull() ?: return
        createNotificationChannel(context)

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(countText(context, sessionCount))
        newest.take(MAX_LINES).forEach { style.addLine(line(context, it)) }
        val more = sessionCount - newest.size.coerceAtMost(MAX_LINES)
        if (more > 0) style.setSummaryText(context.resources.getQuantityString(R.plurals.new_tower_alert_more, more, more))

        val notification = builder(context)
            .setContentTitle(context.getString(R.string.new_tower_alert_title))
            .setContentText(line(context, latest))
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Each replacement should sound again; the sound is the alert.
            .setOnlyAlertOnce(false)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setSound(getNotificationSoundUri(context))
            .build()

        if (post(context, notification)) {
            Timber.i("New tower notification shown for %s-%s-%d-%d (%s), #%d this survey",
                latest.mcc, latest.mnc, latest.area, latest.cellId, latest.radio, sessionCount)
        }
    }

    /**
     * Replace the alert with a silent summary of the session that just stopped. A session that
     * found nothing posted nothing, so it leaves the shade alone: an earlier survey's summary
     * stays until the person dismisses it or clears the list.
     *
     * @param sessionCount How many towers the stopped session found.
     */
    fun showSessionSummary(context: Context, sessionCount: Int) {
        if (sessionCount <= 0) return
        createNotificationChannel(context)

        val notification = builder(context)
            .setContentTitle(context.getString(R.string.new_tower_summary_title))
            .setContentText(countText(context, sessionCount))
            // The channel is high importance; the summary must not sound or peek.
            .setSilent(true)
            .build()

        if (post(context, notification)) Timber.i("New tower summary shown: %d this survey", sessionCount)
    }

    /** Removes the new tower alert or summary. */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID)
    }

    /** The parts the alert and the summary share: channel, icon, tap target. */
    private fun builder(context: Context): NotificationCompat.Builder {
        val intent = Intent(context, NetworkSurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_NEW_TOWERS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cell_tower)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
    }

    private fun post(context: Context, notification: Notification): Boolean = try {
        NotificationManagerCompat.from(context).notify(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID, notification)
        true
    } catch (e: SecurityException) {
        Timber.e(e, "Permission denied for showing notification")
        false
    } catch (e: Exception) {
        Timber.e(e, "Error showing new tower notification")
        false
    }

    private fun countText(context: Context, count: Int): String =
        context.resources.getQuantityString(R.plurals.new_towers_found_count, count, count)

    /** "LTE 310-260 · Cell ID 129595 · 2:04 PM" */
    private fun line(context: Context, find: NewTowerFindEntity): String {
        val tower = context.getString(R.string.new_tower_alert_content, find.radio, find.mcc, find.mnc, find.cellId)
        val time = DateFormat.getTimeFormat(context).format(Date(find.firstSeenAt))
        return context.getString(R.string.new_tower_alert_line, tower, time)
    }
}
