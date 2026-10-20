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

    /**
     * Create the NS Analytics notification channel. Idempotent and safe to call from a worker.
     */
    fun createNotificationChannel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            NsAnalyticsConstants.NS_ANALYTICS_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.ns_analytics_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.ns_analytics_notification_channel_description)
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
        // Collapsed view shows the generic text; the expanded (BigText) view shows the backend
        // quota message when available.
        val text = context.getString(R.string.ns_analytics_paused_notification_text)
        show(
            context,
            title = context.getString(R.string.ns_analytics_paused_notification_title),
            text = text,
            bigText = if (message.isNullOrBlank()) text else message,
        )
    }

    /**
     * Says that NS Analytics no longer accepts this phone (removed from the workspace, or its
     * registration is no longer valid), so the running survey no longer sends records there. Shown
     * whoever started the survey, since the screen may be closed.
     *
     * @param surveyStopped True when NS Analytics was all the survey had, so it stopped.
     */
    fun showRemovedFromWorkspaceNotification(context: Context, surveyStopped: Boolean) {
        show(
            context,
            title = context.getString(R.string.ns_analytics_removed_notification_title),
            text = context.getString(
                if (surveyStopped) R.string.ns_analytics_removed_notification_text_stopped else R.string.ns_analytics_removed_notification_text
            ),
        )
    }

    /** Posts the one NS Analytics notification; a tap opens the NS Analytics page. */
    private fun show(context: Context, title: String, text: String, bigText: String = text) {
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

        val notification = NotificationCompat.Builder(context, NsAnalyticsConstants.NS_ANALYTICS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_upload_24)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NsAnalyticsConstants.NS_ANALYTICS_NOTIFICATION_ID, notification)
            Timber.i("Showed the NS Analytics notification: %s", title)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+). The NS Analytics page shows the same
            // state, so this is non-fatal.
            Timber.w(e, "Notification permission denied; relying on the NS Analytics page")
        } catch (e: Exception) {
            Timber.e(e, "Error showing the NS Analytics notification")
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
