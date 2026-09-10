/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.craxiom.networksurvey.logging.db.uploader;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.notification.NotificationChannels;

/**
 * Builds the community (OpenCelliD / BeaconDB) upload progress notification: a low importance row
 * with a determinate progress bar, a Cancel action, and a tap that lands on the dashboard where the
 * upload results are shown.
 * <p>
 * This class was pulled from the Tower Collector app and modified to work with Network Survey.
 * <p>
 * See: <a href="https://github.com/zamojski/TowerCollector/blob/7c8c4ff7bc2a536a94a34e059189f905ecd52b34/app/src/main/java/info/zamojski/soft/towercollector/uploader/UploaderNotificationHelper.java">here</a>
 */
public class UploaderNotificationHelper
{
    private final Context context;
    private final NotificationCompat.Builder builder;

    public UploaderNotificationHelper(Context context)
    {
        this.context = context;
        builder = new NotificationCompat.Builder(context, NetworkSurveyConstants.UPLOADER_NOTIFICATION_CHANNEL_ID);
    }

    /**
     * Creates the initial "Starting upload" notification, registering the channel first in case
     * this is the first run after install.
     */
    public Notification createNotification(NotificationManager notificationManager)
    {
        NotificationChannels.INSTANCE.createUploaderChannel(context);
        String notificationText = context.getString(R.string.uploader_starting);
        return prepareNotification(notificationText);
    }

    public Notification updateNotificationProgress(int progress, int max)
    {
        String notificationText = context.getString(R.string.uploader_notification_progress_info, progress);
        builder.setContentText(notificationText);
        builder.setProgress(max, progress, false);
        return builder.build();
    }

    private Notification prepareNotification(String notificationText)
    {
        // set style
        builder.setSmallIcon(R.drawable.ic_upload_24);
        builder.setColor(context.getResources().getColor(R.color.md_theme_primary, null));
        builder.setWhen(System.currentTimeMillis());
        builder.setOnlyAlertOnce(true);
        // set intent
        PendingIntent mainActivityIntent = createOpenMainActivityIntent();
        builder.setContentIntent(mainActivityIntent);
        // set message
        builder.setContentTitle(context.getString(R.string.uploader_notification_title));
        builder.setContentText(notificationText);
        builder.setTicker(notificationText);
        // set action
        PendingIntent cancelUploaderIntent = createCancelUploaderIntent();
        NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(R.drawable.menu_stop, context.getString(R.string.cancel), cancelUploaderIntent).build();
        builder.addAction(stopAction);
        return builder.build();
    }

    /**
     * Tapping the upload row opens the dashboard, where the upload card shows the queue and the
     * result of the last upload.
     */
    private PendingIntent createOpenMainActivityIntent()
    {
        Intent intent = new Intent(context, NetworkSurveyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_DASHBOARD, true);
        return PendingIntent.getActivity(context, NetworkSurveyConstants.UPLOADER_NOTIFICATION_ID, intent,
                getImmutablePendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private PendingIntent createCancelUploaderIntent()
    {
        Intent intent = new Intent(UploadStopReceiverKt.STOP_UPLOADER);
        intent.setPackage(context.getPackageName());
        return PendingIntent.getBroadcast(context, 0, intent, getImmutablePendingIntentFlags(0));
    }

    private int getImmutablePendingIntentFlags(int flags)
    {
        return flags | PendingIntent.FLAG_IMMUTABLE;
    }
}
