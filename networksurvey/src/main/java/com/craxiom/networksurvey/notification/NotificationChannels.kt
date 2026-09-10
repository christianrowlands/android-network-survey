package com.craxiom.networksurvey.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.services.watchlist.WatchlistNotificationHelper
import com.craxiom.networksurvey.ui.activesurvey.NewTowerNotificationHelper
import com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsNotificationHelper
import timber.log.Timber

/**
 * The one place that registers every notification channel this app uses.
 *
 * Channels are registered eagerly at startup so they all appear in the system notification
 * settings before their first notification fires, and every display name comes from a string
 * resource so it follows the device language. Channel ids are constants and never change once
 * shipped, because the OS keys the user's per-channel settings on them. Creating an existing
 * channel again is a no-op apart from refreshing its name and description, so this is safe to
 * call repeatedly.
 */
object NotificationChannels {

    /**
     * Register every channel and remove channels that older versions created but no longer use.
     */
    fun createAll(context: Context) {
        val notificationManager = notificationManager(context) ?: return

        createSurveyChannel(context, notificationManager)
        createUploaderChannel(context, notificationManager)
        NsAnalyticsNotificationHelper.createNotificationChannel(context)
        NewTowerNotificationHelper.createNotificationChannel(context)
        WatchlistNotificationHelper.createNotificationChannel(context)

        notificationManager.deleteNotificationChannel(NetworkSurveyConstants.LEGACY_UPLOAD_WORKER_CHANNEL_ID)
    }

    /**
     * Register the low importance channel used by the community (OpenCelliD / BeaconDB) upload
     * progress notification. Called eagerly from [createAll] and lazily by the uploader itself.
     */
    fun createUploaderChannel(context: Context) {
        notificationManager(context)?.let { createUploaderChannel(context, it) }
    }

    private fun createSurveyChannel(context: Context, notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun createUploaderChannel(context: Context, notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NetworkSurveyConstants.UPLOADER_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.uploader_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun notificationManager(context: Context): NotificationManager? {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager == null) {
            Timber.wtf("The Notification Manager could not be retrieved to register the notification channels")
        }
        return manager
    }
}
