package com.craxiom.networksurvey;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkSurveyService extends IntentService
{
    private static final String LOG_TAG = NetworkSurveyService.class.getSimpleName();

    private static final int SERVICE_NOTIFICATION_ID = 1;
    private static final int LOGGING_NOTIFICATION_ID = 2;
    private static final int CONNECTION_NOTIFICATION_ID = 3;
    public static final String NOTIFICATION_CHANNEL_ID = "network_survey_notification";

    private ConnectionServiceBinder connectionServiceBinder;

    private final AtomicBoolean done = new AtomicBoolean(false);

    public NetworkSurveyService()
    {
        super("NetworkSurveyService");

        connectionServiceBinder = new ConnectionServiceBinder();
    }

    @Override
    protected void onHandleIntent(Intent intent)
    {
        while (!done.get())
        {
            try
            {
                Thread.sleep(15_000L);
            } catch (final InterruptedException ex)
            {
                Log.d(LOG_TAG, "The Network Survey Service worker thread was interrupted");
            }
        }

        Log.i(LOG_TAG, "Network Survey Service finished");
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        // We need at least one notification to be started in the foreground for the GPS updates to happen when the app is not in the foreground
        // This generic GPS notification serves that purpose.  Another approach might be to update one notification with both logging and connection details
        addServiceNotification();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return connectionServiceBinder;
    }

    @Override
    public void onDestroy()
    {
        Log.i(LOG_TAG, "onDestroy");
        // Make sure our notification is gone.
        shutdownNotifications();
        setDone();
        super.onDestroy();
    }

    /**
     * This is called if the user force-kills the app
     */
    @Override
    public void onTaskRemoved(Intent rootIntent)
    {
        Log.i(LOG_TAG, "onTaskRemoved (the app might have been force killed)");
        setDone();
        shutdownNotifications();
        stopSelf();
        super.onTaskRemoved(rootIntent);
        Log.i(LOG_TAG, "onTaskRemoved complete");
    }

    /**
     * Creates the persistent notification for the server connection.
     */
    public void addLoggingNotification()
    {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        Intent notificationIntent = new Intent(this, NetworkSurveyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.logging_notification_title))
                .setContentText(getText(R.string.logging_notification_text))
                .setOngoing(true)
                .setSmallIcon(R.drawable.logging_thick_icon)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.logging_notification_title))
                .build();

        notificationManager.notify(LOGGING_NOTIFICATION_ID, notification);
    }

    /**
     * Creates the persistent notification for the server connection.
     */
    public void addConnectionNotification()
    {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        Intent notificationIntent = new Intent(this, NetworkSurveyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.connection_notification_title))
                .setContentText(getText(R.string.connection_notification_text))
                .setOngoing(true)
                .setSmallIcon(R.drawable.connection_icon)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.connection_notification_title))
                .build();

        notificationManager.notify(CONNECTION_NOTIFICATION_ID, notification);
    }

    public void removeLoggingNotification()
    {
        removeNotification(LOGGING_NOTIFICATION_ID);
    }

    public void removeConnectionNotification()
    {
        removeNotification(CONNECTION_NOTIFICATION_ID);
    }

    /**
     * A notification for this service that is started in the foreground so that we can continue to get GPS location updates while the phone is locked
     * or the app is not in the foreground.
     */
    private void addServiceNotification()
    {
        Intent notificationIntent = new Intent(this, NetworkSurveyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.service_notification_title))
                .setOngoing(true)
                .setSmallIcon(R.drawable.gps_map_icon)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.service_notification_title))
                .build();

        startForeground(SERVICE_NOTIFICATION_ID, notification);
    }

    /**
     * Removes the notification identified by the provided notification ID.
     *
     * @param notificationId The ID of the notification to remove.
     */
    private void removeNotification(int notificationId)
    {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        notificationManager.cancel(notificationId);
    }

    public void setDone()
    {
        done.set(true);
    }

    private void shutdownNotifications()
    {
        removeLoggingNotification();
        removeConnectionNotification();
        stopForeground(true);
    }

    /**
     * Class used for the client Binder.  Because we know this service always runs in the same process as its clients, we don't need to deal with IPC.
     */
    class ConnectionServiceBinder extends Binder
    {
        NetworkSurveyService getService()
        {
            return NetworkSurveyService.this;
        }
    }
}
