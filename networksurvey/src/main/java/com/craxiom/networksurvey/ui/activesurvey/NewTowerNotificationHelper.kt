package com.craxiom.networksurvey.ui.activesurvey

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import timber.log.Timber

/**
 * Helper class for creating and managing new tower detection notifications.
 */
object NewTowerNotificationHelper {

    private const val CHANNEL_ID = "new_tower_alerts"
    private const val CHANNEL_NAME = "New Tower Alerts"
    private const val CHANNEL_DESCRIPTION = "Notifications when new cellular towers are detected"
    private const val NOTIFICATION_ID_BASE = 10000

    private var notificationIdCounter = NOTIFICATION_ID_BASE

    /**
     * Create the notification channel for new tower alerts.
     * Must be called before showing notifications on Android O+.
     */
    fun createNotificationChannel(context: Context) {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
            setShowBadge(true)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        Timber.d("New tower notification channel created")
    }

    /**
     * Show a notification for a newly detected tower.
     *
     * @param context Application context
     * @param mcc Mobile Country Code
     * @param mnc Mobile Network Code
     * @param area TAC/LAC
     * @param cellId Cell ID
     * @param technology Radio technology (LTE, NR, GSM, UMTS)
     */
    fun showNewTowerNotification(
        context: Context,
        mcc: Int,
        mnc: Int,
        area: Int,
        cellId: Long,
        technology: String
    ) {
        // Ensure channel exists
        createNotificationChannel(context)

        // Create intent to open the app when notification is tapped
        val intent = Intent(context, NetworkSurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cell_tower)
            .setContentTitle("New Tower Detected")
            .setContentText("$technology: MCC-MNC: $mcc-$mnc, Cell ID: $cellId")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("New $technology tower detected!\nMCC-MNC: $mcc-$mnc\nTAC/LAC: $area\nCell ID: $cellId")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(false) // Don't dismiss when tapped
            .setOngoing(false) // Can be swiped away
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 250, 250))

        // Show the notification
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationIdCounter++, notificationBuilder.build())
            }

            Timber.i("New tower notification shown for $technology tower: MCC=$mcc, MNC=$mnc, CID=$cellId")

            // Reset counter if it gets too high
            if (notificationIdCounter > NOTIFICATION_ID_BASE + 1000) {
                notificationIdCounter = NOTIFICATION_ID_BASE
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied for showing notification")
        } catch (e: Exception) {
            Timber.e(e, "Error showing new tower notification")
        }
    }
}