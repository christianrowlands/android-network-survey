package com.craxiom.networksurvey.ui.activesurvey

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helper for the "New Tower Detected" alert.
 *
 * Every detection replaces the previous alert instead of stacking a new one, so a long community
 * survey never leaves the user with dozens of notifications to clear. The body ends with a running
 * count of new towers seen during the current community survey, which the service resets when
 * that survey stops. The alert still sounds on every replacement because it is the sound, not the
 * row, that tells a driver something new was found.
 */
object NewTowerNotificationHelper {

    private val sessionCount = AtomicInteger(0)

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
     * Reset the per-survey tower counter. Called when the community survey stops.
     */
    fun resetSessionCount() {
        sessionCount.set(0)
    }

    /**
     * Show (or replace) the new tower alert.
     *
     * @param context Application context
     * @param mcc Mobile Country Code (string to preserve leading zeros)
     * @param mnc Mobile Network Code (string to preserve leading zeros)
     * @param area TAC/LAC
     * @param cellId Cell ID
     * @param technology Radio technology (LTE, NR, GSM, UMTS)
     */
    fun showNewTowerNotification(
        context: Context,
        mcc: String,
        mnc: String,
        area: Int,
        cellId: Long,
        technology: String
    ) {
        createNotificationChannel(context)

        val count = sessionCount.incrementAndGet()

        val intent = Intent(context, NetworkSurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = context.getString(R.string.new_tower_alert_content, technology, mcc, mnc, cellId)
        val details = context.getString(R.string.new_tower_alert_details, technology, mcc, mnc, area, cellId)
        val countLine = context.resources.getQuantityString(R.plurals.new_tower_alert_session_count, count, count)

        val notification = NotificationCompat.Builder(context, NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cell_tower)
            .setContentTitle(context.getString(R.string.new_tower_alert_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details + "\n" + countLine))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            // Each replacement should sound again; the sound is the alert.
            .setOnlyAlertOnce(false)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setSound(getNotificationSoundUri(context))
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID, notification)
            Timber.i("New tower notification shown for $technology tower: MCC=$mcc, MNC=$mnc, CID=$cellId (#$count this survey)")
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied for showing notification")
        } catch (e: Exception) {
            Timber.e(e, "Error showing new tower notification")
        }
    }
}
