package com.craxiom.networksurvey.services;

import static com.craxiom.networksurvey.util.GpsTestUtil.getGnssTimeoutIntervalMs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.DeviceStatusData;
import com.craxiom.mqttlibrary.IConnectionStateListener;
import com.craxiom.mqttlibrary.IMqttService;
import com.craxiom.mqttlibrary.MqttConstants;
import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;
import com.craxiom.mqttlibrary.connection.ConnectionState;
import com.craxiom.mqttlibrary.connection.DefaultMqttConnection;
import com.craxiom.mqttlibrary.ui.AConnectionFragment;
import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.CdrSmsReceiver;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.DeviceStatusMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.listeners.ICdrEventListener;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGnssFailureListener;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.listeners.ILoggingChangeListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.logging.CdrLogger;
import com.craxiom.networksurvey.logging.GnssRecordLogger;
import com.craxiom.networksurvey.model.CdrEventType;
import com.craxiom.networksurvey.mqtt.MqttConnection;
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo;
import com.craxiom.networksurvey.services.controller.BluetoothController;
import com.craxiom.networksurvey.services.controller.CellularController;
import com.craxiom.networksurvey.services.controller.WifiController;
import com.craxiom.networksurvey.util.IOUtils;
import com.craxiom.networksurvey.util.MathUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * This service is responsible for getting access to the Android {@link TelephonyManager} and periodically getting the
 * list of cellular towers the phone can see.  It then notifies any listeners of the cellular survey records.
 * <p>
 * It also handles starting the MQTT broker connection if connection information is present.
 *
 * @since 0.0.9
 */
public class NetworkSurveyService extends Service implements IConnectionStateListener, SharedPreferences.OnSharedPreferenceChangeListener, IMqttService
{
    /**
     * Time to wait between first location measurement received before considering this device does
     * not likely support raw GNSS collection.
     */
    private static final long TIME_TO_WAIT_FOR_GNSS_RAW_BEFORE_FAILURE = 1000L * 15L;
    private static final Uri SMS_URI = Uri.parse("content://sms");
    public static final String SMS_COLUMN_ID = "_id";
    private static final String SMS_COLUMN_TYPE = "type";
    private static final String SMS_COLUMN_ADDRESS = "address";
    private static final int SMS_MESSAGE_TYPE_SENT = 2;

    private final AtomicBoolean deviceStatusActive = new AtomicBoolean(false);
    private final AtomicBoolean gnssLoggingEnabled = new AtomicBoolean(false);
    private final AtomicBoolean cdrLoggingEnabled = new AtomicBoolean(false);
    private final AtomicBoolean gnssStarted = new AtomicBoolean(false);
    private final AtomicBoolean cdrStarted = new AtomicBoolean(false);

    private final AtomicInteger deviceStatusGeneratorTaskId = new AtomicInteger();
    private final AtomicInteger gnssScanningTaskId = new AtomicInteger();

    private final SurveyServiceBinder surveyServiceBinder;
    private final Handler uiThreadHandler;
    private final ExecutorService executorService;

    private volatile int gnssScanRateMs;
    private volatile int deviceStatusScanRateMs;

    private CellularController cellularController;
    private WifiController wifiController;
    private BluetoothController bluetoothController;
    private String deviceId;
    private String myPhoneNumber = "";
    private SurveyRecordProcessor surveyRecordProcessor;
    private GpsListener gpsListener;
    private IGnssFailureListener gnssFailureListener;
    private GnssRecordLogger gnssRecordLogger;
    private CdrLogger cdrLogger;
    private Looper serviceLooper;
    private Handler serviceHandler;
    private LocationManager locationManager = null;
    private long firstGpsAcqTime = Long.MIN_VALUE;
    private boolean gnssRawSupportKnown = false;
    private boolean hasGnssRawFailureNagLaunched = false;
    private MqttConnection mqttConnection;
    private BroadcastReceiver managedConfigurationListener;
    private boolean mdmOverride = false;

    private GnssMeasurementsEvent.Callback measurementListener;
    private PhoneStateListener phoneStateCdrListener;
    private final Set<ILoggingChangeListener> loggingChangeListeners = new CopyOnWriteArraySet<>();

    private BroadcastReceiver smsBroadcastReceiver;
    private ContentObserver smsOutgoingObserver;
    private final LinkedHashMap<String, String> smsIdQueue = new EvictingLinkedHashMap();

    public NetworkSurveyService()
    {
        surveyServiceBinder = new SurveyServiceBinder();
        uiThreadHandler = new Handler(Looper.getMainLooper());

        executorService = Executors.newFixedThreadPool(8);
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        Timber.i("Creating the Network Survey Service");

        final Context context = getApplicationContext();

        final HandlerThread handlerThread = new HandlerThread("NetworkSurveyService");
        handlerThread.start();

        serviceLooper = handlerThread.getLooper();
        serviceHandler = new Handler(serviceLooper);

        deviceId = createDeviceId();
        gnssRecordLogger = new GnssRecordLogger(this, serviceLooper);
        cdrLogger = new CdrLogger(this, serviceLooper);

        gpsListener = new GpsListener();

        surveyRecordProcessor = new SurveyRecordProcessor(gpsListener, deviceId, context, executorService);

        cellularController = new CellularController(this, executorService, serviceLooper, serviceHandler, surveyRecordProcessor);
        wifiController = new WifiController(this, executorService, serviceLooper, serviceHandler, surveyRecordProcessor, uiThreadHandler);
        bluetoothController = new BluetoothController(this, executorService, serviceLooper, serviceHandler, surveyRecordProcessor, uiThreadHandler);

        setScanRateValues();
        readMdmOverridePreference();
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this);

        // Must register for MDM updates AFTER initializing the MQTT connection because we try to make an MQTT connection if the MDM settings change
        initializeMqttConnection();
        registerManagedConfigurationListener();

        cellularController.initializeCellularScanningResources();
        wifiController.initializeWifiScanningResources();
        bluetoothController.initializeBtScanningResources();
        initializeGnssScanningResources();

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
            Timber.i("Received the startedAtBoot flag in the NetworkSurveyService. Reading the auto start preferences");

            attemptMqttConnectionAtBoot();

            final Context applicationContext = getApplicationContext();

            final boolean autoStartCellularLogging = PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, false, applicationContext);
            if (autoStartCellularLogging && !cellularController.isLoggingEnabled())
            {
                cellularController.toggleLogging(true);
            }

            final boolean autoStartWifiLogging = PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, false, applicationContext);
            if (autoStartWifiLogging && !wifiController.isLoggingEnabled())
            {
                wifiController.toggleLogging(true);
            }

            final boolean autoStartBluetoothLogging = PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING, false, applicationContext);
            if (autoStartBluetoothLogging && !bluetoothController.isLoggingEnabled())
            {
                bluetoothController.toggleLogging(true);
            }

            final boolean autoStartGnssLogging = PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING, false, applicationContext);
            if (autoStartGnssLogging && !gnssLoggingEnabled.get()) toggleGnssLogging(true);
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        //noinspection ReturnOfInnerClass
        return surveyServiceBinder;
    }

    @Override
    public void onDestroy()
    {
        Timber.i("onDestroy");

        unregisterManagedConfigurationListener();

        if (mqttConnection != null)
        {
            unregisterMqttConnectionStateListener(this);
            mqttConnection.disconnect();
        }

        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).unregisterOnSharedPreferenceChangeListener(this);

        cellularController.stopCellularRecordScanning();
        wifiController.stopWifiRecordScanning();
        bluetoothController.stopBluetoothRecordScanning();
        removeLocationListener();
        stopGnssRecordScanning();
        stopDeviceStatusReport();
        stopAllLogging();

        serviceLooper.quitSafely();
        shutdownNotifications();
        executorService.shutdown();

        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key)
    {
        if (key == null) return;

        switch (key)
        {
            case NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB:
                cellularController.onRolloverPreferenceChanged();
                wifiController.onRolloverPreferenceChanged();
                bluetoothController.onRolloverPreferenceChanged();

                gnssRecordLogger.onSharedPreferenceChanged();
                cdrLogger.onSharedPreferenceChanged();
                break;
            case NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS:
            case NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS:
            case NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS:
            case NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS:
            case NetworkSurveyConstants.PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS:
                setScanRateValues();
                break;
            case NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY:
                readMdmOverridePreference();
                break;
            case NetworkSurveyConstants.PROPERTY_LOG_FILE_TYPE:
                cellularController.onLogFileTypePreferenceChanged();
                wifiController.onLogFileTypePreferenceChanged();
                bluetoothController.onLogFileTypePreferenceChanged();
                break;

            default:
                break;
        }
    }

    /**
     * Creates the {@link DefaultMqttConnection} instance.
     *
     * @since 0.1.1
     */
    public void initializeMqttConnection()
    {
        mqttConnection = new MqttConnection();
        mqttConnection.registerMqttConnectionStateListener(this);
    }

    /**
     * Attempts to connect to the MQTT broker using the saved connection information. First, the MDM
     * saved connection information is used if it is present. If not, then the regular stored "user"
     * MQTT entered parameters are used.
     *
     * @return True if the connection is going to be attempted, false if it could not (for example,
     * the saved connection information is invalid).
     */
    public boolean connectToMqttBrokerUsingSavedConnectionInfo()
    {
        // First try to use the MDM settings. The only exception to this is if the user has overridden the MDM settings
        if (!isMqttMdmOverrideEnabled())
        {
            final RestrictionsManager restrictionsManager = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
            if (restrictionsManager != null)
            {
                final BrokerConnectionInfo connectionInfo = getMdmBrokerConnectionInfo();
                if (connectionInfo != null)
                {
                    connectToMqttBroker(connectionInfo);
                    return true;
                }
            }
        }

        final BrokerConnectionInfo userBrokerConnectionInfo = getUserBrokerConnectionInfo();
        if (userBrokerConnectionInfo != null)
        {
            connectToMqttBroker(userBrokerConnectionInfo);
            return true;
        }

        return false;
    }

    /**
     * Connect to an MQTT broker.
     *
     * @param connectionInfo The information needed to connect to the MQTT broker.
     * @since 0.1.1
     */
    @Override
    public void connectToMqttBroker(BrokerConnectionInfo connectionInfo)
    {
        mqttConnection.connect(getApplicationContext(), connectionInfo);
        MqttConnectionInfo networkSurveyConnection = (MqttConnectionInfo) connectionInfo;

        // Saving the MQTT protocol streaming flags here allows the Dashboard UI to get notified
        // of the updates since otherwise MDM specified flags won't get propagated to the Dashboard
        PreferenceUtils.saveMqttStreamFlags(networkSurveyConnection, getApplicationContext());

        if (networkSurveyConnection.isCellularStreamEnabled())
        {
            registerCellularSurveyRecordListener(mqttConnection);
        }
        if (networkSurveyConnection.isWifiStreamEnabled())
        {
            registerWifiSurveyRecordListener(mqttConnection);
        }
        if (networkSurveyConnection.isBluetoothStreamEnabled())
        {
            registerBluetoothSurveyRecordListener(mqttConnection);
        }
        if (networkSurveyConnection.isGnssStreamEnabled())
        {
            registerGnssSurveyRecordListener(mqttConnection);
        }
        if (networkSurveyConnection.isDeviceStatusStreamEnabled())
        {
            registerDeviceStatusListener(mqttConnection);
        }
    }

    /**
     * Disconnect from the MQTT broker and also remove the MQTT survey record listener.
     *
     * @since 0.1.1
     */
    @Override
    public void disconnectFromMqttBroker()
    {
        Timber.i("Disconnecting from the MQTT Broker");

        mqttConnection.disconnect();

        unregisterCellularSurveyRecordListener(mqttConnection);
        unregisterWifiSurveyRecordListener(mqttConnection);
        unregisterBluetoothSurveyRecordListener(mqttConnection);
        unregisterGnssSurveyRecordListener(mqttConnection);
        unregisterDeviceStatusListener(mqttConnection);
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
    @Override
    public void attemptMqttConnectWithMdmConfig(boolean forceDisconnect)
    {
        if (isMqttMdmOverrideEnabled())
        {
            Timber.i("The MQTT MDM override is enabled, so no MDM configured MQTT connection will be attempted");
            return;
        }

        final BrokerConnectionInfo connectionInfo = getMdmBrokerConnectionInfo();

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
            Timber.i("Skipping the MQTT connection because no MDN MQTT broker configuration has been set");

            if (forceDisconnect) disconnectFromMqttBroker();
        }
    }

    /**
     * @return The current connection state to the MQTT Broker.
     * @since 0.1.1
     */
    @Override
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
    @Override
    public void registerMqttConnectionStateListener(IConnectionStateListener connectionStateListener)
    {
        mqttConnection.registerMqttConnectionStateListener(connectionStateListener);
    }

    /**
     * Removes an {@link IConnectionStateListener} so that it will no longer be notified of MQTT connection state changes.
     *
     * @param connectionStateListener The listener to remove.
     */
    @Override
    public void unregisterMqttConnectionStateListener(IConnectionStateListener connectionStateListener)
    {
        mqttConnection.unregisterMqttConnectionStateListener(connectionStateListener);
    }

    public GpsListener getGpsListener()
    {
        return gpsListener;
    }

    public String getNsDeviceId()
    {
        return deviceId;
    }

    /**
     * Registers a new listener for changes to the location information.
     *
     * @since 1.6.0
     */
    public void registerLocationListener(LocationListener locationListener)
    {
        gpsListener.registerListener(locationListener);
    }

    /**
     * Unregisters a listener for changes to the location information.
     *
     * @since 1.6.0
     */
    public void unregisterLocationListener(LocationListener locationListener)
    {
        gpsListener.unregisterListener(locationListener);
    }

    public void registerLoggingChangeListener(ILoggingChangeListener listener)
    {
        loggingChangeListeners.add(listener);
    }

    public void unregisterLoggingChangeListener(ILoggingChangeListener listener)
    {
        loggingChangeListeners.remove(listener);
    }

    /**
     * Registers a listener for notifications when new cellular survey records are available.
     *
     * @param surveyRecordListener The survey record listener to register.
     */
    public void registerCellularSurveyRecordListener(ICellularSurveyRecordListener surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.registerCellularSurveyRecordListener(surveyRecordListener);
        }

        cellularController.startCellularRecordScanning(); // Only starts scanning if it is not already active.
    }

    /**
     * Unregisters a cellular survey record listener.
     * <p>
     * If the listener being removed is the last listener and nothing else is using this {@link NetworkSurveyService},
     * then this service is shutdown and will need to be restarted before it can be used again.
     *
     * @param surveyRecordListener The listener to unregister.
     */
    public void unregisterCellularSurveyRecordListener(ICellularSurveyRecordListener surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.unregisterCellularSurveyRecordListener(surveyRecordListener);
            if (!surveyRecordProcessor.isCellularBeingUsed())
            {
                cellularController.stopCellularRecordScanning();
            }
        }

        // Check to see if this service is still needed.  It is still needed if we are either logging, the UI is
        // visible, or a server connection is active.
        if (!isBeingUsed()) stopSelf();
    }

    /**
     * Registers a listener for notifications when new Wi-Fi survey records are available.
     *
     * @param surveyRecordListener The survey record listener to register.
     * @since 0.1.2
     */
    public void registerWifiSurveyRecordListener(IWifiSurveyRecordListener surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.registerWifiSurveyRecordListener(surveyRecordListener);
        }

        wifiController.startWifiRecordScanning(); // Only starts scanning if it is not already active.
    }

    /**
     * Unregisters a Wi-Fi survey record listener.
     * <p>
     * If the listener being removed is the last listener and nothing else is using this {@link NetworkSurveyService},
     * then this service is shutdown and will need to be restarted before it can be used again.
     * <p>
     * If the service is still needed for other purposes (e.g. cellular survey records), but no longer for Wi-Fi
     * scanning, then just the Wi-Fi scanning portion of this service is stopped.
     *
     * @param surveyRecordListener The listener to unregister.
     * @since 0.1.2
     */
    public void unregisterWifiSurveyRecordListener(IWifiSurveyRecordListener surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.unregisterWifiSurveyRecordListener(surveyRecordListener);
            if (!surveyRecordProcessor.isWifiBeingUsed())
            {
                wifiController.stopWifiRecordScanning();
            }
        }

        // Check to see if this service is still needed.  It is still needed if we are either logging, the UI is
        // visible, or a server connection is active.
        if (!isBeingUsed()) stopSelf();
    }

    /**
     * Registers a listener for notifications when new Bluetooth survey records are available.
     *
     * @param surveyRecordListener The survey record listener to register.
     * @since 1.0.0
     */
    public void registerBluetoothSurveyRecordListener(IBluetoothSurveyRecordListener surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.registerBluetoothSurveyRecordListener(surveyRecordListener);
        }

        bluetoothController.startBluetoothRecordScanning(); // Only starts scanning if it is not already active.
    }

    /**
     * Unregisters a Bluetooth survey record listener.
     * <p>
     * If the listener being removed is the last listener and nothing else is using this {@link NetworkSurveyService},
     * then this service is shutdown and will need to be restarted before it can be used again.
     * <p>
     * If the service is still needed for other purposes (e.g. cellular survey records), but no longer for Bluetooth
     * scanning, then just the Bluetooth scanning portion of this service is stopped.
     *
     * @param surveyRecordListener The listener to unregister.
     * @since 1.0.0
     */
    public void unregisterBluetoothSurveyRecordListener(IBluetoothSurveyRecordListener surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.unregisterBluetoothSurveyRecordListener(surveyRecordListener);
            if (!surveyRecordProcessor.isBluetoothBeingUsed())
            {
                bluetoothController.stopBluetoothRecordScanning();
            }
        }

        // Check to see if this service is still needed.  It is still needed if we are either logging, the UI is
        // visible, or a server connection is active.
        if (!isBeingUsed()) stopSelf();
    }

    /**
     * Registers a listener for notifications when new GNSS survey records are available.
     *
     * @param surveyRecordListener The survey record listener to register.
     * @since 0.3.0
     */
    public void registerGnssSurveyRecordListener(IGnssSurveyRecordListener surveyRecordListener)
    {
        synchronized (gnssStarted)
        {
            if (surveyRecordProcessor != null)
            {
                surveyRecordProcessor.registerGnssSurveyRecordListener(surveyRecordListener);
            }

            startGnssRecordScanning(); // Only starts scanning if it is not already active.
        }
    }

    /**
     * Unregisters a GNSS survey record listener.
     * <p>
     * If the listener being removed is the last listener and nothing else is using this {@link NetworkSurveyService},
     * then this service is shutdown and will need to be restarted before it can be used again.
     * <p>
     * If the service is still needed for other purposes (e.g. cellular survey records), but no longer for GNSS
     * scanning, then just the GNSS scanning portion of this service is stopped.
     *
     * @param surveyRecordListener The listener to unregister.
     * @since 0.3.0
     */
    public void unregisterGnssSurveyRecordListener(IGnssSurveyRecordListener surveyRecordListener)
    {
        synchronized (gnssStarted)
        {
            if (surveyRecordProcessor != null)
            {
                surveyRecordProcessor.unregisterGnssSurveyRecordListener(surveyRecordListener);
                if (!surveyRecordProcessor.isGnssBeingUsed()) stopGnssRecordScanning();
            }
        }

        // Check to see if this service is still needed.  It is still needed if we are either logging, the UI is
        // visible, or a server connection is active.
        if (!isBeingUsed()) stopSelf();
    }

    /**
     * Registers a listener for notifications when new CDR events are available.
     *
     * @param listener The listener to register.
     * @since 1.11
     */
    public void registerCdrEventListener(ICdrEventListener listener)
    {
        synchronized (cdrStarted)
        {
            if (surveyRecordProcessor != null)
            {
                surveyRecordProcessor.registerCdrEventListener(listener);
            }

            startCdrEvents(); // Only registers a listener if it is not already active.
        }
    }

    /**
     * Unregisters a CDR event listener.
     * <p>
     * If the listener being removed is the last listener and nothing else is using this {@link NetworkSurveyService},
     * then this service is shutdown and will need to be restarted before it can be used again.
     * <p>
     * If the service is still needed for other purposes (e.g. cellular survey records), but no longer for CDR
     * events, then just the phone state lister portion of this service is stopped.
     *
     * @param listener The listener to unregister.
     * @since 1.11
     */
    public void unregisterCdrEventListener(ICdrEventListener listener)
    {
        synchronized (cdrStarted)
        {
            if (surveyRecordProcessor != null)
            {
                surveyRecordProcessor.unregisterCdrEventListener(listener);
                if (!surveyRecordProcessor.isCdrBeingUsed()) stopCdrEvents();
            }
        }

        // Check to see if this service is still needed. It is still needed if we are either logging, the UI is
        // visible, or a server connection is active.
        if (!isBeingUsed()) stopSelf();
    }

    /**
     * Registers a listener for notifications when new device status messages are available.
     *
     * @param deviceStatusListener The survey record listener to register.
     * @since 1.1.0
     */
    public void registerDeviceStatusListener(IDeviceStatusListener deviceStatusListener)
    {
        synchronized (deviceStatusActive)
        {
            if (surveyRecordProcessor != null)
            {
                surveyRecordProcessor.registerDeviceStatusListener(deviceStatusListener);
            }

            startDeviceStatusReport(); // Only starts scanning if it is not already active.
        }
    }

    /**
     * Unregisters a device status message listener.
     * <p>
     * If the listener being removed is the last listener and nothing else is using this {@link NetworkSurveyService},
     * then this service is shutdown and will need to be restarted before it can be used again.
     *
     * @param deviceStatusListener The listener to unregister.
     * @since 1.1.0
     */
    public void unregisterDeviceStatusListener(IDeviceStatusListener deviceStatusListener)
    {
        synchronized (deviceStatusActive)
        {
            if (surveyRecordProcessor != null)
            {
                surveyRecordProcessor.unregisterDeviceStatusListener(deviceStatusListener);
                if (!surveyRecordProcessor.isDeviceStatusBeingUsed()) stopDeviceStatusReport();
            }
        }

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
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isBeingUsed()
    {
        return cellularController.isLoggingEnabled()
                || wifiController.isLoggingEnabled()
                || bluetoothController.isLoggingEnabled()
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

        cellularController.startCellularRecordScanning();
    }

    /**
     * The UI is no longer visible, so don't send any updates to the UI.
     */
    public void onUiHidden()
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.onUiHidden();
            if (!surveyRecordProcessor.isCellularBeingUsed())
            {
                cellularController.stopCellularRecordScanning();
            }
        }
    }

    /**
     * Toggles the cellular logging setting.
     * <p>
     * It is possible that an error occurs while trying to enable or disable logging.  In that event null will be
     * returned indicating that logging could not be toggled.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @return The new state of logging.  True if it is enabled, or false if it is disabled.  Null is returned if the
     * toggling was unsuccessful.
     */
    public Boolean toggleCellularLogging(boolean enable)
    {
        return cellularController.toggleLogging(enable);
    }

    /**
     * Toggles the wifi logging setting.
     * <p>
     * It is possible that an error occurs while trying to enable or disable logging.  In that event null will be
     * returned indicating that logging could not be toggled.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @return The new state of logging.  True if it is enabled, or false if it is disabled.  Null is returned if the
     * toggling was unsuccessful.
     */
    public Boolean toggleWifiLogging(boolean enable)
    {
        return wifiController.toggleLogging(enable);
    }

    /**
     * Toggles the Bluetooth logging setting.
     * <p>
     * It is possible that an error occurs while trying to enable or disable logging.  In that event null will be
     * returned indicating that logging could not be toggled.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @return The new state of logging.  True if it is enabled, or false if it is disabled.  Null is returned if the
     * toggling was unsuccessful.
     */
    public Boolean toggleBluetoothLogging(boolean enable)
    {
        return bluetoothController.toggleLogging(enable);
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

            Timber.i("Toggling GNSS logging to %s", enable);

            final boolean successful = gnssRecordLogger.enableLogging(enable);
            if (successful)
            {
                gnssLoggingEnabled.set(enable);
                if (enable)
                {
                    registerGnssSurveyRecordListener(gnssRecordLogger);
                } else
                {
                    unregisterGnssSurveyRecordListener(gnssRecordLogger);
                }
            }

            updateServiceNotification();
            notifyLoggingChangedListeners();

            final boolean newLoggingState = gnssLoggingEnabled.get();

            return successful ? newLoggingState : null;
        }
    }

    /**
     * Toggles the CDR logging setting.
     * <p>
     * It is possible that an error occurs while trying to enable or disable logging. In that event null will be
     * returned indicating that logging could not be toggled.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @return The new state of logging.  True if it is enabled, or false if it is disabled. Null is returned if the
     * toggling was unsuccessful.
     */
    public Boolean toggleCdrLogging(boolean enable)
    {
        synchronized (cdrLoggingEnabled)
        {
            final boolean originalLoggingState = cdrLoggingEnabled.get();
            if (originalLoggingState == enable) return originalLoggingState;

            Timber.i("Toggling CDR logging to %s", enable);

            final boolean successful = cdrLogger.enableLogging(enable);
            if (successful)
            {
                cdrLoggingEnabled.set(enable);
                if (enable)
                {
                    registerCdrEventListener(cdrLogger);
                } else
                {
                    unregisterCdrEventListener(cdrLogger);
                }
            }

            updateServiceNotification();
            notifyLoggingChangedListeners();

            final boolean newLoggingState = cdrLoggingEnabled.get();

            return successful ? newLoggingState : null;
        }
    }

    public boolean isCellularLoggingEnabled()
    {
        return cellularController.isLoggingEnabled();
    }

    public boolean isWifiLoggingEnabled()
    {
        return wifiController.isLoggingEnabled();
    }

    public boolean isBluetoothLoggingEnabled()
    {
        return bluetoothController.isLoggingEnabled();
    }

    public boolean isGnssLoggingEnabled()
    {
        return gnssLoggingEnabled.get();
    }

    public boolean isCdrLoggingEnabled()
    {
        return cdrLoggingEnabled.get();
    }

    public int getWifiScanRateMs()
    {
        return wifiController.getScanRateMs();
    }

    /**
     * Checks to see if the GNSS timeout has occurred. If we have waited longer than {@link #TIME_TO_WAIT_FOR_GNSS_RAW_BEFORE_FAILURE}
     * without any GNSS measurements coming in, we can assume that the device does not support raw GNSS measurements.
     * If that is the case then present that information to the user so they know their device won't support it.
     */
    public void checkForGnssTimeout()
    {
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
                if (!ignoreRawGnssFailure && gnssFailureListener != null)
                {
                    gnssFailureListener.onGnssFailure();
                    gpsListener.clearGnssTimeoutCallback(); // No need for the callback anymore
                }
            }
        } else
        {
            gpsListener.clearGnssTimeoutCallback();
        }
    }

    /**
     * Triggers the creation of a single device status message and notifies the listeners.
     *
     * @since 1.10.0
     */
    public void sendSingleDeviceStatus()
    {
        surveyRecordProcessor.onDeviceStatus(generateDeviceStatus());
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
     * Triggers a read of the scan rate values and stores them in instance variables.
     * <p>
     * The approach for reading the scan rates is to first use the MDM provided values. If those are not
     * set then the user preference values are employed. Finally, the default values are used as a fallback.
     *
     * @since 0.3.0
     */
    private void setScanRateValues()
    {
        final Context applicationContext = getApplicationContext();

        cellularController.refreshScanRate();
        wifiController.refreshScanRate();
        bluetoothController.refreshScanRate();

        gnssScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_GNSS_SCAN_INTERVAL_SECONDS, applicationContext);

        deviceStatusScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_DEVICE_STATUS_SCAN_INTERVAL_SECONDS, applicationContext);

        surveyRecordProcessor.setGnssScanRateMs(gnssScanRateMs);

        updateLocationListener();
    }

    /**
     * Triggers a read of the mdm override preference and stores it in an instance variable.
     *
     * @since 1.10.0
     */
    private void readMdmOverridePreference()
    {
        final Context applicationContext = getApplicationContext();
        mdmOverride = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);
    }

    /**
     * Creates a new {@link GpsListener} if necessary, and Registers with the Android {@link LocationManager} for
     * location updates.
     * <p>
     * It is necessary to call this method after any of the scanning services are updated. This is needed because the
     * location update rate depends on which scanners are active, and which of those has the shortest scan interval. So
     * if any of that changes we need to update the rate at which we request location updates.
     * <p>
     * If none of the scanning is active, then this method does nothing an returns immediately.
     */
    public void updateLocationListener()
    {
        if (!isBeingUsed()) return;

        Timber.d("Registering the location listener");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("ACCESS_FINE_LOCATION Permission not granted for the NetworkSurveyService");
            return;
        }

        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null)
        {
            int smallestScanRate = Integer.MAX_VALUE;

            // Find the smallest scan rate for all the scanning types that are active as a starting point
            if (cellularController.isScanningActive() && cellularController.getScanRateMs() < smallestScanRate)
            {
                smallestScanRate = cellularController.getScanRateMs();
            }

            if (wifiController.isScanningActive() && wifiController.getScanRateMs() < smallestScanRate)
            {
                smallestScanRate = wifiController.getScanRateMs();
            }

            if (bluetoothController.isScanningActive() && bluetoothController.getScanRateMs() < smallestScanRate)
            {
                smallestScanRate = bluetoothController.getScanRateMs();
            }

            if (gnssStarted.get() && gnssScanRateMs < smallestScanRate)
            {
                smallestScanRate = gnssScanRateMs;
            }

            if (deviceStatusActive.get() && deviceStatusScanRateMs < smallestScanRate)
            {
                smallestScanRate = deviceStatusScanRateMs;
            }

            if (smallestScanRate == Integer.MAX_VALUE)
            {
                // This scenario indicates that only CDR logging is active, and since records are so infrequent we use
                // another approach to get the location.
                Timber.d("Not adding the location update request because only CDR logging is enabled.");
                return;
            }

            // Use the smallest scan rate set by the user for the active scanning types
            if (smallestScanRate > 20_000) smallestScanRate = smallestScanRate / 2;

            if (smallestScanRate < 8_000) smallestScanRate = 8_000;

            Timber.d("Setting the location update rate to %d", smallestScanRate);

            try
            {
                final String provider;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER))
                {
                    provider = LocationManager.FUSED_PROVIDER;
                } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                {
                    provider = LocationManager.GPS_PROVIDER;
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                {
                    provider = LocationManager.NETWORK_PROVIDER;
                } else
                {
                    provider = LocationManager.PASSIVE_PROVIDER;
                }
                locationManager.requestLocationUpdates(provider, smallestScanRate, 0f, gpsListener, serviceLooper);
            } catch (Throwable t)
            {
                // An IllegalArgumentException was occurring on phones that don't have a GPS provider, so some defensive coding here
                Timber.e(t, "Could not request location updates because of an exception.");
            }
        } else
        {
            Timber.e("The location manager was null when trying to request location updates for the NetworkSurveyService");
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
     * Runs one cellular scan. This is used to prime the UI in the event that the scan interval is really long.
     */
    public void runSingleCellularScan()
    {
        cellularController.runSingleScan();
    }

    /**
     * Tries to establish an MQTT broker connection after the phone is first started up.
     * <p>
     * This method only applies to creating the MQTT connection at boot for two reasons. First, it checks the start
     * MQTT at boot preference before creating the connection, and secondly, it does not first disconnect any existing
     * connections because it assumes this method is being called from a fresh start of the Android phone.
     * <p>
     * First, it tries to create a connection using the MDM configured MQTT parameters as long as the user has not
     * toggled the MDM override option. In that case, this method will jump straight to using the user provided MQTT
     * connection information.
     * <p>
     * If the MDM override option is enabled, or if the MDM connection information could not be found then this method
     * attempts to use the user provided MQTT connection information.
     *
     * @since 0.1.3
     */
    private void attemptMqttConnectionAtBoot()
    {
        if (!PreferenceUtils.getMqttStartOnBootPreference(getApplicationContext()))
        {
            Timber.i("Skipping the mqtt auto-connect because the preference indicated as such");
            return;
        }

        // First try to use the MDM settings. The only exception to this is if the user has overridden the MDM settings
        boolean mdmConnection = false;
        if (!isMqttMdmOverrideEnabled())
        {
            final RestrictionsManager restrictionsManager = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
            if (restrictionsManager != null)
            {
                final BrokerConnectionInfo connectionInfo = getMdmBrokerConnectionInfo();
                if (connectionInfo != null)
                {
                    mdmConnection = true;
                    connectToMqttBroker(connectionInfo);
                }
            }
        }

        if (!mdmConnection)
        {
            final BrokerConnectionInfo userBrokerConnectionInfo = getUserBrokerConnectionInfo();
            if (userBrokerConnectionInfo != null)
            {
                connectToMqttBroker(userBrokerConnectionInfo);
            }
        }
    }

    /**
     * Create the callbacks for the {@link GnssMeasurementsEvent} and the {@link GnssStatus} that will be notified of
     * events from the location manager once {@link #startGnssRecordScanning()} is called.
     *
     * @since 0.3.0
     */
    private void initializeGnssScanningResources()
    {
        measurementListener = new GnssMeasurementsEvent.Callback()
        {
            @Override
            public void onGnssMeasurementsReceived(GnssMeasurementsEvent event)
            {
                gnssRawSupportKnown = true;
                if (surveyRecordProcessor != null) surveyRecordProcessor.onGnssMeasurements(event);
            }
        };
    }

    /**
     * Initialize and start the handler that listens for phone state events to create CDR events.
     * <p>
     * This method only starts the CDR listener if it is not already active.
     *
     * @since 1.11
     */
    private void startCdrEvents()
    {
        if (cdrStarted.getAndSet(true)) return;

        // Add a listener for the Service State information if we have access to the Telephony Manager
        final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null && getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
        {
            myPhoneNumber = IOUtils.getMyPhoneNumber(this, telephonyManager);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
            {
                smsBroadcastReceiver = new BroadcastReceiver()
                {
                    @Override
                    public void onReceive(Context context, Intent intent)
                    {
                        if (intent == null) return;
                        final String originatingAddress = intent.getStringExtra(CdrSmsReceiver.ORIGINATING_ADDRESS_EXTRA);
                        execute(() -> surveyRecordProcessor.onSmsEvent(CdrEventType.INCOMING_SMS, originatingAddress, telephonyManager, myPhoneNumber));
                    }
                };

                LocalBroadcastManager.getInstance(this).registerReceiver(smsBroadcastReceiver,
                        new IntentFilter(CdrSmsReceiver.SMS_RECEIVED_INTENT));
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
            {
                ContentResolver contentResolver = getContentResolver();
                smsOutgoingObserver = new ContentObserver(serviceHandler)
                {
                    @Override
                    public void onChange(boolean selfChange)
                    {
                        try (Cursor cursor = contentResolver.query(SMS_URI, null, null, null, null))
                        {
                            if (cursor != null && cursor.moveToFirst())
                            {
                                int idColumn = cursor.getColumnIndex(SMS_COLUMN_ID);
                                String id = cursor.getString(idColumn);
                                // So this is a huge hack, but apparently this content resolver approach for getting
                                // outgoing text messages is not officially supported by Android. The result is that
                                // I am seeing this onChange event called 3 times for each outgoing message. To prevent
                                // duplicate entries in the CDR file we keep track of the most recent messages.
                                if (smsIdQueue.containsKey(id))
                                {
                                    Timber.d("Duplicate outgoing SMS message seen in the CDR processor. Ignoring it.");
                                    return;
                                }
                                smsIdQueue.put(id, id);

                                int typeColumn = cursor.getColumnIndex(SMS_COLUMN_TYPE);
                                if (typeColumn < 0) return;
                                int type = cursor.getInt(typeColumn);

                                if (type == SMS_MESSAGE_TYPE_SENT)
                                {
                                    int addressColumn = cursor.getColumnIndex(SMS_COLUMN_ADDRESS);
                                    if (addressColumn < 0) return;
                                    String destinationAddress = cursor.getString(addressColumn);
                                    execute(() -> surveyRecordProcessor.onSmsEvent(CdrEventType.OUTGOING_SMS, myPhoneNumber, telephonyManager, destinationAddress));
                                }
                            }
                        }
                    }
                };
                contentResolver.registerContentObserver(SMS_URI, true, smsOutgoingObserver);
            }

            Timber.d("Adding the Telephony Manager Service State Listener for CDR events");

            // Sadly we have to use the service handler for this because the PhoneStateListener constructor calls
            // Looper.myLooper(), which needs to be run from a thread where the looper is prepared. The better option
            // is to use the constructor that takes an executor service, but that is only supported in Android 10+.
            serviceHandler.post(() -> {
                phoneStateCdrListener = new PhoneStateListener()
                {
                    @Override
                    public void onCallStateChanged(int state, String otherPhoneNumber)
                    {
                        execute(() -> surveyRecordProcessor.onCallStateChanged(state, otherPhoneNumber,
                                telephonyManager, myPhoneNumber));
                    }

                    @Override
                    public void onServiceStateChanged(ServiceState serviceState)
                    {
                        execute(() -> surveyRecordProcessor.onCdrServiceStateChanged(serviceState, telephonyManager));
                    }
                };

                telephonyManager.listen(phoneStateCdrListener, PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_SERVICE_STATE);
            });
        }
    }

    /**
     * Initialize and start the handler that generates a periodic Device Status Message.
     * <p>
     * This method only starts scanning if the scan is not already active.
     *
     * @since 1.1.0
     */
    private void startDeviceStatusReport()
    {
        if (deviceStatusActive.getAndSet(true)) return;

        final int handlerTaskId = deviceStatusGeneratorTaskId.incrementAndGet();

        serviceHandler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    if (!deviceStatusActive.get() || deviceStatusGeneratorTaskId.get() != handlerTaskId)
                    {
                        Timber.i("Stopping the handler that generates the device status message; taskId=%d", handlerTaskId);
                        return;
                    }

                    surveyRecordProcessor.onDeviceStatus(generateDeviceStatus());

                    serviceHandler.postDelayed(this, deviceStatusScanRateMs);
                } catch (SecurityException e)
                {
                    Timber.e(e, "Could not get the required permissions to generate a device status message");
                }
            }
        }, 1000L);

        cellularController.startPhoneStateListener();
    }

    /**
     * Generate a device status message that can be sent to any remote servers.
     *
     * @return A Device Status message that can be sent to a remote server.
     * @since 1.1.0
     */
    private DeviceStatus generateDeviceStatus()
    {
        final DeviceStatusData.Builder dataBuilder = DeviceStatusData.newBuilder();
        dataBuilder.setDeviceSerialNumber(deviceId)
                .setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));
        dataBuilder.setMdmOverride(BoolValue.newBuilder().setValue(mdmOverride).build());

        if (gpsListener != null)
        {
            final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));
                if (lastKnownLocation.hasSpeed())
                {
                    dataBuilder.setSpeed(lastKnownLocation.getSpeed());
                }
            }
        }

        final IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final Intent batteryStatus = registerReceiver(null, intentFilter);
        if (batteryStatus != null)
        {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            final float batteryPercent = (level / (float) scale) * 100;
            dataBuilder.setBatteryLevelPercent(Int32Value.of((int) batteryPercent));
        }

        dataBuilder.setDeviceModel(Build.MODEL);

        final DeviceStatus.Builder statusBuilder = DeviceStatus.newBuilder();
        statusBuilder.setMessageType(DeviceStatusMessageConstants.DEVICE_STATUS_MESSAGE_TYPE);
        statusBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        statusBuilder.setData(dataBuilder);

        return statusBuilder.build();
    }

    /**
     * Stop generating device status messages.
     *
     * @since 1.1.0
     */
    private void stopDeviceStatusReport()
    {
        Timber.d("Setting the device status active flag to false");

        cellularController.stopPhoneStateListener();

        deviceStatusActive.set(false);

        updateLocationListener();
    }

    /**
     * A notification for this service that is started in the foreground so that we can continue to get GPS location
     * updates while the phone is locked or the app is not in the foreground.
     */
    public void updateServiceNotification()
    {
        try
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            {
                startForeground(NetworkSurveyConstants.LOGGING_NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else
            {
                startForeground(NetworkSurveyConstants.LOGGING_NOTIFICATION_ID, buildNotification());
            }
        } catch (Exception e)
        {
            Timber.e(e, "Could not start the foreground service for Network Survey");
            // TODO This is one possible option for the crash on Samsung S22 devices running Android 13
            /*AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                if (alarmManager.canScheduleExactAlarms())
                {
                    Intent i = new Intent(this, NetworkSurveyService.class);
                    PendingIntent pi = PendingIntent.getForegroundService(this, 50, i, PendingIntent.FLAG_UPDATE_CURRENT);
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + GO_OFF_OFFSET, pi);
                } else
                {
                    Timber.e("Can't schedule an exact alarm in place of startForeground");
                }
            }*/
        }
    }

    /**
     * Creates a new {@link Notification} based on the current state of this service.  The returned notification can
     * then be passed on to the Android system.
     *
     * @return A {@link Notification} that represents the current state of this service (e.g. if logging is enabled).
     */
    private Notification buildNotification()
    {
        Application.createNotificationChannel(this);

        final boolean logging = cellularController.isLoggingEnabled() || wifiController.isLoggingEnabled() || bluetoothController.isLoggingEnabled() || gnssLoggingEnabled.get();
        final com.craxiom.mqttlibrary.connection.ConnectionState connectionState = mqttConnection.getConnectionState();
        final boolean mqttConnectionActive = connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING;
        final CharSequence notificationTitle = getText(R.string.network_survey_notification_title);
        final String notificationText = getNotificationText(logging, mqttConnectionActive, connectionState);

        final Intent notificationIntent = new Intent(this, NetworkSurveyActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(notificationTitle)
                .setOngoing(true)
                .setSmallIcon(mqttConnectionActive ? R.drawable.ic_cloud_connection : (logging ? R.drawable.logging_thick_icon : R.drawable.gps_map_icon))
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
            case CONNECTED ->
                    notificationText += getText(R.string.mqtt_connection_notification_text);
            case CONNECTING ->
                    notificationText += getText(R.string.mqtt_reconnecting_notification_text);
            default ->
            {
            }
        }

        return notificationText;
    }

    /**
     * Starts GNSS record scanning if it is not already started.
     * <p>
     * This method handles registering the GNSS listeners with Android so we get notified of updates.
     * <p>
     * This method is not thread safe, so make sure to call this method from a synchronized block.
     */
    private boolean startGnssRecordScanning()
    {
        if (gnssStarted.getAndSet(true)) return true;

        boolean success = false;

        final int handlerTaskId = gnssScanningTaskId.incrementAndGet();

        boolean hasPermissions = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (hasPermissions)
        {
            if (locationManager == null)
            {
                locationManager = getSystemService(LocationManager.class);
                if (locationManager != null)
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    {
                        locationManager.registerGnssMeasurementsCallback(executorService, measurementListener);
                    } else
                    {
                        locationManager.registerGnssMeasurementsCallback(measurementListener);
                    }
                    gpsListener.addGnssTimeoutCallback(this::checkForGnssTimeout);
                    Timber.i("Successfully registered the GNSS listeners");
                }
            } else
            {
                Timber.w("The location manager was null when registering the GNSS listeners");
            }

            serviceHandler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        if (!gnssStarted.get() || gnssScanningTaskId.get() != handlerTaskId)
                        {
                            Timber.i("Stopping the handler that checks for missed GNSS measurements");
                            return;
                        }

                        surveyRecordProcessor.checkForMissedGnssMeasurement();

                        serviceHandler.postDelayed(this, getGnssTimeoutIntervalMs(gnssScanRateMs));
                    } catch (SecurityException e)
                    {
                        Timber.e(e, "Could not get the required permissions to check for missed GNSS measurement");
                    }
                }
            }, getGnssTimeoutIntervalMs(gnssScanRateMs));

            success = true;
        }

        updateLocationListener();

        return success;
    }

    /**
     * Unregisters from GPS/GNSS updates from the Android OS.
     * <p>
     * This method is not thread safe, so make sure to call this method from a synchronized block.
     */
    private void stopGnssRecordScanning()
    {
        if (!gnssStarted.getAndSet(false)) return;

        if (locationManager != null)
        {
            locationManager.unregisterGnssMeasurementsCallback(measurementListener);
            gpsListener.clearGnssTimeoutCallback();
            locationManager = null;
        }

        updateLocationListener();
    }

    /**
     * Remove the phone state listener for CDR events.
     *
     * @since 1.11
     */
    private void stopCdrEvents()
    {
        Timber.d("Setting the cdr active flag to false");

        if (phoneStateCdrListener != null)
        {
            final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null && getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            {
                Timber.d("Removing the CDR Telephony Manager Service State Listener");

                telephonyManager.listen(phoneStateCdrListener, PhoneStateListener.LISTEN_NONE);
            }
        }

        if (smsBroadcastReceiver != null)
        {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(smsBroadcastReceiver);
        }

        if (smsOutgoingObserver != null)
        {
            getContentResolver().unregisterContentObserver(smsOutgoingObserver);
        }

        cdrStarted.set(false);
    }

    /**
     * If any of the loggers are still active, this stops them all just to be safe. If they are not active then nothing
     * changes.
     *
     * @since 0.3.0
     */
    private void stopAllLogging()
    {
        cellularController.stopAllLogging();
        wifiController.stopAllLogging();
        bluetoothController.stopAllLogging();
        if (gnssRecordLogger != null) gnssRecordLogger.enableLogging(false);
        if (cdrLogger != null) cdrLogger.enableLogging(false);
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
                setScanRateValues();
                attemptMqttConnectWithMdmConfig(true);

                cellularController.onMdmPreferenceChanged();
                wifiController.onMdmPreferenceChanged();
                bluetoothController.onMdmPreferenceChanged();

                gnssRecordLogger.onMdmPreferenceChanged();
                cdrLogger.onMdmPreferenceChanged();
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
                Timber.e(e, "Unable to unregister the Managed Configuration Listener when pausing the app");
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
        return preferences.getBoolean(MqttConstants.PROPERTY_MQTT_MDM_OVERRIDE, false);
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

    private BrokerConnectionInfo getMdmBrokerConnectionInfo()
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            final boolean hasBrokerHost = mdmProperties.containsKey(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST);
            if (!hasBrokerHost) return null;

            final String mqttBrokerHost = mdmProperties.getString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST);
            final int portNumber = mdmProperties.getInt(MqttConstants.PROPERTY_MQTT_CONNECTION_PORT, MqttConstants.DEFAULT_MQTT_PORT);
            final boolean tlsEnabled = mdmProperties.getBoolean(MqttConstants.PROPERTY_MQTT_CONNECTION_TLS_ENABLED, MqttConstants.DEFAULT_MQTT_TLS_SETTING);
            final String clientId = mdmProperties.getString(MqttConstants.PROPERTY_MQTT_CLIENT_ID);
            final String username = mdmProperties.getString(MqttConstants.PROPERTY_MQTT_USERNAME);
            final String password = mdmProperties.getString(MqttConstants.PROPERTY_MQTT_PASSWORD);

            final boolean cellularStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING);
            final boolean wifiStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING);
            final boolean bluetoothStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING);
            final boolean gnssStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING);
            final boolean deviceStatusStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING);

            if (mqttBrokerHost == null || clientId == null)
            {
                return null;
            }

            return new MqttConnectionInfo(mqttBrokerHost, portNumber, tlsEnabled, clientId, username, password,
                    cellularStreamEnabled, wifiStreamEnabled, bluetoothStreamEnabled, gnssStreamEnabled, deviceStatusStreamEnabled);
        }

        return null;
    }

    /**
     * Get the user configured MQTT broker connection information to use to establish the connection.
     * <p>
     * If no user defined MQTT broker connection information is present, then null is returned.
     *
     * @return The connection settings to use for the MQTT broker, or null if no connection information is present.
     * @since 0.1.3
     */
    private BrokerConnectionInfo getUserBrokerConnectionInfo()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        final String mqttBrokerHost = preferences.getString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST, "");
        if (mqttBrokerHost.isEmpty()) return null;

        final String clientId = preferences.getString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, "");
        if (clientId.isEmpty()) return null;

        final int portNumber = preferences.getInt(MqttConstants.PROPERTY_MQTT_CONNECTION_PORT, MqttConstants.DEFAULT_MQTT_PORT);
        final boolean tlsEnabled = preferences.getBoolean(MqttConstants.PROPERTY_MQTT_CONNECTION_TLS_ENABLED, MqttConstants.DEFAULT_MQTT_TLS_SETTING);
        final String username = preferences.getString(MqttConstants.PROPERTY_MQTT_USERNAME, "");
        final String password = preferences.getString(MqttConstants.PROPERTY_MQTT_PASSWORD, "");

        final boolean cellularStreamEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING);
        final boolean wifiStreamEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING);
        final boolean bluetoothStreamEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING);
        final boolean gnssStreamEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING);
        final boolean deviceStatusStreamEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING);

        return new MqttConnectionInfo(mqttBrokerHost, portNumber, tlsEnabled, clientId, username, password,
                cellularStreamEnabled, wifiStreamEnabled, bluetoothStreamEnabled, gnssStreamEnabled, deviceStatusStreamEnabled);
    }

    /**
     * Notify {@link #loggingChangeListeners} that one or more of the logging states have changed.
     *
     * @since 1.10.0
     */
    public void notifyLoggingChangedListeners()
    {
        loggingChangeListeners.forEach(l -> {
            try
            {
                l.onLoggingChanged();
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Logging Changed Listener because of an exception");
            }
        });
    }

    @Override
    public void onConnectionStateChange(ConnectionState connectionState)
    {
        updateServiceNotification();
    }

    /**
     * Registers a listener any GNSS failures. This can include timing out before we received any
     * GNSS measurements.
     *
     * @param gnssFailureListener The listener.
     * @since 0.4.0
     */
    public void registerGnssFailureListener(IGnssFailureListener gnssFailureListener)
    {
        this.gnssFailureListener = gnssFailureListener;
    }

    /**
     * Clears the GNSS failure listener.
     *
     * @since 0.4.0
     */
    public void clearGnssFailureListener()
    {
        gnssFailureListener = null;
    }

    /**
     * Class used for the client Binder.  Because we know this service always runs in the same process as its clients,
     * we don't need to deal with IPC.
     */
    public class SurveyServiceBinder extends AConnectionFragment.ServiceBinder
    {
        @Override
        public IMqttService getService()
        {
            return NetworkSurveyService.this;
        }
    }

    /**
     * Acts as a cache and evicts older entries when new ones are added.
     */
    private static class EvictingLinkedHashMap extends LinkedHashMap<String, String>
    {
        @Override
        protected boolean removeEldestEntry(Entry<String, String> eldest)
        {
            return size() > 10;
        }
    }

    // TODO Delete me as this is a temp solution until everything is move out of here
    private void execute(Runnable runnable)
    {
        try
        {
            executorService.execute(runnable);
        } catch (Throwable t)
        {
            Timber.w(t, "Could not submit to the executor service");
        }
    }
}
