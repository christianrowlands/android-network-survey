package com.craxiom.networksurvey.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.craxiom.networksurvey.CalculationUtils;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.SurveyRecordLogger;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.ISurveyRecordListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.location.LocationManager.GPS_PROVIDER;

/**
 * This service is responsible for getting access to the Android {@link TelephonyManager} and periodically getting the
 * list of cellular towers the phone can see.  It then notifies any listeners of the cellular survey records.
 */
public class NetworkSurveyService extends Service
{
    private static final String LOG_TAG = NetworkSurveyService.class.getSimpleName();

    private static final int NETWORK_DATA_REFRESH_RATE_MS = 2_000;
    private static final int PING_RATE_MS = 10_000;

    private final AtomicBoolean done = new AtomicBoolean(false);
    private final AtomicBoolean loggingEnabled = new AtomicBoolean(false);
    private String deviceId;
    private SurveyServiceBinder surveyServiceBinder;
    private SurveyRecordProcessor surveyRecordProcessor;
    private GpsListener gpsListener;
    private SurveyRecordLogger surveyRecordLogger;

    public NetworkSurveyService()
    {
        surveyServiceBinder = new SurveyServiceBinder();
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        Log.i(LOG_TAG, "Creating the Network Survey Service");

        deviceId = createDeviceId();
        surveyRecordLogger = new SurveyRecordLogger(this);

        initializeLocationListener();
        initializeSurveyRecordScanning();

        updateServiceNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        checkLocationProvider();

        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return surveyServiceBinder;
    }

    @Override
    public void onDestroy()
    {
        Log.i(LOG_TAG, "onDestroy");
        setDone();
        removeLocationListener();
        shutdownNotifications();
        super.onDestroy();
    }

    /**
     * This is called if the user force-kills the app
     * <p>
     * TODO I am not sure if this method is needed.  We might be able to use ServiceInfo#FLAG_STOP_WITH_TASK instead
     */
    @Override
    public void onTaskRemoved(Intent rootIntent)
    {
        Log.i(LOG_TAG, "onTaskRemoved (the app might have been force killed)");
        setDone();
        removeLocationListener();
        shutdownNotifications();
        stopSelf();
        super.onTaskRemoved(rootIntent);
        Log.i(LOG_TAG, "onTaskRemoved complete");
    }

    public GpsListener getGpsListener()
    {
        return gpsListener;
    }

    public String getDeviceId()
    {
        return deviceId;
    }

    public void registerSurveyRecordListener(ISurveyRecordListener surveyRecordListener)
    {
        if (surveyRecordProcessor != null) surveyRecordProcessor.registerSurveyRecordListener(surveyRecordListener);
    }

    public void unregisterSurveyRecordListener(ISurveyRecordListener surveyRecordListener)
    {
        if (surveyRecordProcessor != null) surveyRecordProcessor.unregisterSurveyRecordListener(surveyRecordListener);

        // Check to see if this service is still needed.  It is still needed if we are either logging, the UI is
        // visible, or a server connection is active.
        if (!loggingEnabled.get() && !surveyRecordProcessor.isBeingUsed()) stopSelf();
    }

    /**
     * Whenever the UI is visible, we need to pass information to it so it can be displayed to the user.
     *
     * @param networkSurveyActivity The activity that is now visible to the user.
     */
    public void onUiVisible(NetworkSurveyActivity networkSurveyActivity)
    {
        if (surveyRecordProcessor != null) surveyRecordProcessor.onUiVisible(networkSurveyActivity);
    }

    /**
     * The UI is no longer visible, so don't send any updates to the UI.
     */
    public void onUiHidden()
    {
        if (surveyRecordProcessor != null) surveyRecordProcessor.onUiHidden();
    }

    /**
     * Toggles the logging setting.  If it is currently disabled, then an attempt will be made to enable logging.  If
     * logging is already enabled then it will be turned off.
     * <p>
     * It is possible that an error occurs while trying to enable logging.  In that event false will be returned
     * indicating that logging is still not enabled.
     *
     * @return The new state of logging.  True if it is enabled, or false if it is disabled.  Null is returned if the
     * toggling was unsuccessful.
     */
    public Boolean toggleLogging()
    {
        synchronized (loggingEnabled)
        {
            final boolean originalLoggingState = loggingEnabled.get();
            final boolean successful = surveyRecordLogger.enableLogging(!originalLoggingState);
            loggingEnabled.set(successful != originalLoggingState);
            updateServiceNotification();
            return successful ? loggingEnabled.get() : null;
        }
    }

    public boolean isLoggingEnabled()
    {
        return loggingEnabled.get();
    }

    /**
     * Sends out a ping to the Google DNS IP Address (8.8.8.8) every n seconds.  This allow the data connection to
     * stay alive, which will enable us to get Timing Advance information.
     */
    public void initializePing()
    {
        final Handler handler = new Handler();

        handler.postDelayed(new Runnable()
        {
            public void run()
            {
                try
                {
                    if (!loggingEnabled.get()) return;

                    sendPing();

                    handler.postDelayed(this, PING_RATE_MS);
                } catch (Exception e)
                {
                    Log.e(LOG_TAG, "An exception occurred trying to send out a ping", e);
                }
            }
        }, NETWORK_DATA_REFRESH_RATE_MS);
    }

    /**
     * @return The Android ID associated with this device and app.
     */
    @SuppressLint("HardwareIds")
    private String createDeviceId()
    {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * Registers with the Android {@link LocationManager} for location updates.
     */
    private void initializeLocationListener()
    {
        if (gpsListener != null) return;

        gpsListener = new GpsListener();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }

        checkLocationProvider();

        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null)
        {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, (long) NETWORK_DATA_REFRESH_RATE_MS, 0f, gpsListener);
        }
    }

    /**
     * Checks that the location provider is enabled and that the location permission has been granted.  If GPS location
     * is not enabled on this device, then the settings UI is opened so the user can enable it.
     */
    private void checkLocationProvider()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Log.w(LOG_TAG, "The ACCESS_FINE_LOCATION permission has not been granted");
            return;
        }

        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        final LocationProvider locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
        if (locationProvider == null)
        {
            final String noGpsMessage = getString(R.string.no_gps_device);
            Log.w(LOG_TAG, noGpsMessage);
            Toast.makeText(getApplicationContext(), noGpsMessage, Toast.LENGTH_LONG).show();
        } else if (!locationManager.isProviderEnabled(GPS_PROVIDER))
        {
            // gps exists, but isn't on
            final String turnOnGpsMessage = getString(R.string.turn_on_gps);
            Log.w(LOG_TAG, turnOnGpsMessage);
            Toast.makeText(getApplicationContext(), turnOnGpsMessage, Toast.LENGTH_LONG).show();

            // Allow the user to turn on the GPS
            final Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            try
            {
                startActivity(myIntent);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "An exception occurred trying to start the location activity: ", e);
            }
        }
    }

    /**
     * Removes the location listener from the Android {@link LocationManager}.
     */
    private void removeLocationListener()
    {
        if (gpsListener != null)
        {
            final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) locationManager.removeUpdates(gpsListener);
        }
    }

    /**
     * Gets the {@link TelephonyManager}, and then creates the
     * {@link SurveyRecordProcessor} instance.  If something goes wrong getting access to the manager
     * then the {@link SurveyRecordProcessor} instance will not be created.
     */
    private void initializeSurveyRecordScanning()
    {
        final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager == null || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
        {
            Log.w(LOG_TAG, "Unable to get access to the Telephony Manager.  No network information will be displayed");
            return;
        }

        surveyRecordProcessor = new SurveyRecordProcessor(gpsListener, deviceId);

        final Handler handler = new Handler();

        handler.postDelayed(new Runnable()
        {
            public void run()
            {
                try
                {
                    if (done.get())
                    {
                        Log.i(LOG_TAG, "Stopping the handler that pulls the latest cellular information");
                        return;
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    {
                        final TelephonyManager.CellInfoCallback cellInfoCallback = new TelephonyManager.CellInfoCallback()
                        {
                            @Override
                            public void onCellInfo(@NonNull List<CellInfo> cellInfo)
                            {
                                surveyRecordProcessor.onCellInfoUpdate(cellInfo, CalculationUtils.getNetworkType(telephonyManager.getNetworkType()));
                            }

                            @Override
                            public void onError(int errorCode, @Nullable Throwable detail)
                            {
                                super.onError(errorCode, detail);
                                Log.w(LOG_TAG, "Received an error from the Telephony Manager when requesting a cell info update; errorCode=" + errorCode, detail);
                            }
                        };
                        telephonyManager.requestCellInfoUpdate(AsyncTask.THREAD_POOL_EXECUTOR, cellInfoCallback);
                    } else
                    {
                        surveyRecordProcessor.onCellInfoUpdate(telephonyManager.getAllCellInfo(), CalculationUtils.getNetworkType(telephonyManager.getNetworkType()));
                    }

                    handler.postDelayed(this, NETWORK_DATA_REFRESH_RATE_MS);
                } catch (SecurityException e)
                {
                    Log.e(LOG_TAG, "Could not get the required permissions to get the network details", e);
                }
            }
        }, NETWORK_DATA_REFRESH_RATE_MS);
    }

    /**
     * Sends out a ping to the Google DNS IP Address (8.8.8.8).
     */
    private void sendPing()
    {
        try
        {
            Runtime runtime = Runtime.getRuntime();
            Process ipAddressProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");
            int exitValue = ipAddressProcess.waitFor();
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) Log.v(LOG_TAG, "Ping Exit Value: " + exitValue);
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "An exception occurred trying to send out a ping ", e);
        }
    }

    /**
     * A notification for this service that is started in the foreground so that we can continue to get GPS location updates while the phone is locked
     * or the app is not in the foreground.
     */
    private synchronized void updateServiceNotification()
    {
        startForeground(NetworkSurveyConstants.LOGGING_NOTIFICATION_ID, buildNotification(loggingEnabled.get()));
    }

    private synchronized Notification buildNotification(boolean logging)
    {
        Intent notificationIntent = new Intent(this, NetworkSurveyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        final CharSequence notificationTitle = getText(logging ? R.string.logging_notification_title : R.string.service_notification_title);

        return new Notification.Builder(this, NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setContentText(logging ? getText(R.string.logging_notification_text) : "")
                .setOngoing(true)
                .setSmallIcon(logging ? R.drawable.logging_thick_icon : R.drawable.gps_map_icon)
                .setContentIntent(pendingIntent)
                .setTicker(notificationTitle)
                .build();
    }

    /**
     * Sets the atomic done flag so that any handler loops can be stopped.
     */
    public void setDone()
    {
        done.set(true);
    }

    /**
     * Close out the notification since we no longer need this service.
     */
    private void shutdownNotifications()
    {
        stopForeground(true);
    }

    /**
     * Class used for the client Binder.  Because we know this service always runs in the same process as its clients,
     * we don't need to deal with IPC.
     */
    public class SurveyServiceBinder extends Binder
    {
        public NetworkSurveyService getService()
        {
            return NetworkSurveyService.this;
        }
    }
}
