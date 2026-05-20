package com.craxiom.networksurvey.ui.nsanalytics

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import timber.log.Timber

/**
 * Helper for the notification shown when automatic NS Analytics uploads are paused because the
 * workspace record quota was exceeded (HTTP 402).
 *
 * The pause typically happens during a background auto-upload while the app is closed, so a
 * notification is the discoverable way to tell the user why uploads stopped and how to resume.
 * Tapping the notification opens the NS Analytics screen, where the paused banner and the
 * "Upload now to resume" action live.
 */
object NsAnalyticsNotificationHelper {

    private const val CHANNEL_ID = "ns_analytics_notification_channel"
    private const val CHANNEL_NAME = "NS Analytics"
    private const val CHANNEL_DESCRIPTION =
        "Notifications about NS Analytics uploads, such as when uploads are paused"

    /**
     * Create the NS Analytics notification channel. Idempotent and safe to call from a worker.
     */
    fun createNotificationChannel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(true)
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Show the "uploads paused" notification.
     *
     * @param context Application context
     * @param message Optional quota message from the backend, used as the expanded body text.
     *        Falls back to a generic message when null/blank.
     */
    fun showUploadsPausedNotification(context: Context, message: String?) {
        createNotificationChannel(context)

        // Route the tap to the NS Analytics screen. The activity reads this extra in both
        // onCreate and onNewIntent so it works whether the app is cold or already running.
        val intent = Intent(context, NetworkSurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NsAnalyticsConstants.EXTRA_NAVIGATE_TO_NS_ANALYTICS, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NsAnalyticsConstants.NS_ANALYTICS_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (message.isNullOrBlank()) {
            context.getString(R.string.ns_analytics_paused_notification_text)
        } else {
            message
        }

        // Collapsed view shows the generic text; the expanded (BigText) view shows the backend
        // quota message when available.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_upload_24)
            .setContentTitle(context.getString(R.string.ns_analytics_paused_notification_title))
            .setContentText(context.getString(R.string.ns_analytics_paused_notification_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NsAnalyticsConstants.NS_ANALYTICS_NOTIFICATION_ID, notification)
            Timber.i("Showed NS Analytics uploads-paused notification")
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+). The in-app banner is the guaranteed
            // feedback, so this is non-fatal.
            Timber.w(e, "Notification permission denied; relying on in-app paused banner")
        } catch (e: Exception) {
            Timber.e(e, "Error showing NS Analytics uploads-paused notification")
        }
    }

    /**
     * Dismiss the "uploads paused" notification (e.g. once uploads resume).
     */
    fun clearUploadsPausedNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context)
                .cancel(NsAnalyticsConstants.NS_ANALYTICS_NOTIFICATION_ID)
        } catch (e: Exception) {
            Timber.w(e, "Error clearing NS Analytics uploads-paused notification")
        }
    }
}
