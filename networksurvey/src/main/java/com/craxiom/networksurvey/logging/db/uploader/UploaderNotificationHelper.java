/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.craxiom.networksurvey.logging.db.uploader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;

/**
 * This class was pulled from the Tower Collector app and modified to work with Network Survey.
 * <p>
 * See: <a href="https://github.com/zamojski/TowerCollector/blob/7c8c4ff7bc2a536a94a34e059189f905ecd52b34/app/src/main/java/info/zamojski/soft/towercollector/uploader/UploaderNotificationHelper.java">here</a>
 */
public class UploaderNotificationHelper
{
    private static final String UPLOADER_NOTIFICATION_CHANNEL_ID = "uploader_notification_channel";

    private final Context context;
    private final NotificationCompat.Builder builder;

    public UploaderNotificationHelper(Context context)
    {
        this.context = context;
        builder = new NotificationCompat.Builder(context, UPLOADER_NOTIFICATION_CHANNEL_ID);
    }

    public Notification createNotification(NotificationManager notificationManager)
    {
        createNotificationChannel(notificationManager);
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

    private PendingIntent createOpenMainActivityIntent()
    {
        //final Intent notificationIntent = new Intent(context, NetworkSurveyActivity.class);
        //final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent intent = new Intent(context, NetworkSurveyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.setAction(NsUploaderWorker.SERVICE_FULL_NAME + "_NID_" + NsUploaderWorker.NOTIFICATION_ID);
        return PendingIntent.getActivity(context, 0, intent, getImmutablePendingIntentFlags(0));
    }

    private PendingIntent createCancelUploaderIntent()
    {
        Intent intent = new Intent(UploadStopReceiverKt.STOP_UPLOADER);
        intent.setPackage(context.getPackageName());
        return PendingIntent.getBroadcast(context, 0, intent, getImmutablePendingIntentFlags(0));
    }

    private void createNotificationChannel(NotificationManager notificationManager)
    {
        NotificationChannel channel = new NotificationChannel(
                UPLOADER_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.uploader_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW); // Android will automatically promote to DEFAULT but in case they change their mind I leave it here
        notificationManager.createNotificationChannel(channel);
    }

    private int getImmutablePendingIntentFlags(int flags)
    {
        return flags | PendingIntent.FLAG_IMMUTABLE;
    }
}
