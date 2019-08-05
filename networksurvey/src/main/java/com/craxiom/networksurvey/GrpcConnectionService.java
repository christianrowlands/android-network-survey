package com.craxiom.networksurvey;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.craxiom.networksurvey.NetworkSurveyActivity.NOTIFICATION_CHANNEL_ID;

/**
 * A Service for showing a persistent notification when the gRPC connection is active.
 * <p>
 * This class was modeled after the WiGLE App WigleService class:
 * https://github.com/wiglenet/wigle-wifi-wardriving/blob/master/wiglewifiwardriving/src/main/java/net/wigle/wigleandroid/WigleService.java
 *
 * @since 0.0.4
 */
public final class GrpcConnectionService extends Service
{
    // TODO After comparing the WiGLE Service with the Android Developer Docs, it seems that there are some better practices that we can follow.  See
    // https://developer.android.com/guide/components/services.html#Foreground

    private static final String LOG_TAG = GrpcConnectionService.class.getSimpleName();

    private GuardThread guardThread;
    private final AtomicBoolean done = new AtomicBoolean(false);
    private Bitmap largeIcon = null;
    // Binder given to clients
    private final IBinder connectionServiceBinder = new ConnectionServiceBinder();

    private ConnectionState connectionState;

    private class GuardThread extends Thread
    {
        GuardThread()
        {
        }

        @Override
        public void run()
        {
            Thread.currentThread().setName("GuardThread-" + Thread.currentThread().getName());
            while (!done.get())
            {
                NetworkSurveyActivity.sleep(15_000L);
                setupNotification();
            }
            Log.i(LOG_TAG, "GuardThread done");
        }
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    class ConnectionServiceBinder extends Binder
    {
        GrpcConnectionService getService()
        {
            // Return this instance of LocalService so clients can call public methods
            return GrpcConnectionService.this;
        }
    }

    private void setDone()
    {
        done.set(true);
        guardThread.interrupt();
    }

    @Override
    public IBinder onBind(final Intent intent)
    {
        Log.i(LOG_TAG, "service: onbind. intent: " + intent);
        return connectionServiceBinder;
    }

    @Override
    public void onRebind(final Intent intent)
    {
        Log.i(LOG_TAG, "service: onRebind. intent: " + intent);
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(final Intent intent)
    {
        Log.i(LOG_TAG, "service: onUnbind. intent: " + intent);
        shutdownNotification();
        stopSelf();
        return super.onUnbind(intent);
    }

    /**
     * This is called if the user force-kills the app
     */
    @Override
    public void onTaskRemoved(Intent rootIntent)
    {
        Log.i(LOG_TAG, "onTaskRemoved (the app might have been force killed)");
        if (!done.get())
        {
            setDone();
        }
        shutdownNotification();
        stopSelf();
        super.onTaskRemoved(rootIntent);
        Log.i(LOG_TAG, "onTaskRemoved complete");
    }

    @Override
    public void onCreate()
    {
        Log.i(LOG_TAG, "onCreate");

        setupNotificationChannel();

        setupNotification();

        // don't use guard thread
        guardThread = new GuardThread();
        guardThread.start();
        super.onCreate();
    }

    @Override
    public void onDestroy()
    {
        Log.i(LOG_TAG, "onDestroy");
        // Make sure our notification is gone.
        shutdownNotification();
        setDone();
        super.onDestroy();
    }

    @Override
    public void onLowMemory()
    {
        super.onLowMemory();
        Log.i(LOG_TAG, "onLowMemory");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i(LOG_TAG, "onStartCommand");
        setupNotification();
        // We want this service to continue running until it is explicitly stopped, so return sticky.
        return Service.START_STICKY;
    }

    private void setupNotificationChannel()
    {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                getText(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    private void shutdownNotification()
    {
        stopForeground(true);
    }

    /**
     * Setup the notification that will tell the user that a connection is active, and give them options for stopping the connection.
     */
    private void setupNotification()
    {
        Intent notificationIntent = new Intent(this, NetworkSurveyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.connection_service_title))
                .setContentText(getText(R.string.connection_notification_text))
                .setLargeIcon(largeIcon)
                .setSmallIcon(R.drawable.connection_icon)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.connection_service_title))
                .build();

        startForeground(2, notification);
    }



    /*public void setupNotification()
    {
        if (!done.get())
        {
            final long when = System.currentTimeMillis();
            final Context context = getApplicationContext();
            final String title = context.getString(R.string.connection_service);

            final Intent notificationIntent = new Intent(this, NetworkSurveyActivity.class);
            final PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            final long dbNets = ListFragment.lameStatic.dbNets;
            String text = context.getString(R.string.list_waiting_gps);
            if (dbNets > 0)
            {
                long runNets = ListFragment.lameStatic.runNets + ListFragment.lameStatic.runBt;
                long newNets = ListFragment.lameStatic.newNets;
                text = context.getString(R.string.run) + ": " + runNets
                        + "  " + context.getString(R.string.new_word) + ": " + newNets
                        + "  " + context.getString(R.string.db) + ": " + dbNets;
            }
            if (!MainActivity.isScanning(context))
            {
                text = context.getString(R.string.list_scanning_off) + " " + text;
            }
            if (largeIcon == null)
            {
                largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.wiglewifi);
            }

            final Intent pauseSharedIntent = new Intent();
            pauseSharedIntent.setAction("net.wigle.wigleandroid.PAUSE");
            pauseSharedIntent.setClass(getApplicationContext(), net.wigle.wigleandroid.listener.ScanControlReceiver.class);

            MainActivity ma = MainActivity.getMainActivity();
            Notification notification = null;

            if (null != ma)
            {
                final PendingIntent pauseIntent = PendingIntent.getBroadcast(MainActivity.getMainActivity(), 0, pauseSharedIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                final Intent scanSharedIntent = new Intent();
                scanSharedIntent.setAction("net.wigle.wigleandroid.SCAN");
                scanSharedIntent.setClass(getApplicationContext(), net.wigle.wigleandroid.listener.ScanControlReceiver.class);
                final PendingIntent scanIntent = PendingIntent.getBroadcast(MainActivity.getMainActivity(), 0, scanSharedIntent, PendingIntent.FLAG_CANCEL_CURRENT);

                final Intent uploadSharedIntent = new Intent();
                uploadSharedIntent.setAction("net.wigle.wigleandroid.UPLOAD");
                uploadSharedIntent.setClass(getApplicationContext(), net.wigle.wigleandroid.listener.UploadReceiver.class);
                final PendingIntent uploadIntent = PendingIntent.getBroadcast(MainActivity.getMainActivity(), 0, uploadSharedIntent, PendingIntent.FLAG_CANCEL_CURRENT);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                {
                    notification = getNotification26(title, context, text, when, contentIntent, pauseIntent, scanIntent, uploadIntent);
                } else
                {
                    @SuppressWarnings("deprecation") final NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
                    builder.setContentIntent(contentIntent);
                    builder.setNumber((int) ListFragment.lameStatic.newNets);
                    builder.setTicker(title);
                    builder.setContentTitle(title);
                    builder.setContentText(text);
                    builder.setWhen(when);
                    builder.setLargeIcon(largeIcon);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    {
                        builder.setSmallIcon(R.drawable.wiglewifi_small_white);
                    } else
                    {
                        builder.setSmallIcon(R.drawable.wiglewifi_small);
                    }
                    builder.setOngoing(true);
                    builder.setCategory("SERVICE");
                    builder.setPriority(NotificationCompat.PRIORITY_LOW);
                    builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                    builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent);
                    builder.addAction(android.R.drawable.ic_media_play, "Scan", scanIntent);
                    builder.addAction(android.R.drawable.ic_menu_upload, "Upload", uploadIntent);

                    try
                    {
                        //ALIBI: https://stackoverflow.com/questions/43123466/java-lang-nullpointerexception-attempt-to-invoke-interface-method-java-util-it
                        notification = builder.build();
                    } catch (NullPointerException npe)
                    {
                        MainActivity.error("NPE trying to build notification. " + npe.getMessage());
                    }
                }
            }
            if (null != notification)
            {
                try
                {
                    startForeground(NOTIFICATION_ID, notification);
                } catch (Exception ex)
                {
                    MainActivity.error("notification service error: ", ex);
                }
            } else
            {
                MainActivity.info("null notification - skipping startForeground");
            }
        }
    }

    private Notification getNotification26(final String title, final Context context, final String text,
                                           final long when, final PendingIntent contentIntent,
                                           final PendingIntent pauseIntent, final PendingIntent scanIntent,
                                           final PendingIntent uploadIntent)
    {
        // new notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            final NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) return null;

            final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    title, NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);

            // copied from above
            final Notification.Builder builder = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
            builder.setContentIntent(contentIntent);
            builder.setNumber((int) ListFragment.lameStatic.newNets);
            builder.setTicker(title);
            builder.setContentTitle(title);
            builder.setContentText(text);
            builder.setWhen(when);
            builder.setLargeIcon(largeIcon);
            builder.setSmallIcon(R.drawable.wiglewifi_small_white);
            builder.setOngoing(true);
            builder.setCategory("SERVICE");
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
            builder.setColorized(true);
            // WiGLE Blue builder.setColor(6005486);
            builder.setColor(1973790);

            //TODO: figure out how to update notification actions on exec, then we can show relevant
            if (MainActivity.isScanning(getApplicationContext()))
            {
                Notification.Action pauseAction = new Notification.Action.Builder(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
                        .build();
                builder.addAction(pauseAction);
            } else
            {
                Notification.Action scanAction = new Notification.Action.Builder(android.R.drawable.ic_media_play, "Scan", scanIntent)
                        .build();
                builder.addAction(scanAction);
            }
            Notification.Action ulAction = new Notification.Action.Builder(android.R.drawable.ic_menu_upload, "Upload", uploadIntent)
                    .build();
            builder.addAction(ulAction);

            return builder.build();
        }
        return null;
    }*/
}
