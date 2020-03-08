package com.craxiom.networksurvey.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.CalculationUtils;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.SurveyRecordLogger;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.ISurveyRecordListener;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This service is responsible for getting access to the Android {@link TelephonyManager} and periodically getting the
 * list of cellular towers the phone can see.  It then notifies any listeners of the cellular survey records.
 */
public class NetworkSurveyService extends Service
{
    private static final String LOG_TAG = NetworkSurveyService.class.getSimpleName();

    /**
     * Time to wait between first location measurement received before considering this device does
     * not likely support raw GNSS collection.
     */
    private static final long TIME_TO_WAIT_FOR_GNSS_RAW_BEFORE_FAILURE = 1000L * 15L;
    private static final int NETWORK_DATA_REFRESH_RATE_MS = 2_000;
    private static final int PING_RATE_MS = 10_000;

    private final AtomicBoolean done = new AtomicBoolean(false);
    private final AtomicBoolean cellularLoggingEnabled = new AtomicBoolean(false);
    private final AtomicBoolean gnssLoggingEnabled = new AtomicBoolean(false);
    private final AtomicBoolean gnssStarted = new AtomicBoolean(false);
    private String deviceId;
    private SurveyServiceBinder surveyServiceBinder;
    private SurveyRecordProcessor surveyRecordProcessor;
    private GpsListener gpsListener;
    private SurveyRecordLogger surveyRecordLogger;
    private GnssGeoPackageRecorder gnssGeoPackageRecorder = null;
    private LocationManager locationManager = null;
    private long firstGpsAcqTime = Long.MIN_VALUE;
    private boolean gnssRawSupportKnown = false;
    private boolean hasGnssRawFailureNagLaunched = false;

    /**
     * Callback for receiving GNSS measurements from the location manager.
     */
    private final GnssMeasurementsEvent.Callback measurementListener = new GnssMeasurementsEvent.Callback()
    {
        public void onGnssMeasurementsReceived(GnssMeasurementsEvent event)
        {
            gnssRawSupportKnown = true;
            if (gnssGeoPackageRecorder != null) gnssGeoPackageRecorder.onGnssMeasurementsReceived(event);
        }
    };

    /**
     * Callback for receiving GNSS status from the location manager.
     */
    private final GnssStatus.Callback statusListener = new GnssStatus.Callback()
    {
        public void onSatelliteStatusChanged(final GnssStatus status)
        {
            if (gnssGeoPackageRecorder != null) gnssGeoPackageRecorder.onSatelliteStatusChanged(status);
        }
    };

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
        stopGnssLogging();
        if (gnssGeoPackageRecorder != null)
        {
            gnssGeoPackageRecorder.shutdown();
            gnssGeoPackageRecorder = null;
        }
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
        if (!cellularLoggingEnabled.get() && !surveyRecordProcessor.isBeingUsed()) stopSelf();
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
     * Toggles the cellular logging setting.  If it is currently disabled, then an attempt will be made to enable
     * logging.  If logging is already enabled then it will be turned off.
     * <p>
     * It is possible that an error occurs while trying to enable logging.  In that event false will be returned
     * indicating that logging is still not enabled.
     *
     * @return The new state of logging.  True if it is enabled, or false if it is disabled.  Null is returned if the
     * toggling was unsuccessful.
     */
    public Boolean toggleCellularLogging()
    {
        synchronized (cellularLoggingEnabled)
        {
            final boolean originalLoggingState = cellularLoggingEnabled.get();
            final boolean successful = surveyRecordLogger.enableLogging(!originalLoggingState);
            cellularLoggingEnabled.set(successful != originalLoggingState);
            updateServiceNotification();
            return successful ? cellularLoggingEnabled.get() : null;
        }
    }

    public boolean isCellularLoggingEnabled()
    {
        return cellularLoggingEnabled.get();
    }

    public boolean isGnssLoggingEnabled()
    {
        return gnssLoggingEnabled.get();
    }

    /**
     * Toggles the GNSS logging setting.  If it is currently disabled, then an attempt will be made to enable
     * logging.  If logging is already enabled then it will be turned off.
     * <p>
     * It is possible that an error occurs while trying to enable logging.  In that event false will be returned
     * indicating that logging is still not enabled.
     *
     * @return The new state of logging.  True if it is enabled, or false if it is disabled.  Null is returned if the
     * toggling was unsuccessful.
     */
    public Boolean toggleGnssLogging()
    {
        synchronized (gnssLoggingEnabled)
        {
            final boolean originalLoggingState = gnssLoggingEnabled.get();

            if (originalLoggingState)
            {
                stopGnssLogging();
            } else
            {
                startGnssLogging();
            }

            updateServiceNotification();

            final boolean newLoggingState = gnssLoggingEnabled.get();
            return newLoggingState == originalLoggingState ? null : newLoggingState;
        }
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
                    if (!cellularLoggingEnabled.get()) return;

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
     * Updates the location in the GNSS GeoPackage recorder if it is not null.
     * <p>
     * Also notifies the user if the RAW GNSS measurement timeout has expired.
     *
     * @param location The new location of this Android device.
     */
    public void updateLocation(final Location location)
    {
        if (gnssGeoPackageRecorder != null) gnssGeoPackageRecorder.onLocationChanged(location);

        if (!gnssRawSupportKnown && !hasGnssRawFailureNagLaunched)
        {
            if (firstGpsAcqTime < 0L)
            {
                firstGpsAcqTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() > firstGpsAcqTime + TIME_TO_WAIT_FOR_GNSS_RAW_BEFORE_FAILURE)
            {
                hasGnssRawFailureNagLaunched = true;

                // The user may choose to continue using the app even without GNSS since
                // they do get some satellite status on this display. If that is the case,
                // they can choose not to be nagged about this every time they launch the app.
                boolean ignoreRawGnssFailure = PreferenceUtils.getBoolean(Application.get().getString(R.string.pref_key_ignore_raw_gnss_failure), false);
                if (!ignoreRawGnssFailure)
                {
                    // TODO Convert the activity to a dialog or something similar startActivity(new Intent(NetworkSurveyService.this, RawGnssFailureActivity.class));
                }
            }
        }
    }

    /**
     * Sets the atomic done flag so that any handler loops can be stopped.
     */
    public void setDone()
    {
        done.set(true);
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

        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null)
        {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, NETWORK_DATA_REFRESH_RATE_MS, 0f, gpsListener);
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
        startForeground(NetworkSurveyConstants.LOGGING_NOTIFICATION_ID, buildNotification());
    }

    /**
     * Creates a new {@link Notification} based on the current state of this service.  The returned notification can
     * then be passed on to the Android system.
     *
     * @return A {@link Notification} that represents the current state of this service (e.g. if logging is enabled).
     */
    private synchronized Notification buildNotification()
    {
        final boolean logging = cellularLoggingEnabled.get() || gnssLoggingEnabled.get();

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
     * Starts GNSS logging if the {@link GnssGeoPackageRecorder} is not already initialized and started.
     * <p>
     * This method also handles registering the GNSS listeners with Android so we get notified of updates.
     * <p>
     * This method is not thread safe, so make sure to call this method from a synchronized block.
     */
    private void startGnssLogging()
    {
        if (gnssGeoPackageRecorder == null)
        {
            Log.i(LOG_TAG, "Starting GNSS Logging");

            gnssGeoPackageRecorder = new GnssGeoPackageRecorder(this);
            gnssGeoPackageRecorder.start();

            gnssLoggingEnabled.set(gnssGeoPackageRecorder.openGeoPackageDatabase() && registerGnssListeners());
        }
    }

    /**
     * Stops GNSS logging, removes the GNSS listeners from the Android system, and closes the GeoPackage file if it is
     * open.
     * <p>
     * This method is not thread safe, so make sure to call this method from a synchronized block.
     */
    private void stopGnssLogging()
    {
        unregisterGnssListeners();
        if (gnssGeoPackageRecorder != null)
        {
            Log.i(LOG_TAG, "Stopping GNSS Logging");

            gnssGeoPackageRecorder.shutdown();
            gnssGeoPackageRecorder = null;
        }
        gnssLoggingEnabled.set(false);
    }

    /**
     * Registers for GPS/GNSS updates.
     *
     * @return True if the listeners are registered successfully, false if something went wrong or
     */
    private boolean registerGnssListeners()
    {
        if (gnssStarted.getAndSet(true)) return true;

        boolean success = false;

        boolean hasPermissions = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        hasPermissions = hasPermissions && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        if (hasPermissions)
        {
            if (locationManager == null)
            {
                locationManager = getSystemService(LocationManager.class);
                if (locationManager != null)
                {
                    locationManager.registerGnssMeasurementsCallback(measurementListener);
                    // FIXME Figure out why this causes a NPE locationManager.registerGnssStatusCallback(statusListener);
                }

                gpsListener.addLocationListener(this);
            }

            success = true;
        }

        return success;
    }

    /**
     * Unregisters from GPS/GNSS updates.
     */
    private void unregisterGnssListeners()
    {
        if (!gnssStarted.getAndSet(false)) return;

        if (locationManager != null)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                locationManager.unregisterGnssMeasurementsCallback(measurementListener);
                locationManager.unregisterGnssStatusCallback(statusListener);
            }

            gpsListener.removeLocationListener();
            locationManager = null;
        }
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
