package com.craxiom.networksurvey.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.CalculationUtils;
import com.craxiom.networksurvey.ConnectionState;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.SurveyRecordLogger;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IConnectionStateListener;
import com.craxiom.networksurvey.listeners.ISurveyRecordListener;
import com.craxiom.networksurvey.mqtt.MqttBrokerConnectionInfo;
import com.craxiom.networksurvey.mqtt.MqttConnection;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This service is responsible for getting access to the Android {@link TelephonyManager} and periodically getting the
 * list of cellular towers the phone can see.  It then notifies any listeners of the cellular survey records.
 * <p>
 * It also handles starting the MQTT broker connection if connection information is present.
 *
 * @since 0.0.9
 */
public class NetworkSurveyService extends Service implements IConnectionStateListener
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
    private final SurveyServiceBinder surveyServiceBinder;
    private SurveyRecordProcessor surveyRecordProcessor;
    private GpsListener gpsListener;
    private SurveyRecordLogger surveyRecordLogger;
    private GnssGeoPackageRecorder gnssGeoPackageRecorder = null;
    private Looper serviceLooper;
    private Handler serviceHandler;
    private LocationManager locationManager = null;
    private final long firstGpsAcqTime = Long.MIN_VALUE;
    private boolean gnssRawSupportKnown = false;
    private final boolean hasGnssRawFailureNagLaunched = false;
    private MqttConnection mqttConnection;
    private BroadcastReceiver managedConfigurationListener;

    /**
     * Callback for receiving GNSS measurements from the location manager.
     */
    private final GnssMeasurementsEvent.Callback measurementListener = new GnssMeasurementsEvent.Callback()
    {
        @Override
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
        @Override
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

        final HandlerThread handlerThread = new HandlerThread(LOG_TAG);
        handlerThread.start();

        serviceLooper = handlerThread.getLooper();
        serviceHandler = new Handler(serviceLooper);

        deviceId = createDeviceId();
        surveyRecordLogger = new SurveyRecordLogger(this, serviceLooper);

        initializeLocationListener();
        initializeSurveyRecordScanning();

        initializeMqttConnection();

        updateServiceNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        // If we are started at boot, then that means the NetworkSurveyActivity was never run.  Therefore, to ensure we
        // read and respect the auto start logging user preferences, we need to read them and start logging here.
        final boolean startedAtBoot = intent.getBooleanExtra(NetworkSurveyConstants.EXTRA_STARTED_AT_BOOT, false);
        if (startedAtBoot)
        {
            Log.i(LOG_TAG, "Received the startedAtBoot flag in the NetworkSurveyService. Reading the auto start logging preferences");
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            final boolean autoStartCellularLogging = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, false);
            if (autoStartCellularLogging && !cellularLoggingEnabled.get()) toggleCellularLogging(true);

            final boolean autoStartGnssLogging = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING, false);
            if (autoStartGnssLogging && !gnssLoggingEnabled.get()) toggleGnssLogging(true);
        }

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

        unregisterManagedConfigurationListener();

        if (mqttConnection != null)
        {
            unregisterMqttConnectionStateListener(this);
            disconnectFromMqttBroker();
        }
        setDone();
        removeLocationListener();
        stopGnssLogging();
        if (gnssGeoPackageRecorder != null)
        {
            gnssGeoPackageRecorder.shutdown();
            gnssGeoPackageRecorder = null;
        }
        serviceLooper.quitSafely();
        serviceHandler = null;
        shutdownNotifications();
        super.onDestroy();
    }

    @Override
    public void onConnectionStateChange(ConnectionState newConnectionState)
    {
        updateServiceNotification();
    }

    /**
     * Creates the {@link MqttConnection} instance.
     * <p>
     * If connection information is specified for an MQTT Broker via the MDM Managed Configuration, then kick off an
     * MQTT connection.
     *
     * @since 0.1.1
     */
    public void initializeMqttConnection()
    {
        mqttConnection = new MqttConnection();
        mqttConnection.registerMqttConnectionStateListener(this);

        registerManagedConfigurationListener();

        attemptMqttConnectWithMdmConfig(false);
    }

    /**
     * Connect to an MQTT broker.
     *
     * @param connectionInfo The information needed to connect to the MQTT broker.
     * @since 0.1.1
     */
    public void connectToMqttBroker(MqttBrokerConnectionInfo connectionInfo)
    {
        mqttConnection.connect(getApplicationContext(), connectionInfo);
        registerSurveyRecordListener(mqttConnection);
    }

    /**
     * Disconnect from the MQTT broker and also remove the MQTT survey record listener.
     *
     * @since 0.1.1
     */
    public void disconnectFromMqttBroker()
    {
        unregisterSurveyRecordListener(mqttConnection);
        mqttConnection.disconnect();
    }

    /**
     * If connection information is specified for an MQTT Broker via the MDM Managed Configuration, then kick off an
     * MQTT connection.
     *
     * @param forceDisconnect Set to true so that the MQTT broker connection will be shutdown even if the MDM configured
     *                        connection info is not present.  This flag is needed to stop an MQTT connection if it was
     *                        previously configured via MDM, but the config has since been removed from the MDM.  In
     *                        that case, the connection info will be null but we still want to disconnect from the MQTT
     *                        broker.
     * @since 0.1.1
     */
    public void attemptMqttConnectWithMdmConfig(boolean forceDisconnect)
    {
        if (isMqttMdmOverrideEnabled())
        {
            Log.i(LOG_TAG, "The MQTT MDM override is enabled, so no MDM configured MQTT connection will be attempted");
            return;
        }

        final MqttBrokerConnectionInfo connectionInfo = getMqttBrokerConnectionInfo();

        if (connectionInfo != null)
        {
            // Make sure there is not another connection active first, if there is, disconnect. Don't use the
            // disconnectFromMqttBroker() method because it will cause the listener to get unregistered, which will
            // cause the NetworkSurveyService to get stopped if it is the last listener/user of the service.  Since we
            // are starting the connection right back up there is not a need to remove the listener.
            mqttConnection.disconnect();

            connectToMqttBroker(connectionInfo);
        } else
        {
            Log.i(LOG_TAG, "Skipping the MQTT connection because no MQTT broker configuration has been set");

            if (forceDisconnect) disconnectFromMqttBroker();
        }
    }

    /**
     * @return The current connection state to the MQTT Broker.
     * @since 0.1.1
     */
    public ConnectionState getMqttConnectionState()
    {
        if (mqttConnection != null) return mqttConnection.getConnectionState();

        return ConnectionState.DISCONNECTED;
    }

    /**
     * Adds an {@link IConnectionStateListener} so that it will be notified of all future MQTT connection state changes.
     *
     * @param connectionStateListener The listener to add.
     */
    public void registerMqttConnectionStateListener(IConnectionStateListener connectionStateListener)
    {
        mqttConnection.registerMqttConnectionStateListener(connectionStateListener);
    }

    /**
     * Removes an {@link IConnectionStateListener} so that it will no longer be notified of MQTT connection state changes.
     *
     * @param connectionStateListener The listener to remove.
     */
    public void unregisterMqttConnectionStateListener(IConnectionStateListener connectionStateListener)
    {
        mqttConnection.unregisterMqttConnectionStateListener(connectionStateListener);
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
        if (!isBeingUsed()) stopSelf();
    }

    /**
     * Used to check if this service is still needed.
     * <p>
     * This service is still needed if logging is enabled, if the UI is visible, or if a connection is active.  In other
     * words, if there is an active consumer of the survey records.
     *
     * @return True if there is an active consumer of the survey records produced by this service, false otherwise.
     * @since 0.1.1
     */
    public boolean isBeingUsed()
    {
        return cellularLoggingEnabled.get()
                || gnssLoggingEnabled.get()
                || getMqttConnectionState() != ConnectionState.DISCONNECTED
                || GrpcConnectionService.getConnectedState() != ConnectionState.DISCONNECTED
                || surveyRecordProcessor.isBeingUsed();
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
     * Toggles the cellular logging setting.
     * <p>
     * It is possible that an error occurs while trying to enable or disable logging.  In that event null will be
     * * returned indicating that logging could not be toggled.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @return The new state of logging.  True if it is enabled, or false if it is disabled.  Null is returned if the
     * toggling was unsuccessful.
     */
    public Boolean toggleCellularLogging(boolean enable)
    {
        synchronized (cellularLoggingEnabled)
        {
            final boolean originalLoggingState = cellularLoggingEnabled.get();
            if (originalLoggingState == enable) return originalLoggingState;

            Log.i(LOG_TAG, "Toggling cellular logging to " + enable);

            final boolean successful = surveyRecordLogger.enableLogging(enable);
            if (successful)
            {
                cellularLoggingEnabled.set(enable);
                if (enable)
                {
                    registerSurveyRecordListener(surveyRecordLogger);
                } else
                {
                    unregisterSurveyRecordListener(surveyRecordLogger);
                }
            }
            updateServiceNotification();

            final boolean newLoggingState = cellularLoggingEnabled.get();
            if (successful && newLoggingState) initializePing();

            return successful ? newLoggingState : null;
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
     * Toggles the GNSS logging setting.
     * <p>
     * It is possible that an error occurs while trying to enable or disable logging.  In that event null will be
     * returned indicating that logging could not be toggled.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @return The new state of logging.  True if it is enabled, or false if it is disabled.  Null is returned if the
     * toggling was unsuccessful.
     */
    public Boolean toggleGnssLogging(boolean enable)
    {
        synchronized (gnssLoggingEnabled)
        {
            final boolean originalLoggingState = gnssLoggingEnabled.get();

            if (originalLoggingState == enable) return originalLoggingState;

            Log.i(LOG_TAG, "Toggling GNSS logging to " + enable);

            if (enable)
            {
                startGnssLogging();
            } else
            {
                stopGnssLogging();
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
        serviceHandler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    if (!cellularLoggingEnabled.get()) return;

                    sendPing();

                    serviceHandler.postDelayed(this, PING_RATE_MS);
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

        // TODO Add this back in when we can test on a device that does not support GNSS
        /*if (!gnssRawSupportKnown && !hasGnssRawFailureNagLaunched)
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
                    final GnssFailureDialogFragment gnssFailureDialogFragment = new GnssFailureDialogFragment();
                    gnssFailureDialogFragment.show();
                }
            }
        }*/
    }

    /**
     * Sets the atomic done flag so that any handler loops can be stopped.
     */
    private void setDone()
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
            Log.w(LOG_TAG, "ACCESS_FINE_LOCATION Permission not granted for the NetworkSurveyService");
            return;
        }

        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null)
        {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, NETWORK_DATA_REFRESH_RATE_MS, 0f, gpsListener);
        } else
        {
            Log.e(LOG_TAG, "The location manager was null when trying to request location updates for the NetworkSurveyService");
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

        serviceHandler.postDelayed(new Runnable()
        {
            @Override
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

                    serviceHandler.postDelayed(this, NETWORK_DATA_REFRESH_RATE_MS);
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
     * A notification for this service that is started in the foreground so that we can continue to get GPS location
     * updates while the phone is locked or the app is not in the foreground.
     */
    private void updateServiceNotification()
    {
        startForeground(NetworkSurveyConstants.LOGGING_NOTIFICATION_ID, buildNotification());
    }

    /**
     * Creates a new {@link Notification} based on the current state of this service.  The returned notification can
     * then be passed on to the Android system.
     *
     * @return A {@link Notification} that represents the current state of this service (e.g. if logging is enabled).
     */
    private Notification buildNotification()
    {
        final boolean logging = cellularLoggingEnabled.get() || gnssLoggingEnabled.get();
        final ConnectionState connectionState = mqttConnection.getConnectionState();
        final boolean mqttConnectionActive = connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING;
        final CharSequence notificationTitle = getText(R.string.network_survey_notification_title);
        final String notificationText = getNotificationText(logging, mqttConnectionActive, connectionState);

        final Intent notificationIntent = new Intent(this, NetworkSurveyActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setOngoing(true)
                .setSmallIcon(mqttConnectionActive ? R.drawable.ic_cloud_connection : logging ? R.drawable.logging_thick_icon : R.drawable.gps_map_icon)
                .setContentIntent(pendingIntent)
                .setTicker(notificationTitle)
                .setContentText(notificationText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText));

        if (connectionState == ConnectionState.CONNECTING)
        {
            builder.setColor(getResources().getColor(R.color.connectionStatusConnecting, null));
            builder.setColorized(true);
        }

        return builder.build();
    }

    /**
     * Gets the text to use for the Network Survey Service Notification.
     *
     * @param logging              True if logging is active, false if disabled.
     * @param mqttConnectionActive True if the MQTT connection is either in a connected or reconnecting state.
     * @param connectionState      The actual connection state of the MQTT broker connection.
     * @return The text that can be added to the service notification.
     * @since 0.1.1
     */
    private String getNotificationText(boolean logging, boolean mqttConnectionActive, ConnectionState connectionState)
    {
        String notificationText = "";

        if (logging)
        {
            notificationText = String.valueOf(getText(R.string.logging_notification_text)) + (mqttConnectionActive ? getText(R.string.and) : "");
        }

        switch (connectionState)
        {
            case CONNECTED:
                notificationText += getText(R.string.mqtt_connection_notification_text);
                break;
            case CONNECTING:
                notificationText += getText(R.string.mqtt_reconnecting_notification_text);
                break;
            default:
        }

        return notificationText;
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

            gnssGeoPackageRecorder = new GnssGeoPackageRecorder(this, serviceLooper);
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
                    locationManager.registerGnssStatusCallback(statusListener, serviceHandler);
                    Log.i(LOG_TAG, "Successfully registered the GNSS listeners");
                }

                gpsListener.addLocationListener(this);
            } else
            {
                Log.w(LOG_TAG, "The location manager was not null when registering the GNSS listeners");
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
     * Register a listener so that if the Managed Config changes we will be notified of the new config and can restart
     * the MQTT broker connection with the new parameters.
     *
     * @since 0.1.1
     */
    private void registerManagedConfigurationListener()
    {
        final IntentFilter restrictionsFilter = new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED);

        managedConfigurationListener = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                attemptMqttConnectWithMdmConfig(true);
            }
        };

        registerReceiver(managedConfigurationListener, restrictionsFilter);
    }

    /**
     * Remove the managed configuration listener.
     *
     * @since 0.1.1
     */
    private void unregisterManagedConfigurationListener()
    {
        if (managedConfigurationListener != null)
        {
            try
            {
                unregisterReceiver(managedConfigurationListener);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Unable to unregister the Managed Configuration Listener when pausing the app", e);
            }
            managedConfigurationListener = null;
        }
    }

    /**
     * @return True if the MQTT MDM override has been enabled by the user.  False if the MDM config should still be employed.
     */
    private boolean isMqttMdmOverrideEnabled()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_MDM_OVERRIDE, false);
    }

    /**
     * Get the MDM configured MQTT broker connection information to use to establish the connection.
     * <p>
     * If the user has specified to override the MDM connection config, then null is returned.
     *
     * @return The connection settings to use for the MQTT broker, or null if no connection information is present or
     * the user has overrode the MDM config.
     * @since 0.1.1
     */
    private MqttBrokerConnectionInfo getMqttBrokerConnectionInfo()
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            final boolean hasBrokerUri = mdmProperties.containsKey(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_URL);
            if (!hasBrokerUri) return null;

            final String mqttBrokerUri = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_URL);
            final String clientId = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_CLIENT_ID);
            final String username = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_USERNAME);
            final String password = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_PASSWORD);

            if (mqttBrokerUri == null || clientId == null)
            {
                return null;
            }

            return new MqttBrokerConnectionInfo(mqttBrokerUri, clientId, username, password);
        }

        return null;
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
