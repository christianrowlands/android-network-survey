package com.craxiom.networksurvey.services.watchlist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import timber.log.Timber

/**
 * Helper for creating and showing Watchlist "Seen" notifications.
 * <p>
 * Modeled on the New Tower notification helper, but with its own high-importance channel and a
 * distinct notification id range so the two never collide. Tapping a notification deep-links to the
 * Watchlist history screen. The notification body uses the user's own label plus the signal
 * strength; because the detection manager only alerts on a network re-appearing (not on every scan)
 * the signal value does not cause notification spam.
 */
object WatchlistNotificationHelper {

    const val CHANNEL_ID = "watchlist_alerts"
    private const val NOTIFICATION_ID_BASE = 20000
    private const val NOTIFICATION_ID_RANGE = 1000

    /**
     * Create the high-importance Watchlist notification channel. Safe to call repeatedly.
     */
    fun createNotificationChannel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.watchlist_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.watchlist_alert_channel_description)
            enableVibration(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Show a "Seen" notification for a watched network that just came into range.
     * <p>
     * The notification id is derived from the entry id, so a re-alert for the same watched network
     * replaces its previous notification rather than stacking a new one.
     *
     * @param context application context
     * @param entryId the id of the matched watchlist entry, used for a stable notification id
     * @param label the user's label for the matched entry
     * @param rssi the signal strength of the detection, in dBm
     */
    fun showWatchlistNotification(
        context: Context,
        entryId: Long,
        label: String,
        rssi: Int
    ) {
        createNotificationChannel(context)

        val notificationId =
            NOTIFICATION_ID_BASE + (Math.floorMod(entryId, NOTIFICATION_ID_RANGE.toLong())).toInt()

        val intent = Intent(context, NetworkSurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_WATCHLIST_HISTORY, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = context.getString(R.string.watchlist_alert_content, label, rssi)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_watchlist)
            .setContentTitle(context.getString(R.string.watchlist_alert_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 250, 250))

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notificationBuilder.build())
            }
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted. The hit is still recorded to history, and the UI
            // surfaces a "notifications blocked" warning, so this is logged and swallowed.
            Timber.w(e, "Unable to show watchlist notification, notification permission denied")
        } catch (e: Exception) {
            Timber.e(e, "Error showing watchlist notification")
        }
    }
}
