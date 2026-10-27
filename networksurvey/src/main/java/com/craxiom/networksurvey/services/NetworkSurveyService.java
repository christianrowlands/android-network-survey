package com.craxiom.networksurvey.services;

import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.LOCATION_PROVIDER_ALL;
import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.LOCATION_PROVIDER_FUSED;
import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.LOCATION_PROVIDER_GNSS;
import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.LOCATION_PROVIDER_NETWORK;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.telephony.SubscriptionInfo;

import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.DeviceStatusData;
import com.craxiom.mqttlibrary.IConnectionStateListener;
import com.craxiom.mqttlibrary.IQueueBackpressureListener;
import com.craxiom.mqttlibrary.MqttConstants;
import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;
import com.craxiom.mqttlibrary.connection.ConnectionState;
import com.craxiom.mqttlibrary.connection.DefaultMqttConnection;
import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.DeviceStatusMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.NsAnalyticsConstants;
import com.craxiom.networksurvey.gpstest.util.FormatUtils;
import com.craxiom.networksurvey.gpstest.util.MathUtils;
import com.craxiom.networksurvey.listeners.ExtraLocationListener;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.listeners.ICdrEventListener;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGnssFailureListener;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IPhoneStateListener;
import com.craxiom.networksurvey.listeners.IUploadRecordCountListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.logging.DeviceStatusCsvLogger;
import com.craxiom.networksurvey.logging.db.DbUploadStore;
import com.craxiom.networksurvey.logging.db.NsAnalyticsDataStore;
import com.craxiom.networksurvey.logging.db.SurveyDatabase;
import com.craxiom.networksurvey.logging.db.uploader.AutoUploadManager;
import com.craxiom.networksurvey.logging.db.uploader.AutoUploadRequest;
import com.craxiom.networksurvey.logging.db.uploader.NsAnalyticsUploadWorker;
import com.craxiom.networksurvey.model.BatteryPauseState;
import com.craxiom.networksurvey.model.SurveyTypes;
import com.craxiom.networksurvey.model.UploadScanningResult;
import com.craxiom.networksurvey.mqtt.MqttConnection;
import com.craxiom.networksurvey.notification.NotificationChannels;
import com.craxiom.networksurvey.notification.SurveyNotificationBuilder;
import com.craxiom.networksurvey.notification.SurveyNotificationState;
import com.craxiom.networksurvey.notification.SurveyStallWatchdog;
import com.craxiom.networksurvey.ui.activesurvey.NewTowerNotificationHelper;
import com.craxiom.networksurvey.ui.activesurvey.NewTowerRegistry;
import com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsNotificationHelper;
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo;
import com.craxiom.networksurvey.mqtt.OrganizationBroker;
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfileStore;
import com.craxiom.networksurvey.services.controller.BluetoothController;
import com.craxiom.networksurvey.services.controller.CellularController;
import com.craxiom.networksurvey.services.controller.GnssController;
import com.craxiom.networksurvey.services.controller.WifiController;
import com.craxiom.networksurvey.services.watchlist.WatchlistChangePublisher;
import com.craxiom.networksurvey.services.watchlist.WatchlistDetectionManager;
import com.craxiom.networksurvey.fragments.model.GrpcConnectionSettings;
import com.craxiom.networksurvey.survey.engine.DestinationRuntime;
import com.craxiom.networksurvey.survey.status.DestinationStatusRules;
import com.craxiom.networksurvey.survey.status.LeftOutItems;
import com.craxiom.networksurvey.survey.engine.MissionSessionTracker;
import com.craxiom.networksurvey.survey.engine.SurveyCoordinator;
import com.craxiom.networksurvey.survey.engine.SurveyOrchestrator;
import com.craxiom.networksurvey.survey.engine.SurveyOrigin;
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState;
import com.craxiom.networksurvey.survey.engine.SurveySessionCounters;
import com.craxiom.networksurvey.survey.engine.SurveyStartResult;
import com.craxiom.networksurvey.survey.engine.SurveyStateHolder;
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver;
import com.craxiom.networksurvey.survey.intent.AndroidIntentExtras;
import com.craxiom.networksurvey.survey.intent.IntentSurveyRequest;
import com.craxiom.networksurvey.survey.intent.SurveyIntentMapper;
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource;
import com.craxiom.networksurvey.survey.mdm.MdmOverrideController;
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot;
import com.craxiom.networksurvey.survey.mdm.RestrictionsSource;
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy;
import com.craxiom.networksurvey.survey.model.Destination;
import com.craxiom.networksurvey.survey.model.DestinationCapabilities;
import com.craxiom.networksurvey.survey.model.RecordType;
import com.craxiom.networksurvey.survey.model.SurveySetup;
import com.craxiom.networksurvey.survey.setup.SurveySetupStore;
import com.craxiom.networksurvey.ui.wifi.model.AndroidWifiEnvironment;
import com.craxiom.networksurvey.ui.wifi.model.WifiEnvironment;
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState;
import com.craxiom.networksurvey.util.CredentialSecureStorage;
import com.craxiom.networksurvey.util.LocationUtils;
import com.craxiom.networksurvey.util.MdmUtils;
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage;
import com.craxiom.networksurvey.util.NsAnalyticsUtils;
import com.craxiom.networksurvey.util.NsUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.Unit;
import kotlinx.coroutines.flow.StateFlow;
import timber.log.Timber;

/**
 * This service is the main service for Network Survey. It is responsible for initializing all of
 * the controllers and any other resources that is needed to conduct a survey. When the user
 * performs any actions such as toggling on logging or creating an MQTT connection this is the
 * service that handles dispatching those requests to the appropriate controller.
 * <p>
 * This service also handles performing any actions that need to be done when the application is
 * started at boot. For example, if the user has enabled auto start logging, then this service will
 * start the logging when the application is started at boot.
 *
 * @since 0.0.9
 */
public class NetworkSurveyService extends Service implements IConnectionStateListener, SharedPreferences.OnSharedPreferenceChangeListener, IUploadRecordCountListener, BatteryMonitor.IBatteryLevelListener, IQueueBackpressureListener
{
    public static final String ACTION_START_SURVEY = "com.craxiom.networksurvey.START_SURVEY";
    public static final String ACTION_STOP_SURVEY = "com.craxiom.networksurvey.STOP_SURVEY";

    private static final String PROPERTY_BATTERY_PAUSE_STATE = "battery_pause_state";
    private static final long SURVEY_CONTROL_TIMEOUT_SECONDS = 10;

    private final AtomicBoolean deviceStatusActive = new AtomicBoolean(false);
    private final ScheduledThreadPoolExecutor deviceStatusExecutor = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> deviceStatusFuture;

    private SurveyServiceBinder surveyServiceBinder;
    private final Handler uiThreadHandler;
    private final ExecutorService executorService;

    private volatile int deviceStatusScanRateMs;

    private PowerManager.WakeLock wakeLock;
    private final AtomicBoolean wakeLockActive = new AtomicBoolean(false);

    private CellularController cellularController;
    private WifiController wifiController;
    private WatchlistDetectionManager watchlistDetectionManager;
    private boolean watchlistRegistered = false;
    // Volatile because it is written on the thread that starts the MQTT connection and read from the
    // MQTT client's connection-state callback thread in onConnectionStateChange.
    private volatile WatchlistChangePublisher watchlistChangePublisher;
    // Volatile for the same reason as the publisher field: written from the connect paths and read
    // when the watchlist detection manager is (re)created.
    private volatile boolean mqttWatchlistStreamEnabled = false;
    private BluetoothController bluetoothController;
    private GnssController gnssController;
    private String deviceId;
    private SurveyRecordProcessor surveyRecordProcessor;
    private GpsListener primaryLocationListener;
    private ExtraLocationListener gnssLocationListener;
    private ExtraLocationListener networkLocationListener;
    private volatile DbUploadStore dbUploadStore;
    private volatile AutoUploadManager autoUploadManager;

    /** The record types saved for the community databases, guarded by {@code destinationToggleLock}. */
    private final Set<RecordType> communityTypes = EnumSet.noneOf(RecordType.class);
    private volatile NsAnalyticsDataStore nsAnalyticsDataStore;

    private DeviceStatusCsvLogger deviceStatusCsvLogger;
    private Looper serviceLooper;
    private Handler serviceHandler;
    private MqttConnection mqttConnection;

    /** A little past the escalation time, so the refresh sees it as reached. */
    private static final long ESCALATION_REFRESH_MARGIN_MS = 100L;

    /** Refreshes the notification and the survey state when a connection should escalate. */
    private final Runnable connectingEscalation = this::updateServiceNotification;
    private final NsAnalyticsUtils.RegistrationListener nsAnalyticsRegistrationListener = this::onNsAnalyticsRegistrationChanged;
    private BroadcastReceiver radioStateReceiver;
    private NsAnalyticsRegistrationWatcher nsAnalyticsRegistrationWatcher;

    /** Reads each destination's health for the published survey state. */
    private final DestinationHealthReader destinationHealthReader = new DestinationHealthReader(
            () -> mqttConnection, this::isMqttDroppingMessages, () -> autoUploadManager, System::currentTimeMillis);
    private BroadcastReceiver managedConfigurationListener;
    private volatile boolean mdmOverride = false;

    private BatteryMonitor batteryMonitor;
    private BatteryPauseState batteryPauseState;

    // Queue backpressure states. These two states are mutually exclusive:
    // - isPausedDueToQueueBackpressure: Scanning is paused when streaming queue is full and no other outputs are active
    // - mqttConnection.isDropping(): MQTT messages are dropped (not paused) when queue is full but other outputs (file logging, gRPC) are active
    // Only one can be true at a time. When the MQTT queue fills up, we enter drop mode if other outputs are enabled,
    // otherwise we pause scanning entirely. MqttConnection is the single source of truth for drop mode state.
    private final AtomicBoolean isPausedDueToQueueBackpressure = new AtomicBoolean(false);

    // The single ongoing notification for this service. The first post goes through startForeground
    // and every later refresh goes through the NotificationManager.
    private SurveyNotificationBuilder surveyNotificationBuilder;
    private SurveyStallWatchdog surveyStallWatchdog;
    private final AtomicBoolean foregroundStarted = new AtomicBoolean(false);

    private int locationProviderPreference = NetworkSurveyConstants.DEFAULT_LOCATION_PROVIDER;

    // Survey session and Mission ID tracking. The Mission ID identifies one survey session and rolls
    // when the first mission relevant survey of a session starts (OpenCelliD/BeaconDB uploads do not
    // count). The rules live in MissionSessionTracker so they can be unit tested; this service only
    // tells it when destinations start and stop.
    private final MissionSessionTracker missionSessionTracker;

    // The survey setup model. Every survey start, change and stop (the Survey screen, the Intent
    // API, boot, app open and MDM changes) runs through the coordinator, on its own serial thread.
    private final ExecutorService surveyControlExecutor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "SurveyControl");
        surveyControlThread = thread;
        return thread;
    });
    private final SurveyStateHolder surveyStateHolder = new SurveyStateHolder();
    private SurveySetupStore surveySetupStore;
    private MqttBrokerProfileStore brokerProfileStore;
    private SurveyCoordinator surveyCoordinator;
    private MdmOverrideController mdmOverrideController;

    // Dedicated locks. They are deliberately not the service monitor: the device status listener
    // registration holds deviceStatusActive and then updates the wake lock, so sharing one monitor
    // between the wake lock and the destination toggles (which register device status listeners)
    // could deadlock.
    private final Object destinationToggleLock = new Object();
    private final Object wakeLockLock = new Object();
    private final Object runtimeStatePublishLock = new Object();
    private volatile boolean destroyed = false;
    private volatile Thread surveyControlThread;

    // The record types each streaming destination currently receives. Guarded by their own monitor.
    private final Set<RecordType> mqttStreamTypes = EnumSet.noneOf(RecordType.class);
    private final Set<RecordType> nsAnalyticsTypes = EnumSet.noneOf(RecordType.class);
    private final Set<RecordType> grpcStreamTypes = EnumSet.noneOf(RecordType.class);

    // The broker from an Intent API START, used for the MQTT connection that request starts.
    private volatile BrokerConnectionInfo intentBrokerConnectionInfo = null;
    // Fingerprint of the broker the running MQTT connection uses, so an MDM change can tell
    // whether a reconnect is needed.
    private volatile String connectedBrokerFingerprint = null;
    // True when the running MQTT connection uses the organization's broker, so removing that broker
    // ends the stream instead of moving it to the user's broker.
    private volatile boolean connectedToOrganizationBroker = false;

    // The managed configuration, read through currentRestrictions() so a test can replace it.
    private AndroidRestrictionsSource platformRestrictionsSource;
    private volatile RestrictionsSource restrictionsSourceForTest = null;

    public NetworkSurveyService()
    {
        surveyServiceBinder = new SurveyServiceBinder(this);
        uiThreadHandler = new Handler(Looper.getMainLooper());

        // Making it single thread to ensure records are processed in the order they are received.
        executorService = Executors.newSingleThreadExecutor();

        missionSessionTracker = new MissionSessionTracker(
                this::generateMissionId,
                this::isAnySurveyActive,
                this::isAnyMissionRelevantSurveyActive,
                (currentMissionId, sessionActive) -> Unit.INSTANCE,
                System::currentTimeMillis,
                endedSession -> {
                    // Sessions only run after onCreate, so the store exists by the time one ends.
                    if (surveySetupStore != null) surveySetupStore.setLastSession(endedSession);
                    return Unit.INSTANCE;
                });
    }

    @Override
    public void onCreate()
    {
        Timber.i("Creating the Network Survey Service");

        final Context context = getApplicationContext();

        // Initialize wake lock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null)
        {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    NetworkSurveyConstants.WAKE_LOCK_TAG);
            wakeLock.setReferenceCounted(false);
            Timber.d("Wake lock initialized");
        } else
        {
            Timber.e("PowerManager is null, wake lock not initialized");
        }

        final HandlerThread handlerThread = new HandlerThread("NetworkSurveyService");
        handlerThread.start();

        serviceLooper = handlerThread.getLooper();
        serviceHandler = new Handler(serviceLooper);

        deviceId = createDeviceId();
        deviceStatusCsvLogger = new DeviceStatusCsvLogger(this);

        // The channels must exist before the first startForeground call, including when this service
        // is started at boot without the activity ever running.
        NotificationChannels.INSTANCE.createAll(context);
        surveyNotificationBuilder = new SurveyNotificationBuilder(this);

        primaryLocationListener = new GpsListener();
        gnssLocationListener = new ExtraLocationListener(LocationManager.GPS_PROVIDER);
        networkLocationListener = new ExtraLocationListener(LocationManager.NETWORK_PROVIDER);

        surveyRecordProcessor = new SurveyRecordProcessor(primaryLocationListener, deviceId, context, executorService);
        surveyRecordProcessor.setNetworkSurveyService(this);

        cellularController = new CellularController(this, executorService, serviceLooper, serviceHandler, surveyRecordProcessor);
        wifiController = new WifiController(this, executorService, serviceLooper, serviceHandler, surveyRecordProcessor);
        bluetoothController = new BluetoothController(this, executorService, serviceLooper, serviceHandler, surveyRecordProcessor, uiThreadHandler);
        gnssController = new GnssController(this, executorService, serviceLooper, serviceHandler, surveyRecordProcessor);

        setScanRateValues();
        readMdmOverridePreference();
        PreferenceUtils.populateRandomMqttClientIdIfMissing(context);
        brokerProfileStore = MqttBrokerProfileStore.create(context);
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this);

        // Before the survey coordinator: a managed configuration that changed while the app was
        // closed is handled as the coordinator starts, and that asks whether MQTT is streaming.
        // Must register for MDM updates AFTER initializing the MQTT connection because we try to make an MQTT connection if the MDM settings change
        initializeMqttConnection();
        initializeSurveyCoordinator();
        registerManagedConfigurationListener();

        cellularController.initialize();
        wifiController.initializeWifiScanningResources();
        bluetoothController.initializeBtScanningResources();
        gnssController.initializeGnssScanningResources();

        // Register the Watchlist detector if the feature is enabled (created lazily).
        updateWatchlistRegistration();

        // Take NS Analytics out of the survey if this phone is removed from its workspace
        nsAnalyticsRegistrationWatcher = new NsAnalyticsRegistrationWatcher(this);
        NsAnalyticsUtils.addRegistrationListener(nsAnalyticsRegistrationListener);
        registerRadioStateReceiver();

        // Initialize battery management
        batteryMonitor = new BatteryMonitor(this);
        batteryPauseState = loadBatteryPauseState();

        // Start battery monitoring
        batteryMonitor.startMonitoring();

        // Register battery monitor if battery management is enabled
        if (PreferenceUtils.isBatteryManagementEnabled(context))
        {
            batteryMonitor.register(this);

            // Force a threshold evaluation in case the initial battery reading hasn't completed yet
            batteryMonitor.reevaluateThreshold();

            // Check if we should start paused
            if (batteryMonitor.isPausedDueToBattery())
            {
                Timber.i("Starting in paused state due to low battery");
                pauseAllOperations();
            }
        }

        surveyStallWatchdog = createStallWatchdog();
        surveyStallWatchdog.start();

        updateServiceNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (intent == null) return START_REDELIVER_INTENT;

        ensureForegroundForStartRequest(startId);

        // If we are started at boot, then that means the NetworkSurveyActivity was never run, so the
        // auto start setting is applied here.
        final boolean startedAtBoot = intent.getBooleanExtra(NetworkSurveyConstants.EXTRA_STARTED_AT_BOOT, false);
        if (startedAtBoot)
        {
            Timber.i("Received the startedAtBoot flag in the NetworkSurveyService. Applying the auto start setting");
            surveyCoordinator.startAutoSurveyIfEnabled(SurveyOrigin.BOOT);
            stopSelfAfterRequestIfUnused(startId);
        } else if (ACTION_START_SURVEY.equals(intent.getAction()))
        {
            if (!PreferenceUtils.getAllowIntentControlPreference(this))
            {
                Timber.w("Received a start survey control intent, but the user has disabled intent control");
                return START_REDELIVER_INTENT;
            }

            handleStartSurveyIntent(intent);
            stopSelfAfterRequestIfUnused(startId);
        } else if (ACTION_STOP_SURVEY.equals(intent.getAction()))
        {
            if (!PreferenceUtils.getAllowIntentControlPreference(this))
            {
                Timber.w("Received a stop survey control intent, but the user has disabled intent control");
                return START_REDELIVER_INTENT;
            }

            Timber.i("The Network Survey Service is being stopped via an external intent");

            // STOP stops everything: file logging, MQTT, gRPC, NS Analytics and the community
            // databases. The service then stops itself once nothing is using it.
            surveyCoordinator.stopSurvey(SurveyOrigin.INTENT);
            stopSelfAfterRequestIfUnused(startId);
        }

        return START_REDELIVER_INTENT;
    }

    /**
     * Answers a start request with {@link #startForeground} when the system does not count this
     * service as foreground. A request sent with startForegroundService (the Intent API, boot) has to
     * be answered by startForeground before the service stops, or Android crashes the app with
     * ForegroundServiceDidNotStartInTimeException. The service normally goes foreground in
     * {@link #onCreate()} and stays there, so this does nothing; it logs when the system and
     * {@link #foregroundStarted} disagree, which is how the one such crash seen in testing would show.
     * An attempt that fails (no location permission) still answers the request.
     *
     * @param startId The id of the start request being handled.
     */
    private void ensureForegroundForStartRequest(int startId)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        if (getForegroundServiceType() != ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE) return;

        if (foregroundStarted.get())
        {
            Timber.w("Start request %d arrived while the service is not in the foreground, although it went foreground before; going foreground again", startId);
            foregroundStarted.set(false);
        } else
        {
            Timber.i("Start request %d arrived before the service could go foreground; trying again", startId);
        }
        updateServiceNotification();
    }

    /**
     * Runs a survey control operation on the survey control thread (where the coordinator runs every
     * start, change and stop) and waits for it, so the older screens that still call the service
     * directly cannot interleave with a survey start or stop. Runs inline when already on that
     * thread.
     *
     * @return The operation's result, or null if it failed or did not finish in time.
     */
    private <T> T callOnSurveyThread(java.util.concurrent.Callable<T> operation)
    {
        try
        {
            if (Thread.currentThread() == surveyControlThread) return operation.call();
            return surveyCoordinator.submit(() -> {
                try
                {
                    return operation.call();
                } catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            }).get(SURVEY_CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e)
        {
            Timber.e(e, "A survey control operation failed");
            return null;
        }
    }

    /**
     * Queued behind a start or stop request: stops this service if, once the request has run,
     * nothing is using it (for example a STOP, or a START where nothing could start). Uses
     * {@link #stopSelfResult(int)} so a newer start request delivered in the meantime keeps the
     * service alive.
     *
     * @param startId The start id of the request that was just queued.
     */
    private void stopSelfAfterRequestIfUnused(int startId)
    {
        surveyCoordinator.submit(() -> {
            if (!isBeingUsed())
            {
                Timber.i("Nothing is using the service after the request with start id %d", startId);
                stopSelfResult(startId);
            }
            return Unit.INSTANCE;
        });
    }

    /**
     * @return The broker an Intent API START supplied, or null when it supplied none or its settings
     * could not be read.
     */
    private static BrokerConnectionInfo readIntentBroker(IntentSurveyRequest request)
    {
        if (request.getMqttSettings() == null) return null;
        try
        {
            return request.getMqttSettings().toMqttConnectionInfo();
        } catch (Exception e)
        {
            Timber.e(e, "Failed to read the MQTT connection settings from the start survey intent");
            return null;
        }
    }

    /**
     * Maps an Intent API START onto a survey setup and adds it to whatever is running. The old
     * per-destination extras keep working (mapped as a union) but are deprecated in favor of the
     * {@code record_types} and {@code destinations} extras.
     */
    private void handleStartSurveyIntent(Intent intent)
    {
        Timber.i("The Network Survey Service was started via an external intent");

        final IntentSurveyRequest request = SurveyIntentMapper.map(new AndroidIntentExtras(intent));
        if (request == null)
        {
            Timber.w("The start survey intent did not name any record type or destination");
            return;
        }

        if (request.getUsedLegacyExtras())
        {
            Timber.w("The start survey intent uses deprecated per-destination extras; use %s and %s instead",
                    NetworkSurveyConstants.EXTRA_RECORD_TYPES, NetworkSurveyConstants.EXTRA_DESTINATIONS);
        }
        if (!request.getInvalidValues().isEmpty())
        {
            Timber.w("Ignoring unknown values in the start survey intent: %s", request.getInvalidValues());
        }

        SurveySetup requestedSetup = request.getSetup();
        final BrokerConnectionInfo requestedBroker = readIntentBroker(request);
        if (request.getMqttSettings() != null && requestedBroker == null)
        {
            // The intent named a broker that could not be read. Its records must never go to
            // another broker, so the rest of the request starts without MQTT.
            final Set<Destination> destinations = EnumSet.noneOf(Destination.class);
            destinations.addAll(requestedSetup.getDestinations());
            destinations.remove(Destination.MQTT);
            requestedSetup = SurveySetup.custom(requestedSetup.getRecordTypes(), destinations);
        }

        if (requestedBroker != null)
        {
            // The intent supplies the broker for an MQTT stream it starts, unless the organization
            // keeps MQTT off (the merge below then skips MQTT). A stream that already runs (the
            // user's, the organization's or an earlier intent's) is never moved to another app's
            // broker. Done on the survey control thread so it is ordered with the merge.
            surveyCoordinator.submit(() -> {
                if (!surveyCoordinator.policy().isDestinationAvailable(Destination.MQTT)) return Unit.INSTANCE;
                if (mqttConnection.getConnectionState() == ConnectionState.DISCONNECTED)
                {
                    intentBrokerConnectionInfo = requestedBroker;
                } else
                {
                    Timber.i("MQTT already streams, so the start survey intent's broker is not used");
                }
                return Unit.INSTANCE;
            });
        }

        surveyCoordinator.mergeIntoRunningSurvey(requestedSetup, SurveyOrigin.INTENT);
        surveyCoordinator.submit(() -> {
            // An intent broker only lives as long as the stream it started.
            if (!isMqttStreamingActive()) intentBrokerConnectionInfo = null;
            return Unit.INSTANCE;
        });
        surveyCoordinator.submit(() -> {
            final SurveyStartResult result = surveyCoordinator.getLastResult();
            if (result != null)
            {
                Timber.i("Intent API START result: recording=%b, skipped=%s, record type problems=%s",
                        result.getRecording(), result.getSkipped(), result.getRecordTypeProblems());
            }
            return Unit.INSTANCE;
        });
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return surveyServiceBinder;
    }

    /**
     * Called once the last bound client has disconnected. Each unregister method already re-checks
     * {@link #isBeingUsed()} and stops this service, but a client can unbind without unregistering
     * anything, and {@link com.craxiom.networksurvey.NetworkSurveyActivity#onPause()} runs its own
     * check before its fragments have been paused. Re-checking here means the last unbind always
     * gets a chance to shut the service (and its ongoing notification) down when nothing is logging,
     * connected, or listening.
     *
     * @return false so that a later bind goes through {@link #onBind(Intent)} again rather than
     * {@code onRebind}, which this service does not implement.
     */
    @Override
    public boolean onUnbind(Intent intent)
    {
        if (surveyRecordProcessor != null && !isBeingUsed())
        {
            Timber.i("The last client unbound from the Network Survey Service and nothing is using it, so it is being stopped");
            stopSelf();
        }

        return false;
    }

    @Override
    public void onDestroy()
    {
        destroyed = true;
        // A Community upload ends with the service, so no tower stays tagged NEW on the map
        NewTowerRegistry.endSession();
        // Drop any queued survey control task; nothing may run against the torn down controllers.
        surveyControlExecutor.shutdownNow();

        // Unregister and stop battery monitor
        if (batteryMonitor != null)
        {
            batteryMonitor.unregister(this);
            batteryMonitor.stopMonitoring();
        }

        // Release wake lock if held
        if (wakeLock != null && wakeLockActive.getAndSet(false))
        {
            Timber.d("Releasing wake lock in onDestroy");
            try
            {
                if (wakeLock.isHeld())
                {
                    wakeLock.release();
                }
            } catch (Exception e)
            {
                Timber.e(e, "Failed to release wake lock in onDestroy");
            }
        }

        unregisterManagedConfigurationListener();

        NsAnalyticsUtils.removeRegistrationListener(nsAnalyticsRegistrationListener);
        unregisterRadioStateReceiver();
        if (nsAnalyticsRegistrationWatcher != null) nsAnalyticsRegistrationWatcher.stop();

        if (mqttConnection != null)
        {
            mqttConnection.unregisterMqttConnectionStateListener(this);
            mqttConnection.unregisterQueueBackpressureListener(this);
            mqttConnection.disconnect();
        }

        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).unregisterOnSharedPreferenceChangeListener(this);

        if (watchlistDetectionManager != null)
        {
            watchlistDetectionManager.stop();
            watchlistDetectionManager = null;
            watchlistRegistered = false;
        }

        if (watchlistChangePublisher != null)
        {
            watchlistChangePublisher.stop();
            watchlistChangePublisher = null;
        }

        cellularController.stopCellularRecordScanning();
        wifiController.stopWifiRecordScanning();
        bluetoothController.stopBluetoothRecordScanning();
        gnssController.stopGnssRecordScanning();
        cellularController.stopCdrEvents();
        removeLocationListener();
        stopDeviceStatusReport(true);
        stopAllLogging();

        if (surveyStallWatchdog != null) surveyStallWatchdog.stop();
        serviceLooper.quitSafely();
        shutdownNotifications();
        executorService.shutdown();
        deviceStatusExecutor.shutdown();

        cellularController.onDestroy();
        wifiController.onDestroy();
        bluetoothController.onDestroy();
        gnssController.onDestroy();

        surveyServiceBinder.onDestroy();
        surveyServiceBinder = null;

        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key)
    {
        // SharedPreferences snapshots its listeners when a commit starts and calls them later on
        // the main thread, so a change made just before onDestroy unregistered this listener can
        // still arrive after the controllers and loggers were torn down.
        if (key == null || destroyed) return;

        switch (key)
        {
            case NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB:
                cellularController.onRolloverPreferenceChanged();
                wifiController.onRolloverPreferenceChanged();
                bluetoothController.onRolloverPreferenceChanged();
                gnssController.onRolloverPreferenceChanged();

                deviceStatusCsvLogger.onSharedPreferenceChanged();
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
                mdmOverrideController.onOverrideChanged(mdmOverride);
                // Turning the override on changes no running destination, so nothing else
                // republishes the unlocked policy.
                updateServiceNotification();
                break;
            case NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER:
                updateLocationListener();
                break;
            case NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED:
                updateWatchlistRegistration();
                break;
            case NetworkSurveyConstants.PROPERTY_WATCHLIST_ABSENCE_WINDOW_SECONDS:
                if (watchlistDetectionManager != null) watchlistDetectionManager.refreshConfig();
                break;
            case NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES:
            case NetworkSurveyConstants.PROPERTY_MQTT_ACTIVE_BROKER_PROFILE:
            case MqttConstants.PROPERTY_MQTT_CLIENT_ID:
            case NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED:
                // Posted, never awaited: this runs on the main thread.
                surveyCoordinator.onDestinationSettingsChanged(Destination.MQTT, () -> {
                    reconnectMqttIfBrokerChanged();
                    return Unit.INSTANCE;
                }, this::hasMqttBrokerConfigured);
                break;
            case NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_HOST:
            case NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_PORT:
            case NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_DEVICE_NAME:
                // Posted, never awaited: this runs on the main thread. One save changes all three
                // keys; the second and third find the server unchanged and do nothing.
                surveyCoordinator.onDestinationSettingsChanged(Destination.GRPC, () -> {
                    switchGrpcServerIfRunning();
                    return Unit.INSTANCE;
                }, this::hasGrpcServerConfigured);
                break;
            case NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED:
                handleWatchlistStreamingPreferenceChange();
                break;

            case NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT:
                handleBatteryPreferenceChange();
                break;

            case NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT:
                handleStreamingQueueLimitChange();
                break;

            case NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID:
            case NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB:
                // The community databases take the record types their targets take, so a running
                // survey follows the change (and starts them once a first target is picked)
                if (surveyCoordinator != null) surveyCoordinator.retrySkipped();
                break;

            default:
                break;
        }
    }

    /**
     * @return The managed configuration in force: the platform's, or the one a test put in place.
     */
    private RestrictionsSnapshot currentRestrictions()
    {
        final RestrictionsSource testSource = restrictionsSourceForTest;
        if (testSource != null) return testSource.snapshot();
        return platformRestrictionsSource != null ? platformRestrictionsSource.snapshot()
                : new AndroidRestrictionsSource(this).snapshot();
    }

    /**
     * Replaces the managed configuration the survey service reads, because a real phone needs a
     * device policy controller to set one. Null restores the platform's.
     */
    @VisibleForTesting
    void setRestrictionsSourceForTest(RestrictionsSource source)
    {
        restrictionsSourceForTest = source;
    }

    /**
     * Creates the survey setup store, the orchestrator and the coordinator that every survey start
     * and stop goes through, and the controller that reacts to MDM and override changes.
     */
    private void initializeSurveyCoordinator()
    {
        final Context context = getApplicationContext();
        surveySetupStore = new SurveySetupStore(context);
        missionSessionTracker.restoreLastSession(surveySetupStore.getLastSession());
        platformRestrictionsSource = new AndroidRestrictionsSource(context);
        final RestrictionsSource restrictionsSource = this::currentRestrictions;
        final SurveyOrchestrator orchestrator = new SurveyOrchestrator(new ServiceSurveyOutputs(this),
                () -> surveyCoordinator.policy());
        surveyCoordinator = new SurveyCoordinator(orchestrator, surveySetupStore, restrictionsSource,
                () -> mdmOverride, surveyControlExecutor, () -> {
            updateServiceNotification();
            return Unit.INSTANCE;
        });
        mdmOverrideController = new MdmOverrideController(surveyCoordinator, surveySetupStore,
                PreferenceManager.getDefaultSharedPreferences(context), restrictionsSource,
                () -> {
                    sendSingleDeviceStatus();
                    return Unit.INSTANCE;
                },
                () -> {
                    copyOrganizationBrokerToUserSettings();
                    return Unit.INSTANCE;
                },
                () -> {
                    reconnectMqttIfBrokerChanged();
                    return Unit.INSTANCE;
                });
        mdmOverrideController.initialize();
    }

    /**
     * Creates the {@link DefaultMqttConnection} instance.
     */
    public void initializeMqttConnection()
    {
        mqttConnection = new MqttConnection();
        mqttConnection.registerMqttConnectionStateListener(this);
        mqttConnection.registerQueueBackpressureListener(this);

        // Set the streaming queue limit from preferences
        final int queueLimit = PreferenceUtils.getStreamingQueueLimit(getApplicationContext());
        mqttConnection.setStreamingQueueLimit(queueLimit);
        Timber.d("Initialized MQTT connection with streaming queue limit: %d", queueLimit);
    }

    /**
     * Connects to the broker the survey should use (an Intent API broker, then the MDM broker unless
     * overridden, then the user's broker) streaming the given record types.
     *
     * @return True if a connection was started, false if no broker is configured.
     */
    public boolean connectToMqttBrokerForSurvey(Set<RecordType> types)
    {
        final BrokerConnectionInfo connectionInfo = intentBrokerConnectionInfo != null
                ? intentBrokerConnectionInfo : getActiveBrokerConnectionInfo();
        if (connectionInfo == null) return false;

        connectToMqttBroker(connectionInfo, types);
        return true;
    }

    /**
     * Connect to an MQTT broker streaming the given record types. The record type flags in the
     * connection info are ignored; only its device status flag is used.
     *
     * @param connectionInfo The information needed to connect to the MQTT broker.
     * @param types          The record types to stream.
     */
    private void connectToMqttBroker(BrokerConnectionInfo connectionInfo, Set<RecordType> types)
    {
        // Start or stop watchlist streaming BEFORE connecting so the CONNECTED callback, which publishes
        // the initial watchlist snapshot, can never fire while the publisher is still null (the publisher's
        // first flow emission publishes a snapshot that is simply dropped while disconnected). Watchlist
        // streaming is governed by the Watchlist "Stream over MQTT" setting rather than a per-connection
        // stream flag; the stop call covers a reconnect (for example an MDM reconfiguration) that never
        // goes through disconnectFromMqttBroker().
        if (isWatchlistStreamingEnabled())
        {
            startWatchlistStreaming();
        } else
        {
            stopWatchlistStreaming();
        }

        mqttConnection.connect(getApplicationContext(), connectionInfo);
        rememberConnectedBroker(connectionInfo);

        // Track survey session when MQTT streaming starts
        onSurveyStarted(true);
        updateWakeLock();

        setMqttStreamTypes(types);

        if (isDeviceStatusStreamEnabled(connectionInfo))
        {
            registerDeviceStatusListener(mqttConnection);
        }
    }

    /**
     * Changes the record types streamed over the MQTT connection without reconnecting, by
     * registering and unregistering the connection as a record listener.
     *
     * @param types The record types to stream from now on (CDR is ignored; it is files only).
     */
    public void setMqttStreamTypes(Set<RecordType> types)
    {
        synchronized (mqttStreamTypes)
        {
            applyListenerTypes(mqttStreamTypes, types, mqttConnection, mqttConnection, mqttConnection,
                    mqttConnection, mqttConnection);
        }
    }

    /**
     * @return A copy of the record types the MQTT connection streams.
     */
    public Set<RecordType> getMqttStreamTypes()
    {
        synchronized (mqttStreamTypes)
        {
            return EnumSet.copyOf(mqttStreamTypes);
        }
    }

    /**
     * Registers and unregisters one destination's record listeners so that it receives exactly the
     * given record types, and updates {@code current} to match.
     */
    private void applyListenerTypes(Set<RecordType> current, Set<RecordType> wanted,
                                    ICellularSurveyRecordListener cellularListener,
                                    IPhoneStateListener phoneStateListener,
                                    IWifiSurveyRecordListener wifiListener,
                                    IBluetoothSurveyRecordListener bluetoothListener,
                                    IGnssSurveyRecordListener gnssListener)
    {
        // Register additions first so a type swap never leaves a scanner without a listener in between.
        for (RecordType type : RecordType.values())
        {
            if (wanted.contains(type) && !current.contains(type))
            {
                switch (type)
                {
                    case CELLULAR -> registerCellularSurveyRecordListener(cellularListener);
                    case PHONE_STATE -> registerPhoneStateListener(phoneStateListener);
                    case WIFI -> registerWifiSurveyRecordListener(wifiListener);
                    case BLUETOOTH -> registerBluetoothSurveyRecordListener(bluetoothListener);
                    case GNSS -> registerGnssSurveyRecordListener(gnssListener);
                    default ->
                    {
                        // CDR is files only.
                        continue;
                    }
                }
                current.add(type);
            }
        }
        for (RecordType type : RecordType.values())
        {
            if (!wanted.contains(type) && current.contains(type))
            {
                switch (type)
                {
                    case CELLULAR -> unregisterCellularSurveyRecordListener(cellularListener);
                    case PHONE_STATE -> unregisterPhoneStateListener(phoneStateListener);
                    case WIFI -> unregisterWifiSurveyRecordListener(wifiListener);
                    case BLUETOOTH -> unregisterBluetoothSurveyRecordListener(bluetoothListener);
                    case GNSS -> unregisterGnssSurveyRecordListener(gnssListener);
                    default ->
                    {
                    }
                }
                current.remove(type);
            }
        }
    }

    /**
     * Begin streaming Wi-Fi Watchlist match events and watchlist snapshots over MQTT.
     * <p>
     * Match events are published by attaching the MQTT connection as the detection manager's match
     * listener (when a manager exists; if watchlist detection is enabled later the listener is attached
     * at that point). Watchlist state is published by a {@link WatchlistChangePublisher} that publishes
     * a full snapshot on every change. The initial snapshot is published from
     * {@link #onConnectionStateChange(ConnectionState)} once the connection is established, so it also
     * fires on a silent auto-reconnect.
     */
    private void startWatchlistStreaming()
    {
        mqttWatchlistStreamEnabled = true;

        if (watchlistDetectionManager != null)
        {
            watchlistDetectionManager.setMatchListener(mqttConnection);
        }

        if (watchlistChangePublisher == null)
        {
            watchlistChangePublisher = new WatchlistChangePublisher(getApplicationContext(), deviceId,
                    this::getMissionIdForRecords, mqttConnection);
            watchlistChangePublisher.start();
        }
    }

    /**
     * Stop streaming Wi-Fi Watchlist events over MQTT.
     */
    private void stopWatchlistStreaming()
    {
        mqttWatchlistStreamEnabled = false;

        if (watchlistDetectionManager != null)
        {
            watchlistDetectionManager.setMatchListener(null);
        }

        if (watchlistChangePublisher != null)
        {
            watchlistChangePublisher.stop();
            watchlistChangePublisher = null;
        }
    }

    /**
     * Disconnect from the MQTT broker and also remove the MQTT survey record listener.
     */
    public void disconnectFromMqttBroker()
    {
        callOnSurveyThread(() -> {
            disconnectFromMqttBrokerNow();
            surveyCoordinator.onRunningStateChanged();
            return true;
        });
    }

    /**
     * Disconnects from the MQTT broker and removes the MQTT record listeners. Must run on the survey
     * control thread.
     */
    private void disconnectFromMqttBrokerNow()
    {
        Timber.i("Disconnecting from the MQTT Broker");

        mqttConnection.disconnect();
        intentBrokerConnectionInfo = null;
        connectedBrokerFingerprint = null;
        connectedToOrganizationBroker = false;

        releaseMqttListeners();

        // Track survey session end
        onSurveyStopped();
        updateWakeLock();
    }

    /**
     * Removes everything the MQTT stream registered (its record and device status listeners, its
     * record types, watchlist streaming and the queue backpressure state), so the scanners it kept
     * running can stop. It does not touch the connection itself: after the client gave up (for
     * example a rejected sign-in), disconnecting would clear the failure the status UI shows.
     * Idempotent, so a deliberate disconnect followed by its DISCONNECTED callback is harmless.
     * Must run on the survey control thread.
     */
    private void releaseMqttListeners()
    {
        unregisterCellularSurveyRecordListener(mqttConnection);
        unregisterWifiSurveyRecordListener(mqttConnection);
        unregisterBluetoothSurveyRecordListener(mqttConnection);
        unregisterGnssSurveyRecordListener(mqttConnection);
        unregisterDeviceStatusListener(mqttConnection);
        unregisterPhoneStateListener(mqttConnection);
        synchronized (mqttStreamTypes)
        {
            mqttStreamTypes.clear();
        }
        stopWatchlistStreaming();

        // Clear queue backpressure states since there's no active connection to be backed up
        clearMqttBackpressureStates();
    }

    /**
     * If MQTT is streaming and the broker it should use (MDM unless overridden, otherwise the user's)
     * is not the one it is connected to, reconnects to the right broker keeping the same record
     * types. If no broker is configured anymore, MQTT is stopped.
     */
    private void reconnectMqttIfBrokerChanged()
    {
        if (!isMqttStreamingActive() || intentBrokerConnectionInfo != null) return;

        if (connectedToOrganizationBroker && getMdmBrokerConnectionInfo() == null)
        {
            // As in v1, the organization's stream ends with its broker; it never moves to the
            // user's broker.
            Timber.i("The organization's MQTT broker was removed, so the MQTT stream is stopped");
            disconnectFromMqttBrokerNow();
            return;
        }

        final BrokerConnectionInfo desired = getActiveBrokerConnectionInfo();
        if (desired == null)
        {
            Timber.i("No MQTT broker is configured anymore, so the MQTT stream is stopped");
            disconnectFromMqttBrokerNow();
            return;
        }

        if (!brokerFingerprint(desired).equals(connectedBrokerFingerprint))
        {
            Timber.i("The MQTT broker changed, so the MQTT stream is reconnected");
            reconnectMqttBroker(desired);
        }

        // The device status and watchlist streams have their own (MDM aware) settings, which a
        // configuration push may have changed without changing the broker.
        if (isDeviceStatusStreamEnabled(desired))
        {
            registerDeviceStatusListener(mqttConnection);
        } else
        {
            unregisterDeviceStatusListener(mqttConnection);
        }
        handleWatchlistStreamingPreferenceChange();
    }

    /**
     * Moves the running MQTT stream to another broker, keeping its record types and listeners.
     * The disconnect does not go through {@link #disconnectFromMqttBroker()} because that would
     * unregister the listeners (possibly stopping this service) and end the survey session.
     */
    private void reconnectMqttBroker(BrokerConnectionInfo connectionInfo)
    {
        mqttConnection.disconnect();
        mqttConnection.connect(getApplicationContext(), connectionInfo);
        rememberConnectedBroker(connectionInfo);
    }

    /**
     * Records which broker the MQTT connection now uses: its fingerprint, and whether it is the
     * organization's broker in force (not overridden).
     */
    private void rememberConnectedBroker(BrokerConnectionInfo connectionInfo)
    {
        connectedBrokerFingerprint = brokerFingerprint(connectionInfo);
        final BrokerConnectionInfo organizationBroker = isMqttMdmOverrideEnabled() ? null : getMdmBrokerConnectionInfo();
        connectedToOrganizationBroker = organizationBroker != null
                && brokerFingerprint(organizationBroker).equals(connectedBrokerFingerprint);
    }

    /**
     * @return The broker MQTT should use right now: the MDM broker unless the user overrode the MDM
     * settings, otherwise the user's broker. Null if none is configured.
     */
    private BrokerConnectionInfo getActiveBrokerConnectionInfo()
    {
        if (!isMqttMdmOverrideEnabled())
        {
            final BrokerConnectionInfo mdmConnectionInfo = getMdmBrokerConnectionInfo();
            if (mdmConnectionInfo != null) return mdmConnectionInfo;
        }
        return getUserBrokerConnectionInfo();
    }

    /**
     * @return True if MQTT has a broker to connect to (from an Intent API request, MDM or the user).
     */
    public boolean hasMqttBrokerConfigured()
    {
        return intentBrokerConnectionInfo != null || getActiveBrokerConnectionInfo() != null;
    }

    /**
     * @return True if an MQTT stream with no record types still has something to send: the device
     * status heartbeat is on for the broker a survey connects to (the Intent API broker first, as
     * in {@link #connectToMqttBrokerForSurvey(Set)}), or the watchlist streams over MQTT.
     */
    public boolean mqttSendsWithoutRecordTypes()
    {
        if (isWatchlistStreamingEnabled()) return true;
        final BrokerConnectionInfo connectionInfo = intentBrokerConnectionInfo != null
                ? intentBrokerConnectionInfo : getActiveBrokerConnectionInfo();
        return connectionInfo != null && isDeviceStatusStreamEnabled(connectionInfo);
    }

    /**
     * @return A string that identifies a broker connection, used to tell whether a reconnect is needed.
     */
    private static String brokerFingerprint(BrokerConnectionInfo info)
    {
        final String deviceName = info instanceof MqttConnectionInfo ? ((MqttConnectionInfo) info).getDeviceName() : null;
        return info.getMqttBrokerHost() + ":" + info.getPortNumber() + ":" + info.isTlsEnabled() + ":"
                + info.getMqttClientId() + ":" + info.getMqttUsername() + ":" + info.getMqttPassword() + ":"
                + info.getTopicPrefix() + ":" + info.getMqttQos() + ":" + deviceName + ":"
                + isDeviceStatusStreamEnabled(info);
    }

    /**
     * @return True if the device status heartbeat should be streamed over this MQTT connection.
     */
    private static boolean isDeviceStatusStreamEnabled(BrokerConnectionInfo info)
    {
        return !(info instanceof MqttConnectionInfo) || ((MqttConnectionInfo) info).isDeviceStatusStreamEnabled();
    }

    /**
     * Saves a copy of the organization's MQTT broker as a broker profile and selects it, so that when
     * the user turns on "Override organization settings" the stream keeps going to the same broker
     * until they change it.
     */
    private void copyOrganizationBrokerToUserSettings()
    {
        final MqttConnectionInfo mdmConnectionInfo = getMdmBrokerConnectionInfo();
        if (mdmConnectionInfo == null) return;

        brokerProfileStore.addCopyOfOrganization(mdmConnectionInfo,
                getString(R.string.mqtt_broker_organization_copy_name));
    }

    /**
     * @return The current connection state to the MQTT Broker.
     */
    public ConnectionState getMqttConnectionState()
    {
        if (mqttConnection != null) return mqttConnection.getConnectionState();

        return ConnectionState.DISCONNECTED;
    }

    public GpsListener getPrimaryLocationListener()
    {
        return primaryLocationListener;
    }

    /**
     * Registers a new listener for changes to the location information.
     */
    public void registerLocationListener(LocationListener locationListener)
    {
        primaryLocationListener.registerListener(locationListener);
    }

    /**
     * Unregisters a listener for changes to the location information.
     *
     * @since 1.6.0
     */
    public void unregisterLocationListener(LocationListener locationListener)
    {
        primaryLocationListener.unregisterListener(locationListener);
    }

    /**
     * Registers a listener for notifications when new cellular survey records are available.
     *
     * @param surveyRecordListener The survey record listener to register.
     */
    public void registerCellularSurveyRecordListener(ICellularSurveyRecordListener
                                                             surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.registerCellularSurveyRecordListener(surveyRecordListener);
        }

        cellularController.startCellularRecordScanning(); // Only starts scanning if it is not already active.
        startDeviceStatusReportIfLoggingEnabled(); // Only starts the device status report if it is not already active.
    }

    /**
     * Unregisters a cellular survey record listener.
     * <p>
     * If the listener being removed is the last listener and nothing else is using this {@link NetworkSurveyService},
     * then this service is shutdown and will need to be restarted before it can be used again.
     *
     * @param surveyRecordListener The listener to unregister.
     */
    public void unregisterCellularSurveyRecordListener(ICellularSurveyRecordListener
                                                               surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.unregisterCellularSurveyRecordListener(surveyRecordListener);
            if (!surveyRecordProcessor.isCellularBeingUsed())
            {
                cellularController.stopCellularRecordScanning();
            }
        }

        stopDeviceStatusReportIfNotNeeded();

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
        startDeviceStatusReportIfLoggingEnabled(); // Only starts the device status report if it is not already active.
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
    public void unregisterWifiSurveyRecordListener(IWifiSurveyRecordListener
                                                           surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.unregisterWifiSurveyRecordListener(surveyRecordListener);
            if (!surveyRecordProcessor.isWifiBeingUsed())
            {
                wifiController.stopWifiRecordScanning();
            }
        }

        stopDeviceStatusReportIfNotNeeded();

        // Check to see if this service is still needed.  It is still needed if we are either logging, the UI is
        // visible, or a server connection is active.
        if (!isBeingUsed()) stopSelf();
    }

    /**
     * Registers or unregisters the Watchlist detector based on the watchlist-enabled preference.
     * <p>
     * The detector is registered as a PASSIVE Wi-Fi listener, so it observes scan results only while
     * a survey is already scanning Wi-Fi and never starts scanning or keeps the service alive on its
     * own. This is the "detection requires a survey running" model. The manager is recreated on each
     * enable so its coroutine scope is fresh after a previous disable.
     */
    public void updateWatchlistRegistration()
    {
        if (surveyRecordProcessor == null) return;

        final boolean enabled = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, NetworkSurveyConstants.DEFAULT_WATCHLIST_ENABLED);

        if (enabled && !watchlistRegistered)
        {
            if (watchlistDetectionManager == null)
            {
                watchlistDetectionManager = new WatchlistDetectionManager(getApplicationContext(), deviceId,
                        this::getMissionIdForRecords);
            }
            surveyRecordProcessor.registerPassiveWifiSurveyRecordListener(watchlistDetectionManager);
            watchlistDetectionManager.start();
            // Attach the match listener to the freshly created manager if watchlist streaming is already
            // active, since the detection and MQTT lifecycles are independent.
            if (mqttWatchlistStreamEnabled)
            {
                watchlistDetectionManager.setMatchListener(mqttConnection);
            }
            watchlistRegistered = true;
            Timber.i("Watchlist detection enabled");
        } else if (!enabled && watchlistRegistered)
        {
            surveyRecordProcessor.unregisterPassiveWifiSurveyRecordListener(watchlistDetectionManager);
            if (watchlistDetectionManager != null)
            {
                watchlistDetectionManager.stop();
                watchlistDetectionManager = null;
            }
            watchlistRegistered = false;
            Timber.i("Watchlist detection disabled");
        }
    }

    /**
     * @return true if the Wi-Fi Watchlist should be streamed while an MQTT connection is active. This is
     * controlled by the "Stream over MQTT" switch in the Watchlist settings (or the matching MDM
     * restriction), not by a per-connection stream flag like the survey streams.
     */
    private boolean isWatchlistStreamingEnabled()
    {
        return PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED,
                NetworkSurveyConstants.DEFAULT_MQTT_WATCHLIST_STREAM_SETTING, getApplicationContext());
    }

    /**
     * Starts or stops watchlist streaming when the Watchlist "Stream over MQTT" setting changes while an
     * MQTT connection is active. On enable, the current snapshot is published immediately so the backend
     * is reconciled without waiting for the next watchlist change or reconnect.
     */
    private void handleWatchlistStreamingPreferenceChange()
    {
        if (mqttConnection == null || mqttConnection.getConnectionState() == ConnectionState.DISCONNECTED)
        {
            return;
        }

        if (isWatchlistStreamingEnabled())
        {
            startWatchlistStreaming();
            if (watchlistChangePublisher != null) watchlistChangePublisher.publishSnapshot();
        } else
        {
            stopWatchlistStreaming();
        }
    }

    /**
     * Registers a listener for notifications when new Bluetooth survey records are available.
     *
     * @param surveyRecordListener The survey record listener to register.
     * @since 1.0.0
     */
    public void registerBluetoothSurveyRecordListener(IBluetoothSurveyRecordListener
                                                              surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.registerBluetoothSurveyRecordListener(surveyRecordListener);
        }

        bluetoothController.startBluetoothRecordScanning(); // Only starts scanning if it is not already active.
        startDeviceStatusReportIfLoggingEnabled(); // Only starts the device status report if it is not already active.
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
    public void unregisterBluetoothSurveyRecordListener(IBluetoothSurveyRecordListener
                                                                surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.unregisterBluetoothSurveyRecordListener(surveyRecordListener);
            if (!surveyRecordProcessor.isBluetoothBeingUsed())
            {
                bluetoothController.stopBluetoothRecordScanning();
            }
        }

        stopDeviceStatusReportIfNotNeeded();

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
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.registerGnssSurveyRecordListener(surveyRecordListener);
        }

        gnssController.startGnssRecordScanning(); // Only starts scanning if it is not already active.
        startDeviceStatusReportIfLoggingEnabled(); // Only starts the device status report if it is not already active.
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
    public void unregisterGnssSurveyRecordListener(IGnssSurveyRecordListener
                                                           surveyRecordListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.unregisterGnssSurveyRecordListener(surveyRecordListener);
            if (!surveyRecordProcessor.isGnssBeingUsed())
            {
                gnssController.stopGnssRecordScanning();
            }
        }

        stopDeviceStatusReportIfNotNeeded();

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
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.registerCdrEventListener(listener);
        }

        cellularController.startCdrEvents(); // Only starts if it is not already active

        // Don't call startDeviceStatusReportIfLoggingEnabled() here because CDR events are not part of the device status report.
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
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.unregisterCdrEventListener(listener);
            if (!surveyRecordProcessor.isCdrBeingUsed()) cellularController.stopCdrEvents();
        }

        // Don't call stopDeviceStatusReportIfNotNeeded() here because CDR events are not part of the device status report.

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

            startDeviceStatusReport(true); // Only starts scanning if it is not already active.
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
                if (!surveyRecordProcessor.isDeviceStatusBeingUsed()) stopDeviceStatusReport(true);
            }
        }

        // Check to see if this service is still needed.  It is still needed if we are either logging, the UI is
        // visible, or a server connection is active.
        if (!isBeingUsed()) stopSelf();
    }

    /**
     * Registers a listener for notifications when new phone state messages are available.
     * If this is the first phone state listener, the Android PhoneStateListener is started.
     *
     * @param phoneStateListener The phone state listener to register.
     */
    public void registerPhoneStateListener(IPhoneStateListener phoneStateListener)
    {
        boolean wasEmpty = surveyRecordProcessor != null && !surveyRecordProcessor.isPhoneStateBeingUsed();

        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.registerPhoneStateListener(phoneStateListener);
        }

        if (wasEmpty)
        {
            cellularController.startPhoneStateListener();
        }
    }

    /**
     * Unregisters a phone state message listener.
     * <p>
     * If no more phone state listeners remain, the Android PhoneStateListener is stopped.
     * If the listener being removed is the last listener and nothing else is using this
     * {@link NetworkSurveyService}, then this service is shutdown.
     *
     * @param phoneStateListener The listener to unregister.
     */
    public void unregisterPhoneStateListener(IPhoneStateListener phoneStateListener)
    {
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.unregisterPhoneStateListener(phoneStateListener);
            if (!surveyRecordProcessor.isPhoneStateBeingUsed())
            {
                cellularController.stopPhoneStateListener();
            }
        }

        if (!isBeingUsed()) stopSelf();
    }

    /**
     * Used to check if this service is still needed.
     * <p>
     * This service is still needed if logging is enabled, if the UI is visible, or if a connection is active.  In other
     * words, if there is an active consumer of the survey records.
     *
     * @return True if there is an active consumer of the survey records produced by this service, false otherwise.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isBeingUsed()
    {
        return cellularController.isLoggingEnabled()
                || cellularController.isPhoneStateLoggingEnabled()
                || wifiController.isLoggingEnabled()
                || bluetoothController.isLoggingEnabled()
                || gnssController.isLoggingEnabled()
                || cellularController.isCdrLoggingEnabled()
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
        if (surveyRecordProcessor != null)
        {
            surveyRecordProcessor.onUiVisible(networkSurveyActivity);
        }

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
        Boolean result = cellularController.toggleLogging(enable);
        if (result != null)
        {
            if (enable && result)
            {
                onSurveyStarted(true);

                // Auto-include phone state with cellular if the preference is enabled
                if (shouldAutoIncludePhoneState())
                {
                    cellularController.autoStartPhoneStateIfNotRunning();
                }
            } else if (!enable && !result)
            {
                // Auto-stop phone state if it was auto-started by cellular
                if (cellularController.isPhoneStateAutoStartedByCellular() && isPhoneStateLoggingEnabled())
                {
                    cellularController.togglePhoneStateLogging(false);
                    cellularController.setPhoneStateAutoStartedByCellular(false);
                }

                onSurveyStopped();
            }
            updateWakeLock();
        }
        return result;
    }

    /**
     * @return True if the auto-include phone state with cellular preference is enabled.
     */
    private boolean shouldAutoIncludePhoneState()
    {
        return PreferenceUtils.getAutoStartPreference(
                NetworkSurveyConstants.PROPERTY_AUTO_INCLUDE_PHONE_STATE_WITH_CELLULAR,
                true, getApplicationContext());
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
        Boolean result = wifiController.toggleLogging(enable);
        if (result != null)
        {
            if (enable && result)
            {
                onSurveyStarted(true);
            } else if (!enable && !result) onSurveyStopped();
            updateWakeLock();
        }
        return result;
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
        Boolean result = bluetoothController.toggleLogging(enable);
        if (result != null)
        {
            if (enable && result)
            {
                onSurveyStarted(true);
            } else if (!enable && !result) onSurveyStopped();
            updateWakeLock();
        }
        return result;
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
        Boolean result = gnssController.toggleLogging(enable);
        if (result != null)
        {
            if (enable && result)
            {
                onSurveyStarted(true);
            } else if (!enable && !result) onSurveyStopped();
            updateWakeLock();
        }
        return result;
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

    /**
     * Toggles the phone state logging setting.
     * <p>
     * Clears the auto-started-by-cellular flag on ANY manual toggle (both enable and disable)
     * so the user "takes ownership" of phone state whenever they interact with it directly.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @return The new state of logging. True if it is enabled, or false if it is disabled. Null is
     * returned if the toggling was unsuccessful.
     */
    public Boolean togglePhoneStateLogging(boolean enable)
    {
        cellularController.setPhoneStateAutoStartedByCellular(false);
        return toggleImmediateFireSurvey(enable, cellularController::togglePhoneStateLogging);
    }

    public boolean isPhoneStateLoggingEnabled()
    {
        return cellularController.isPhoneStateLoggingEnabled();
    }

    public Boolean toggleCdrLogging(boolean enable)
    {
        return toggleImmediateFireSurvey(enable, cellularController::toggleCdrLogging);
    }

    /**
     * A controller logging toggle that returns the new logging state (true enabled, false disabled)
     * or null when the toggle was unsuccessful.
     */
    @FunctionalInterface
    private interface LoggingToggle
    {
        Boolean toggle(boolean enable);
    }

    /**
     * Toggles a survey whose controller registers a listener that can emit a record immediately on
     * registration (phone state and CDR both register for {@code LISTEN_SERVICE_STATE}, which
     * delivers the current state right away). The Mission ID must be rolled before that registration
     * so the immediate record cannot capture a pre-roll value, but the controller toggle is what
     * performs the registration, so the roll is done optimistically first and undone if the toggle
     * reports the start failed.
     *
     * @param enable           True to start the survey, false to stop it.
     * @param controllerToggle The controller toggle to invoke.
     * @return The controller's result: the new logging state, or null if the toggle was unsuccessful.
     */
    private Boolean toggleImmediateFireSurvey(boolean enable, LoggingToggle controllerToggle)
    {
        final MissionSessionTracker.Snapshot missionBeforeStart = missionSessionTracker.snapshot();

        if (enable) onSurveyStarted(true);

        final Boolean result = controllerToggle.toggle(enable);
        if (result != null)
        {
            if (!enable && !result) onSurveyStopped();
            updateWakeLock();
        }

        // The controller returns null when the start failed. Undo the optimistic roll so a failed
        // start never leaves behind a rolled Mission ID that no record will ever carry.
        if (enable && result == null)
        {
            missionSessionTracker.undoOptimisticStart(missionBeforeStart);
        }

        return result;
    }

    /**
     * Starts or stops saving records for the community databases (OpenCelliD and BeaconDB).
     *
     * @param enable True to start, false to stop.
     * @param types  The record types to save when starting, already narrowed to what the selected
     *               upload targets take; a type the survey didn't select is never scanned for them.
     */
    public UploadScanningResult toggleUploadRecordSaving(boolean enable, Set<RecordType> types)
    {
        synchronized (destinationToggleLock)
        {
            return toggleUploadRecordSavingLocked(enable, types);
        }
    }

    /**
     * @return The record types being saved for the community databases; empty when not running.
     */
    public Set<RecordType> getCommunityTypes()
    {
        synchronized (destinationToggleLock)
        {
            return communityTypes.isEmpty() ? EnumSet.noneOf(RecordType.class) : EnumSet.copyOf(communityTypes);
        }
    }

    /**
     * Changes the record types saved for the community databases while they run, starting or
     * stopping the Cellular and Wi-Fi listeners to match.
     *
     * @param types The record types to save, already narrowed to what the upload targets take.
     */
    public void setCommunityTypes(Set<RecordType> types)
    {
        synchronized (destinationToggleLock)
        {
            if (dbUploadStore == null) return;

            final boolean cellular = types.contains(RecordType.CELLULAR) && PreferenceUtils.shouldStartCellularForUpload(this);
            final boolean wifi = types.contains(RecordType.WIFI) && PreferenceUtils.shouldStartWifiForUpload(this);
            if (cellular != communityTypes.contains(RecordType.CELLULAR))
            {
                if (cellular) registerCellularSurveyRecordListener(dbUploadStore);
                else unregisterCellularSurveyRecordListener(dbUploadStore);
            }
            if (wifi != communityTypes.contains(RecordType.WIFI))
            {
                if (wifi) registerWifiSurveyRecordListener(dbUploadStore);
                else unregisterWifiSurveyRecordListener(dbUploadStore);
            }

            communityTypes.clear();
            if (cellular) communityTypes.add(RecordType.CELLULAR);
            if (wifi) communityTypes.add(RecordType.WIFI);
        }
        updateServiceNotification();
    }

    private UploadScanningResult toggleUploadRecordSavingLocked(boolean enable, Set<RecordType> types)
    {
        try
        {
            // The organization's upload gate only refuses a start. A stop must always work, or a
            // policy change mid survey would leave Community saving records that Stop can't end.
            if (enable && !SurveyPolicy.isExternalDataUploadAllowed(currentRestrictions()))
            {
                return new UploadScanningResult(false, false, getString(R.string.upload_disabled_via_mdm));
            }

            if (enable && dbUploadStore != null)
            {
                return new UploadScanningResult(true, true, getString(R.string.upload_saving_start_toast));
            } else if (!enable && dbUploadStore == null)
            {
                return new UploadScanningResult(true, false, getString(R.string.upload_saving_stop_toast));
            }

            if (enable)
            {
                // Only start surveys for protocols the survey selected and the upload targets need
                final boolean shouldStartCellular = types.contains(RecordType.CELLULAR) && PreferenceUtils.shouldStartCellularForUpload(this);
                final boolean shouldStartWifi = types.contains(RecordType.WIFI) && PreferenceUtils.shouldStartWifiForUpload(this);

                if (!shouldStartCellular && !shouldStartWifi)
                {
                    Timber.w("No upload targets selected, so no survey scanning will be started");
                    return new UploadScanningResult(false, false, getString(R.string.upload_saving_no_targets));
                }

                // A new upload session checks the cell the phone is already on, and starts with no
                // towers tagged NEW on the map
                NewTowerRegistry.startSession();
                if (surveyRecordProcessor != null) surveyRecordProcessor.resetNewTowerDetection();

                dbUploadStore = new DbUploadStore(this);
                if (PreferenceUtils.isAutoUploadEnabled(this))
                {
                    autoUploadManager = new AutoUploadManager(this, batteryMonitor, this, this::updateServiceNotification);
                    dbUploadStore.setUploadRecordCountListener(autoUploadManager);
                } else
                {
                    dbUploadStore.setUploadRecordCountListener(this);
                }
                dbUploadStore.resetLastLocations();

                Set<SurveyTypes> surveysStarted = new LinkedHashSet<>();

                if (shouldStartCellular)
                {
                    registerCellularSurveyRecordListener(dbUploadStore);
                    surveysStarted.add(SurveyTypes.CELLULAR);
                    communityTypes.add(RecordType.CELLULAR);
                }

                if (shouldStartWifi)
                {
                    registerWifiSurveyRecordListener(dbUploadStore);
                    surveysStarted.add(SurveyTypes.WIFI);
                    communityTypes.add(RecordType.WIFI);
                }

                // Track survey session. OpenCelliD/BeaconDB uploads discard the Mission ID, so this
                // is not a mission relevant survey and must not roll a new Mission ID.
                onSurveyStarted(false);
                updateWakeLock();
                updateServiceNotification();

                // Generate appropriate success message based on what was started
                String message;
                if (shouldStartCellular && shouldStartWifi)
                {
                    message = getString(R.string.upload_saving_started_cellular_wifi);
                } else if (shouldStartCellular)
                {
                    message = getString(R.string.upload_saving_started_cellular);
                } else
                {
                    message = getString(R.string.upload_saving_start_toast); // Fallback
                }

                return new UploadScanningResult(true, true, message, surveysStarted);
            } else
            {
                unregisterCellularSurveyRecordListener(dbUploadStore);
                unregisterWifiSurveyRecordListener(dbUploadStore);

                if (autoUploadManager != null)
                {
                    autoUploadManager.reset();
                    autoUploadManager = null;

                    // Automatic upload was on, so upload what is left now (or once the phone is on
                    // a network it allows) instead of leaving it for the next survey
                    executorService.execute(() -> {
                        try
                        {
                            AutoUploadRequest.uploadRemaining(this);
                        } catch (Exception e)
                        {
                            Timber.e(e, "Failed to upload the community records left at the end of the survey");
                        }
                    });
                }

                dbUploadStore = null;
                communityTypes.clear();

                // Track survey session end
                onSurveyStopped();
                updateWakeLock();
                NewTowerNotificationHelper.INSTANCE.resetSessionCount();
                NewTowerRegistry.endSession();
                updateServiceNotification();

                // Check to see if this service is still needed.  It is still needed if we are either logging, the UI is
                // visible, or a server connection is active.
                if (!isBeingUsed()) stopSelf();

                return new UploadScanningResult(true, false, getString(R.string.upload_saving_stop_toast));
            }
        } catch (Exception e)
        {
            Timber.e(e, "Failed to toggle upload record saving");
            return new UploadScanningResult(false, false, getString(R.string.upload_saving_toggle_failed));
        }
    }

    /**
     * Toggles NS Analytics scanning on or off. When started this way (the NS Analytics screen, or an
     * older control), NS Analytics receives the record types of the running survey, or of the
     * user's survey setup when nothing is running.
     *
     * @param enable True to enable NS Analytics scanning, false to disable
     * @return Result indicating success/failure and whether scanning was started or stopped
     */
    public UploadScanningResult toggleNsAnalyticsScanning(boolean enable)
    {
        final UploadScanningResult result = callOnSurveyThread(() ->
                toggleNsAnalyticsScanning(enable, enable ? getStreamTypesForNewDestination(Destination.NS_ANALYTICS)
                        : Collections.emptySet()));
        return result != null ? result
                : new UploadScanningResult(false, false, getString(R.string.ns_analytics_survey_toggle_failed));
    }

    /**
     * Toggles NS Analytics scanning on or off. When enabled, survey records of the given types are
     * queued to the NS Analytics data store for upload to the NS Analytics backend.
     *
     * @param enable True to enable NS Analytics scanning, false to disable
     * @param types  The record types to collect when enabling (CDR is ignored).
     * @return Result indicating success/failure and whether scanning was started or stopped
     */
    public UploadScanningResult toggleNsAnalyticsScanning(boolean enable, Set<RecordType> types)
    {
        synchronized (destinationToggleLock)
        {
            return toggleNsAnalyticsScanningLocked(enable, types);
        }
    }

    private UploadScanningResult toggleNsAnalyticsScanningLocked(boolean enable, Set<RecordType> types)
    {
        try
        {
            boolean isCurrentlyActive = (nsAnalyticsDataStore != null);

            if (enable && !isCurrentlyActive)
            {
                // Check if device is registered with NS Analytics
                if (!isNsAnalyticsRegistered())
                {
                    return new UploadScanningResult(false, false,
                            getString(R.string.ns_analytics_not_registered));
                }

                if (!MdmUtils.isNsAnalyticsAllowed(this))
                {
                    return new UploadScanningResult(false, false,
                            getString(R.string.ns_analytics_not_allowed_by_organization));
                }

                final Set<RecordType> collectedTypes = DestinationCapabilities.typesFor(Destination.NS_ANALYTICS,
                        types, Collections.emptySet());
                if (collectedTypes.isEmpty())
                {
                    Timber.w("No NS Analytics record types selected, so no survey scanning will be started");
                    return new UploadScanningResult(false, false,
                            getString(R.string.ns_analytics_survey_no_protocols_enabled));
                }

                nsAnalyticsDataStore = new NsAnalyticsDataStore(this);
                nsAnalyticsDataStore.startCollecting();

                // Roll the Mission ID before registering any listener. The phone state listener (and
                // device status) can emit a record as soon as it is registered, so the roll must be
                // committed first or that record would capture a pre-roll Mission ID. This is placed
                // after the bail-outs above so an abandoned start never rolls.
                final MissionSessionTracker.Snapshot missionBeforeStart = missionSessionTracker.snapshot();
                onSurveyStarted(true);

                try
                {
                    setNsAnalyticsTypes(collectedTypes);

                    // Always register for device status
                    registerDeviceStatusListener(nsAnalyticsDataStore);
                } catch (Exception e)
                {
                    // Do not leave a half built data store behind a result that says it failed.
                    Timber.e(e, "Failed to register the NS Analytics listeners; undoing the start");
                    shutdownNsAnalyticsDataStore();
                    missionSessionTracker.undoOptimisticStart(missionBeforeStart);
                    throw e;
                }

                // Uploads are scheduled from the start itself, so a survey started at boot, by the
                // Intent API or from the Survey screen uploads the same way.
                if (PreferenceUtils.isNsAnalyticsAutoUpload(this))
                {
                    NsAnalyticsUploadWorker.Companion.schedulePeriodicUpload(this,
                            NsAnalyticsSecureStorage.INSTANCE.getUploadFrequency(this), false);
                }

                nsAnalyticsRegistrationWatcher.start();

                updateWakeLock();
                updateServiceNotification();

                Timber.i("NS Analytics survey started for %s", collectedTypes);

                // Re-evaluate MQTT backpressure mode since NS Analytics is now active
                reevaluateMqttBackpressureMode();

                return new UploadScanningResult(true, true,
                        getString(R.string.ns_analytics_survey_started));
            } else if (!enable && isCurrentlyActive)
            {
                shutdownNsAnalyticsDataStore();

                // Cancel periodic uploads first
                NsAnalyticsUploadWorker.Companion.cancelPeriodicUpload(this);
                Timber.i("Canceled NS Analytics periodic uploads after survey stop");

                // Trigger immediate upload if there are pending records and auto-upload is enabled
                if (PreferenceUtils.isNsAnalyticsAutoUpload(this))
                {
                    // Run in background to avoid blocking
                    executorService.execute(() -> {
                        try
                        {
                            SurveyDatabase db = SurveyDatabase.getInstance(this);
                            int pendingCount = db.nsAnalyticsDao().getPendingRecordCount();
                            if (pendingCount > 0)
                            {
                                // Upload the rest now, or as soon as the phone is back online
                                NsAnalyticsUploadWorker.triggerImmediateUpload(this, true);
                                Timber.i("NS Analytics survey stopped with %d pending records - triggered immediate upload", pendingCount);
                            } else
                            {
                                Timber.i("NS Analytics survey stopped with no pending records");
                            }
                        } catch (Exception e)
                        {
                            Timber.e(e, "Failed to check pending records after stopping NS Analytics survey");
                        }
                    });
                }

                onSurveyStopped();
                updateWakeLock();
                updateServiceNotification();

                Timber.i("NS Analytics survey stopped");

                return new UploadScanningResult(true, false,
                        getString(R.string.ns_analytics_survey_stopped));
            } else
            {
                // Already in desired state
                return new UploadScanningResult(true, enable,
                        enable ? getString(R.string.ns_analytics_survey_already_running)
                                : getString(R.string.ns_analytics_survey_already_stopped));
            }
        } catch (Exception e)
        {
            Timber.e(e, "Failed to toggle NS Analytics scanning");
            return new UploadScanningResult(false, false,
                    getString(R.string.ns_analytics_survey_toggle_failed));
        }
    }

    /**
     * Unregisters the NS Analytics data store from every record stream and shuts it down.
     */
    private void shutdownNsAnalyticsDataStore()
    {
        final NsAnalyticsDataStore dataStore = nsAnalyticsDataStore;
        if (dataStore == null) return;

        if (nsAnalyticsRegistrationWatcher != null) nsAnalyticsRegistrationWatcher.stop();

        unregisterCellularSurveyRecordListener(dataStore);
        unregisterPhoneStateListener(dataStore);
        unregisterWifiSurveyRecordListener(dataStore);
        unregisterBluetoothSurveyRecordListener(dataStore);
        unregisterGnssSurveyRecordListener(dataStore);
        unregisterDeviceStatusListener(dataStore);
        synchronized (nsAnalyticsTypes)
        {
            nsAnalyticsTypes.clear();
        }

        dataStore.shutdown();
        nsAnalyticsDataStore = null;
    }

    /**
     * Called after this phone connected to an NS Analytics workspace, or after its registration was
     * cleared.
     * <p>
     * Connected: a running survey that asked for NS Analytics starts sending to it. Removed by the
     * workspace: a running survey stops sending records there, and a notification says why since
     * the screen may be closed. Either way it works whoever started the survey. A disconnect by the
     * user needs NS Analytics stopped first, so it only gets the same clean drop, without a notice.
     */
    private void onNsAnalyticsRegistrationChanged(NsAnalyticsUtils.RegistrationChange change)
    {
        if (destroyed || surveyCoordinator == null) return;

        if (change == NsAnalyticsUtils.RegistrationChange.CONNECTED)
        {
            surveyCoordinator.retrySkipped();
        } else if (nsAnalyticsDataStore != null)
        {
            final boolean removedByWorkspace = change == NsAnalyticsUtils.RegistrationChange.REMOVED_BY_WORKSPACE;
            surveyCoordinator.dropDestination(Destination.NS_ANALYTICS, () -> {
                if (!removedByWorkspace) return;
                final boolean surveyStopped = !surveyCoordinator.getOrchestrator().isRecording();
                NsAnalyticsNotificationHelper.INSTANCE.showRemovedFromWorkspaceNotification(this, surveyStopped);
            });
        }
    }

    /**
     * Retries what the running survey asked for and left out (a radio that was off, a permission,
     * a destination that needed setting up), whoever started it.
     */
    public Future<SurveyStartResult> retrySkippedSurveyItems()
    {
        return surveyCoordinator.retrySkipped();
    }

    /**
     * Follows the radios for the running survey, whoever started it. Bluetooth coming on retries
     * the survey, so Bluetooth left out because the radio was off starts recording on its own. Wi-Fi
     * stays in the survey with the radio off, so a Wi-Fi change only refreshes the notification's
     * "Wi-Fi is off" line.
     */
    private void registerRadioStateReceiver()
    {
        radioStateReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                if (destroyed || surveyCoordinator == null) return;
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction()))
                {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_ON) surveyCoordinator.retrySkipped();
                } else if (isBeingUsed())
                {
                    updateServiceNotification();
                }
            }
        };
        final IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        WifiEnvironment.Companion.getACTIONS().forEach(filter::addAction);
        ContextCompat.registerReceiver(this, radioStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterRadioStateReceiver()
    {
        if (radioStateReceiver == null) return;
        try
        {
            unregisterReceiver(radioStateReceiver);
        } catch (Exception e)
        {
            Timber.e(e, "Unable to unregister the radio state receiver");
        }
        radioStateReceiver = null;
    }

    /**
     * Changes the record types collected for NS Analytics without restarting it.
     *
     * @param types The record types to collect from now on (CDR is ignored).
     */
    public void setNsAnalyticsTypes(Set<RecordType> types)
    {
        final NsAnalyticsDataStore dataStore = nsAnalyticsDataStore;
        if (dataStore == null) return;
        synchronized (nsAnalyticsTypes)
        {
            applyListenerTypes(nsAnalyticsTypes, types, dataStore, dataStore, dataStore, dataStore, dataStore);
        }
    }

    /**
     * @return A copy of the record types collected for NS Analytics.
     */
    public Set<RecordType> getNsAnalyticsTypes()
    {
        synchronized (nsAnalyticsTypes)
        {
            return EnumSet.copyOf(nsAnalyticsTypes);
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
        return gnssController.isLoggingEnabled();
    }

    public boolean isCdrLoggingEnabled()
    {
        return cellularController.isCdrLoggingEnabled();
    }

    /**
     * @return true if MQTT streaming is currently active (connected or connecting)
     */
    public boolean isMqttStreamingActive()
    {
        if (mqttConnection == null) return false;
        final com.craxiom.mqttlibrary.connection.ConnectionState state = mqttConnection.getConnectionState();
        return state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING;
    }

    /**
     * @return true if gRPC streaming is currently active
     */
    public boolean isGrpcConnectionActive()
    {
        return GrpcConnectionService.getConnectedState() == ConnectionState.CONNECTED;
    }

    public boolean isUploadScanningActive()
    {
        return dbUploadStore != null;
    }

    public boolean isNsAnalyticsScanningActive()
    {
        return nsAnalyticsDataStore != null;
    }

    /**
     * @return true if the device is registered with NS Analytics
     */
    public boolean isNsAnalyticsRegistered()
    {
        return NsAnalyticsSecureStorage.INSTANCE.isRegistered(this);
    }

    /**
     * Triggers the creation of a single device status message and notifies the listeners.
     */
    public void sendSingleDeviceStatus()
    {
        surveyRecordProcessor.onDeviceStatus(generateDeviceStatus());
    }

    /**
     * Returns the information for the Active SIMs in the device, both physical and eSIMs. If the
     * returned list is empty, then the device does not have any active SIMs. This does not mean
     * that the device does not have a cellular radio, nor does it mean that survey results won't
     * be available. Because of emergency service requirements the phone should still report
     * limited survey results even if there is no active SIM.
     */
    public List<SubscriptionInfo> getActiveSubscriptionInfoList()
    {
        return cellularController.getActiveSubscriptionInfoList();
    }

    /**
     * Check if any type of survey is currently active (logging or streaming).
     * This includes file logging, MQTT streaming, gRPC streaming, and upload scanning.
     *
     * @return true if any survey is active
     */
    public boolean isAnySurveyActive()
    {
        return isAnyMissionRelevantSurveyActive() || isUploadScanningActive();
    }

    /**
     * Check if any survey that is relevant to the Mission ID is currently active.
     * <p>
     * This is the same set as {@link #isAnySurveyActive()} except it excludes OpenCelliD/BeaconDB
     * upload scanning ({@link #isUploadScanningActive()}). Those community upload databases ignore
     * the Mission ID, so starting or stopping them must not roll it.
     *
     * @return true if any mission relevant survey is active
     */
    private boolean isAnyMissionRelevantSurveyActive()
    {
        return isCellularLoggingEnabled() || isPhoneStateLoggingEnabled() || isWifiLoggingEnabled() ||
                isBluetoothLoggingEnabled() || isGnssLoggingEnabled() || isCdrLoggingEnabled() ||
                isMqttStreamingActive() || isGrpcActiveOrConnecting() || isNsAnalyticsScanningActive();
    }

    /**
     * Checks if any survey record outputs other than MQTT streaming are currently active.
     * This includes file logging (CSV), gRPC streaming, NS Analytics, and upload scanning.
     * <p>
     * This is used to determine whether to pause scanning (conserve battery when only MQTT is active)
     * or drop MQTT messages (continue other outputs when MQTT backs up).
     *
     * @return true if other outputs are active, false if only MQTT streaming is active
     */
    private boolean hasOtherActiveOutputs()
    {
        // Check if file logging is enabled for any protocol
        if (isCellularLoggingEnabled() || isPhoneStateLoggingEnabled() || isWifiLoggingEnabled() ||
                isBluetoothLoggingEnabled() || isGnssLoggingEnabled() || isCdrLoggingEnabled())
        {
            return true;
        }

        // Check if gRPC streaming is connected
        if (isGrpcConnectionActive())
        {
            return true;
        }

        // Check if NS Analytics is active
        if (isNsAnalyticsScanningActive())
        {
            return true;
        }

        // Check if upload scanning is active
        return isUploadScanningActive();
    }

    /**
     * Acquires the wake lock if any survey is active. This ensures the device
     * doesn't go to sleep while collecting survey data.
     */
    @SuppressLint("WakelockTimeout")
    private void updateWakeLock()
    {
        synchronized (wakeLockLock)
        {
            updateWakeLockLocked();
        }
    }

    private void updateWakeLockLocked()
    {
        if (wakeLock == null)
        {
            Timber.w("Wake lock is null, cannot update wake lock state");
            return;
        }

        boolean shouldHoldWakeLock = isAnySurveyActive();

        if (shouldHoldWakeLock && !wakeLockActive.getAndSet(true))
        {
            Timber.d("Acquiring wake lock for survey operations");
            try
            {
                wakeLock.acquire();
            } catch (Exception e)
            {
                Timber.e(e, "Failed to acquire wake lock");
                wakeLockActive.set(false);
            }
        } else if (!shouldHoldWakeLock && wakeLockActive.getAndSet(false))
        {
            Timber.d("Releasing wake lock - no surveys active");
            try
            {
                if (wakeLock.isHeld())
                {
                    wakeLock.release();
                }
            } catch (Exception e)
            {
                Timber.e(e, "Failed to release wake lock");
            }
        }
    }

    /**
     * Called when any survey starts. Starts the session if none is running and rolls the Mission ID
     * on the transition to the first mission relevant survey (see {@link MissionSessionTracker}).
     * <p>
     * Callers pass whether the survey they are starting counts toward the Mission ID, and call this
     * before registering any listener that can emit a record immediately, so that record cannot
     * capture a pre-roll Mission ID.
     *
     * @param missionRelevant true if the starting survey counts toward the Mission ID (every survey
     *                        except OpenCelliD/BeaconDB upload scanning).
     */
    private void onSurveyStarted(boolean missionRelevant)
    {
        missionSessionTracker.onSurveyStarted(missionRelevant);
    }

    /**
     * Called when any survey stops. Ends the session when nothing runs anymore.
     */
    private void onSurveyStopped()
    {
        missionSessionTracker.onSurveyStopped();
    }

    /**
     * Ends the survey session and the Mission ID session right away, even if a destination (gRPC) is
     * still finishing an asynchronous stop, so a Start right after a Stop rolls a fresh Mission ID.
     */
    public void endMissionSession()
    {
        missionSessionTracker.endSession();
        updateWakeLock();
    }

    /**
     * Called by the {@link GrpcConnectionService} when the gRPC streaming connection becomes
     * connected or disconnected. gRPC streaming is a mission relevant survey, but unlike MQTT the
     * connection is driven from a separate service, so it must signal the survey session here.
     * This mirrors how connectToMqttBroker / disconnectFromMqttBroker call onSurveyStarted /
     * onSurveyStopped. The gRPC service only reports DISCONNECTED for a real disconnect (not a
     * transient reconnect), so a network blip will not roll a new Mission ID.
     *
     * @param connected true if the gRPC connection just became CONNECTED, false if it disconnected.
     */
    public void onGrpcConnectionStateChanged(boolean connected)
    {
        if (connected)
        {
            onSurveyStarted(true);
        } else
        {
            synchronized (grpcStreamTypes)
            {
                grpcStreamTypes.clear();
            }
            onSurveyStopped();
            surveyCoordinator.onRunningStateChanged();
        }
        updateWakeLock();
    }

    /**
     * Generates a new Mission ID of the form "NS &lt;deviceId&gt; &lt;yyyyMMdd-HHmmss&gt;" using the
     * current time. Reuses {@link SurveyRecordProcessor#DATE_TIME_FORMATTER} so the trailing
     * timestamp format stays consistent with the rest of the app.
     *
     * @return A new Mission ID.
     */
    private String generateMissionId()
    {
        return NetworkSurveyConstants.MISSION_ID_PREFIX + deviceId + " "
                + SurveyRecordProcessor.DATE_TIME_FORMATTER.format(LocalDateTime.now());
    }

    /**
     * @return The Mission ID to stamp on survey records: the current rolled Mission ID, or an empty
     * string if no mission relevant survey has started yet. The empty string case is only reachable
     * when nothing but OpenCelliD/BeaconDB scanning is running, where the value is discarded before
     * reaching any consumer that uses it. Every mission relevant survey rolls the Mission ID before
     * registering any record listener, so retained records always carry the rolled value.
     */
    public String getMissionIdForRecords()
    {
        return missionSessionTracker.missionIdForRecords();
    }

    /**
     * @return The current rolled Mission ID for display, or null if no mission relevant survey has
     * started during this app session. After a survey stops, this retains the most recent value.
     */
    public String getRolledMissionId()
    {
        return missionSessionTracker.rolledMissionId();
    }

    /**
     * @return true while at least one mission relevant survey is running.
     */
    public boolean isMissionSessionActive()
    {
        return missionSessionTracker.isMissionSessionActive();
    }

    /**
     * Counts records the {@link SurveyRecordProcessor} just processed, for the session totals (the
     * stall watchdog) and the per record type counts (the Recording screen).
     *
     * @param type  The record type.
     * @param count The number of records of that type.
     */
    public void onRecordsProcessed(RecordType type, int count)
    {
        if (count <= 0) return;
        missionSessionTracker.getRecordCount().addAndGet(count);
        missionSessionTracker.getCounters().increment(type, count);
    }

    /**
     * Gives the instrumented destination tests a way to push synthetic records through the same
     * listener fan-out that real scans use, so they can assert what each destination receives.
     *
     * @return The record processor that owns the per-type listener lists.
     */
    @VisibleForTesting
    SurveyRecordProcessor getSurveyRecordProcessorForTest()
    {
        return surveyRecordProcessor;
    }

    /**
     * @return True if the survey wake lock is held.
     */
    @VisibleForTesting
    boolean isWakeLockHeldForTest()
    {
        return wakeLockActive.get();
    }

    /**
     * @return The per record type counts and last-record time for the current survey session.
     */
    public SurveySessionCounters getSessionCounters()
    {
        return missionSessionTracker.getCounters();
    }

    /**
     * @return The per record type counts of the records saved for the community databases in the
     * current survey session, and when the last one was saved.
     */
    public SurveySessionCounters getCommunityCounters()
    {
        return missionSessionTracker.getCommunityCounters();
    }

    // IUploadRecordCountListener implementation
    @Override
    public void onCellularUploadRecordsWritten(int recordCount)
    {
        missionSessionTracker.getCommunityCounters().increment(RecordType.CELLULAR, recordCount);
    }

    @Override
    public void onWifiUploadRecordsWritten(int recordCount)
    {
        missionSessionTracker.getCommunityCounters().increment(RecordType.WIFI, recordCount);
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
        gnssController.refreshScanRate();

        final int oldDeviceStatusScanRateMs = deviceStatusScanRateMs;
        deviceStatusScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_DEVICE_STATUS_SCAN_INTERVAL_SECONDS, applicationContext);

        // If device status reporting is active and the rate has changed, restart it with the new rate
        if (deviceStatusActive.get() && oldDeviceStatusScanRateMs != deviceStatusScanRateMs)
        {
            Timber.i("Device status scan rate changed from %d ms to %d ms, restarting to apply new rate",
                    oldDeviceStatusScanRateMs, deviceStatusScanRateMs);

            restartDeviceStatusReport();
        }

        surveyRecordProcessor.setGnssScanRateMs(gnssController.getScanRateMs());

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

        // Don't register location listeners if paused for battery
        if (isPausedForBattery())
        {
            Timber.d("Skipping location listener registration - paused for battery");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("ACCESS_FINE_LOCATION Permission not granted for the NetworkSurveyService");
            return;
        }

        final LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
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

            if (gnssController.isScanningActive() && gnssController.getScanRateMs() < smallestScanRate)
            {
                smallestScanRate = gnssController.getScanRateMs();
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

            long scanRateCeiling = GpsListener.LOCATION_AGE_THRESHOLD_MS - 35_000;
            if (smallestScanRate > scanRateCeiling) smallestScanRate = (int) scanRateCeiling;

            if (smallestScanRate < 5_000) smallestScanRate = 5_000;

            Timber.d("Setting the location update rate to %d", smallestScanRate);

            try
            {
                final String provider;

                locationProviderPreference = PreferenceUtils.getLocationProviderPreference(getApplicationContext());

                String locationProviderMapping = getLocationProviderFromPreference(locationProviderPreference);
                if (locationManager.isProviderEnabled(locationProviderMapping))
                {
                    provider = locationProviderMapping;
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER))
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
                // Register the extra GPS/NETWORK listeners before the primary FUSED listener.
                // On de-Googled OSs (e.g. GrapheneOS), FUSED_PROVIDER silently fails if GPS
                // hardware isn't already active. Warming up GPS first ensures FUSED can
                // piggyback on the active provider. This is a no-op on standard Android.
                updateOtherLocationListeners(locationProviderPreference, locationManager, smallestScanRate);

                locationManager.requestLocationUpdates(provider, smallestScanRate, 0f, primaryLocationListener, serviceLooper);
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
     * @return The location provider that is mapped to the location provider preference. This is a one to one mapping
     * but in this method we also check the Android SDK version and also map "All" to the FUSED provider.
     */
    private String getLocationProviderFromPreference(int locationProvider)
    {
        switch (locationProvider)
        {
            // Use the FUSED provider for "All" as well, since we will be registering them all anyway.
            case LOCATION_PROVIDER_FUSED, LOCATION_PROVIDER_ALL ->
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                {
                    return LocationManager.FUSED_PROVIDER;
                } else
                {
                    return LocationManager.GPS_PROVIDER;
                }
            }
            case LOCATION_PROVIDER_GNSS ->
            {
                return LocationManager.GPS_PROVIDER;
            }
            case LOCATION_PROVIDER_NETWORK ->
            {
                return LocationManager.NETWORK_PROVIDER;
            }
            default ->
            {
                return LocationManager.GPS_PROVIDER;
            }
        }
    }

    @SuppressLint("MissingPermission")
    // Only called from updateLocationListener, which checks the permission
    private void updateOtherLocationListeners(int locationProviderPreference, LocationManager
            locationManager, int scanRate)
    {
        if (locationProviderPreference == LOCATION_PROVIDER_ALL)
        {

            locationManager.requestLocationUpdates(gnssLocationListener.getProvider(), scanRate, 0f, gnssLocationListener, serviceLooper);
            locationManager.requestLocationUpdates(networkLocationListener.getProvider(), scanRate, 0f, networkLocationListener, serviceLooper);
        } else
        {
            locationManager.removeUpdates(gnssLocationListener);
            locationManager.removeUpdates(networkLocationListener);
        }
    }

    /**
     * Removes the location listener(s) from the Android {@link LocationManager}.
     */
    private void removeLocationListener()
    {
        final LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (primaryLocationListener != null)
        {
            if (locationManager != null) locationManager.removeUpdates(primaryLocationListener);
        }
        if (gnssLocationListener != null)
        {
            if (locationManager != null) locationManager.removeUpdates(gnssLocationListener);
        }
        if (networkLocationListener != null)
        {
            if (locationManager != null) locationManager.removeUpdates(networkLocationListener);
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
     * Starts the device status report if any of the logging types are enabled (other than CDR logging), otherwise it
     * does nothing.
     */
    private void startDeviceStatusReportIfLoggingEnabled()
    {
        if (cellularController.isLoggingEnabled()
                || wifiController.isLoggingEnabled()
                || bluetoothController.isLoggingEnabled()
                || gnssController.isLoggingEnabled())
        {
            // Also runs each time a screen registers a listener while a survey logs; turning the
            // logger on again would start a new file, so only turn it on when it is off
            if (surveyRecordProcessor != null && !deviceStatusCsvLogger.isLoggingEnabled())
            {
                deviceStatusCsvLogger.enableLogging(true);
                surveyRecordProcessor.registerDeviceStatusListener(deviceStatusCsvLogger);
            }
            startDeviceStatusReport(true);
        }
    }

    /**
     * Initialize and start the handler that generates a periodic Device Status Message.
     * <p>
     * This method only starts scanning if the scan is not already active.
     *
     * @param fullStart If true, also updates wake lock.
     *                  If false, only starts the device status task itself.
     */
    private void startDeviceStatusReport(boolean fullStart)
    {
        if (deviceStatusActive.getAndSet(true)) return;

        if (fullStart)
        {
            updateWakeLock();
        }

        // Cancel any existing device status task
        if (deviceStatusFuture != null)
        {
            deviceStatusFuture.cancel(true);
            deviceStatusFuture = null;
        }

        // Ensure scan rate is valid before scheduling
        if (deviceStatusScanRateMs <= 0)
        {
            Timber.w("Invalid device status scan rate: %d ms. Using default value.", deviceStatusScanRateMs);
            deviceStatusScanRateMs = NetworkSurveyConstants.DEFAULT_DEVICE_STATUS_SCAN_INTERVAL_SECONDS * 1000;
        }

        // Schedule the device status task at a fixed rate
        try
        {
            deviceStatusFuture = deviceStatusExecutor.scheduleWithFixedDelay(() -> {
                try
                {
                    if (!deviceStatusActive.get())
                    {
                        Timber.i("Device status reporting is no longer active, skipping status generation");
                        return;
                    }

                    // Skip device status generation if paused for battery management
                    if (isPausedForBattery())
                    {
                        Timber.d("Device status generation is paused for battery management");
                        return;
                    }

                    surveyRecordProcessor.onDeviceStatus(generateDeviceStatus());
                } catch (SecurityException e)
                {
                    Timber.e(e, "Could not get the required permissions to generate a device status message");
                } catch (Exception e)
                {
                    Timber.e(e, "Unexpected error generating device status message");
                }
            }, 1000L, deviceStatusScanRateMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ree)
        {
            Timber.w(ree, "Could not schedule device status reporting task - executor may be shut down");
        }

        Timber.d("Started device status reporting with interval %d ms", deviceStatusScanRateMs);
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
                .setDeviceTime(NsUtils.getRfc3339String(ZonedDateTime.now()));
        dataBuilder.setMdmOverride(BoolValue.newBuilder().setValue(mdmOverride).build());

        // Read the clock once so that the primary, GNSS, and network ages in this message are all
        // measured against the same instant and stay comparable to each other.
        final long elapsedTimeMillis = SystemClock.elapsedRealtime();

        if (primaryLocationListener != null)
        {
            final Location lastKnownLocation = primaryLocationListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));
                dataBuilder.setLocationAge(SurveyRecordProcessor.getLocationAgeMs(lastKnownLocation, elapsedTimeMillis));

                // Only ever set when the location really was mocked. Mocking is the exceptional
                // case, so stamping every ordinary record with an explicit false would spend wire
                // bytes on every message, and a row of the CSV, to say nothing.
                if (LocationUtils.isMock(lastKnownLocation))
                {
                    dataBuilder.setMockLocation(BoolValue.of(true));
                }
                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        if (locationProviderPreference == LOCATION_PROVIDER_ALL)
        {
            // Add the extra locations to the message. The listeners hold the last fix from each
            // provider indefinitely, so the age is what tells a consumer whether the provider is
            // still producing fixes or has gone quiet while the app keeps reporting the old one.
            if (gnssLocationListener != null)
            {
                Location gnssLocation = gnssLocationListener.getLatestLocation();
                if (gnssLocation != null)
                {
                    dataBuilder.setGnssLatitude(gnssLocation.getLatitude());
                    dataBuilder.setGnssLongitude(gnssLocation.getLongitude());
                    dataBuilder.setGnssAltitude((float) gnssLocation.getAltitude());
                    dataBuilder.setGnssAccuracy(MathUtils.roundAccuracy(gnssLocation.getAccuracy()));
                    dataBuilder.setGnssAge(SurveyRecordProcessor.getLocationAgeMs(gnssLocation, elapsedTimeMillis));
                }
            }

            if (networkLocationListener != null)
            {
                Location networkLocation = networkLocationListener.getLatestLocation();
                if (networkLocation != null)
                {
                    dataBuilder.setNetworkLatitude(networkLocation.getLatitude());
                    dataBuilder.setNetworkLongitude(networkLocation.getLongitude());
                    dataBuilder.setNetworkAltitude((float) networkLocation.getAltitude());
                    dataBuilder.setNetworkAccuracy(MathUtils.roundAccuracy(networkLocation.getAccuracy()));
                    dataBuilder.setNetworkAge(SurveyRecordProcessor.getLocationAgeMs(networkLocation, elapsedTimeMillis));
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
        dataBuilder.setAppVersion(NsUtils.getAppVersionName(this));

        final DeviceStatus.Builder statusBuilder = DeviceStatus.newBuilder();
        statusBuilder.setMessageType(DeviceStatusMessageConstants.DEVICE_STATUS_MESSAGE_TYPE);
        statusBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        statusBuilder.setData(dataBuilder);

        return statusBuilder.build();
    }

    /**
     * Stops generating device status reports if no more loggers are enabled.
     */
    private void stopDeviceStatusReportIfNotNeeded()
    {
        if (!deviceStatusActive.get()) return;

        // We only want to stop the device status report if all of the logging types are inactive.
        // Not checking for CDR logging since that is a different beast (it logs at a much lower rate)
        if (!cellularController.isLoggingEnabled() && !wifiController.isLoggingEnabled() && !bluetoothController.isLoggingEnabled() && !gnssController.isLoggingEnabled())
        {
            surveyRecordProcessor.unregisterDeviceStatusListener(deviceStatusCsvLogger);
            deviceStatusCsvLogger.enableLogging(false);

            // Need to check the survey record processor because MQTT could be using the device status message
            if (!surveyRecordProcessor.isDeviceStatusBeingUsed()) stopDeviceStatusReport(true);
        }
    }

    /**
     * Restarts the device status report with a new scan rate.
     * This method stops and restarts the device status task without affecting
     * the phone state listener, wake lock, or location listener.
     */
    private void restartDeviceStatusReport()
    {
        stopDeviceStatusReport(false);
        startDeviceStatusReport(false);
    }

    /**
     * Stop generating device status messages.
     *
     * @param fullStop If true, also updates wake lock/location listener.
     *                 If false, only stops the device status task itself.
     */
    private void stopDeviceStatusReport(boolean fullStop)
    {
        deviceStatusActive.set(false);

        // Cancel the scheduled device status task
        if (deviceStatusFuture != null)
        {
            deviceStatusFuture.cancel(true);
            deviceStatusFuture = null;
            Timber.d("Cancelled device status reporting task");
        }

        if (fullStop)
        {
            updateLocationListener();
            updateWakeLock();
        }
    }

    /**
     * Posts or refreshes the single ongoing notification for this service.
     * <p>
     * The first call starts the service in the foreground so that location updates continue while
     * the phone is locked or the app is in the background. Every later call only refreshes the
     * notification content through the NotificationManager, which avoids the permission re-checks
     * that repeated startForeground calls trigger on newer Android versions. The content is built by
     * {@link SurveyNotificationBuilder} from a {@link SurveyNotificationState} snapshot so that
     * everything running (file logging, MQTT, gRPC, the community survey, NS Analytics) is listed.
     */
    public void updateServiceNotification()
    {
        if (destroyed) return;

        publishSurveyRuntimeState();

        try
        {
            final Notification notification = surveyNotificationBuilder.build(buildNotificationState());

            if (!foregroundStarted.get())
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                {
                    startForeground(NetworkSurveyConstants.LOGGING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                } else
                {
                    startForeground(NetworkSurveyConstants.LOGGING_NOTIFICATION_ID, notification);
                }
                foregroundStarted.set(true);
            } else
            {
                final NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null)
                {
                    notificationManager.notify(NetworkSurveyConstants.LOGGING_NOTIFICATION_ID, notification);
                }
            }
        } catch (Exception e)
        {
            Timber.e(e, "Could not update the foreground service notification for Network Survey");
        }
    }

    /**
     * Publishes the survey state as it is actually running, for the Survey screen. Called from
     * {@link #updateServiceNotification()} so the screen and the notification always agree.
     */
    private void publishSurveyRuntimeState()
    {
        if (surveyCoordinator == null || mqttConnection == null || destroyed) return;

        // Computed and published under one lock, so a slower thread can never overwrite a newer
        // snapshot with an older one.
        synchronized (runtimeStatePublishLock)
        {
            publishSurveyRuntimeStateLocked();
        }
    }

    private void publishSurveyRuntimeStateLocked()
    {
        try
        {
            final ServiceSurveyOutputs outputs = new ServiceSurveyOutputs(this);
            final SurveyOrchestrator orchestrator = surveyCoordinator.getOrchestrator();
            final SurveySetup requested = orchestrator.getRequestedSetup();
            final Map<Destination, DestinationRuntime> destinations =
                    destinationHealthReader.read(outputs, requested, orchestrator.getFileLoggingFailures());

            scheduleConnectingEscalation(destinations);
            surveyStateHolder.publish(new SurveyRuntimeState(
                    destinations,
                    missionSessionTracker.rolledMissionId(),
                    missionSessionTracker.isMissionSessionActive(),
                    missionSessionTracker.sessionStartTimeMs(),
                    isPausedForBattery(),
                    isPausedForQueueBackpressure(),
                    isMqttDroppingMessages(),
                    surveyStallWatchdog != null ? surveyStallWatchdog.getStalledMinutes() : null,
                    surveyCoordinator.policy(),
                    destinations.isEmpty() ? null : surveyCoordinator.getStartOrigin(),
                    surveyCoordinator.getLastResult(),
                    getCurrentBatteryLevel(),
                    PreferenceUtils.getBatteryThresholdPercent(this),
                    PreferenceUtils.getStreamingQueueLimit(this),
                    mdmOverrideController != null && mdmOverrideController.getOrganizationUpdatedWhileOverridden(),
                    missionSessionTracker.lastSession(),
                    destinations.isEmpty() ? null : requested,
                    destinationHealthReader.endedBy(requested, destinations)));
        } catch (Exception e)
        {
            Timber.e(e, "Could not publish the survey state");
        }
    }

    /**
     * Refreshes the notification when a first connection attempt turns from Connecting into
     * Can't connect, since nothing else changes at that moment. The Survey screen has its own tick.
     */
    private void scheduleConnectingEscalation(Map<Destination, DestinationRuntime> destinations)
    {
        if (serviceHandler == null) return;
        serviceHandler.removeCallbacks(connectingEscalation);
        final long now = System.currentTimeMillis();
        final Long escalationMs = DestinationStatusRules.INSTANCE.nextEscalationMs(destinations.values(), now);
        if (escalationMs != null)
        {
            serviceHandler.postDelayed(connectingEscalation, escalationMs - now + ESCALATION_REFRESH_MARGIN_MS);
        }
    }

    /**
     * @return The survey state as it is actually running. The Survey screen collects this.
     */
    public StateFlow<SurveyRuntimeState> getSurveyStateFlow()
    {
        return surveyStateHolder.getState();
    }

    /**
     * Starts a survey. With a null setup, the user's working setup (with the organization's locks
     * applied) is started. If a survey is already running it is changed to match instead.
     *
     * @return The pending per-destination result.
     */
    public Future<SurveyStartResult> startSurvey(SurveySetup setup, SurveyOrigin origin)
    {
        return surveyCoordinator.startSurvey(setup, origin);
    }

    /**
     * Changes the running survey to the given setup without rolling the Mission ID. Pass null to
     * apply the user's working setup with the organization's locks applied.
     */
    public Future<SurveyStartResult> applySurveySetup(SurveySetup setup, SurveyOrigin origin)
    {
        return surveyCoordinator.applySetup(setup, origin);
    }

    /**
     * Stops every destination of the running survey.
     */
    public Future<SurveyStartResult> stopSurvey(SurveyOrigin origin)
    {
        return surveyCoordinator.stopSurvey(origin);
    }

    /**
     * Starts the survey if it is set to start automatically (organization policy, otherwise the
     * user's setting) and nothing is running yet.
     *
     * @param origin {@link SurveyOrigin#BOOT} or {@link SurveyOrigin#APP_OPEN}.
     */
    public Future<SurveyStartResult> startAutoSurveyIfEnabled(SurveyOrigin origin)
    {
        return surveyCoordinator.startAutoSurveyIfEnabled(origin);
    }

    /**
     * @return The record types a destination turned on outside a survey start receives: those of the
     * running survey, or of the user's survey setup when nothing is running. A running survey is
     * never widened to the user's setup, even when it records nothing this destination takes (a
     * device status only MQTT stream, or CDR files only).
     */
    public Set<RecordType> getStreamTypesForNewDestination(Destination destination)
    {
        if (surveyCoordinator != null && surveyCoordinator.getOrchestrator().isRecording())
        {
            final Set<RecordType> running = surveyCoordinator.getOrchestrator().runningSetup().getRecordTypes();
            return DestinationCapabilities.typesFor(destination, running, SurveyTypeResolver.communityTargets(this));
        }
        return SurveyTypeResolver.typesForCurrentSetup(this, destination);
    }

    /**
     * Starts the gRPC stream for a survey, with the saved gRPC server settings.
     *
     * @return True if a connection was started, false if no gRPC server is set up.
     */
    public boolean connectToGrpcServerForSurvey(Set<RecordType> types)
    {
        final GrpcConnectionSettings settings = GrpcConnectionSettings.fromPreferences(
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        if (settings == null) return false;

        synchronized (grpcStreamTypes)
        {
            grpcStreamTypes.clear();
            grpcStreamTypes.addAll(types);
        }
        GrpcConnectionService.connectToGrpcServer(getApplicationContext(), settings, types);

        // Start the session (and roll the Mission ID) now, as MQTT does: the stream listens and
        // holds records while it connects, so those records must carry this survey's Mission ID.
        // The connect call above already reports CONNECTING, so the session sees gRPC as active.
        onSurveyStarted(true);
        updateWakeLock();
        return true;
    }

    /**
     * Moves a running gRPC stream to the saved server (or applies a new device name), keeping the
     * survey and its Mission ID. Does nothing when gRPC is not running. Runs on the survey thread,
     * after any Stop that was requested before it.
     */
    private void switchGrpcServerIfRunning()
    {
        if (!isGrpcActiveOrConnecting()) return;
        final GrpcConnectionSettings settings = GrpcConnectionSettings.fromPreferences(
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        if (settings != null) GrpcConnectionService.switchServer(getApplicationContext(), settings);
    }

    /**
     * Changes the record types streamed over the gRPC connection without reconnecting.
     */
    public void setGrpcStreamTypes(Set<RecordType> types)
    {
        synchronized (grpcStreamTypes)
        {
            grpcStreamTypes.clear();
            grpcStreamTypes.addAll(types);
        }
        GrpcConnectionService.updateStreamTypes(getApplicationContext(), types);
    }

    /**
     * @return A copy of the record types the gRPC connection streams.
     */
    public Set<RecordType> getGrpcStreamTypes()
    {
        synchronized (grpcStreamTypes)
        {
            return EnumSet.copyOf(grpcStreamTypes);
        }
    }

    /**
     * Disconnects from the gRPC server. The disconnect is asynchronous; the session bookkeeping
     * happens when the gRPC service reports DISCONNECTED.
     */
    public void disconnectFromGrpcServer()
    {
        GrpcConnectionService.disconnectFromGrpcServer(getApplicationContext());
    }

    /**
     * @return True while the gRPC connection is up or being established.
     */
    public boolean isGrpcActiveOrConnecting()
    {
        final ConnectionState state = GrpcConnectionService.getConnectedState();
        return state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING;
    }

    /**
     * @return True if a gRPC server host is saved.
     */
    public boolean hasGrpcServerConfigured()
    {
        return GrpcConnectionSettings.fromPreferences(
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())) != null;
    }

    /**
     * @return True if the Bluetooth permissions are granted. Never prompts.
     */
    public boolean hasBluetoothPermissions()
    {
        return bluetoothController.hasBluetoothPermissions();
    }

    /**
     * @return True if Bluetooth can be scanned right now. Never prompts.
     */
    public boolean isBluetoothReady()
    {
        return bluetoothController.isBluetoothReady();
    }

    /**
     * Snapshots everything the survey notification reflects. Kept as one method so the notification
     * always sees a consistent view of the service state.
     */
    private SurveyNotificationState buildNotificationState()
    {
        final boolean fileLogging = isCellularLoggingEnabled() || isPhoneStateLoggingEnabled() || isWifiLoggingEnabled()
                || isBluetoothLoggingEnabled() || isGnssLoggingEnabled() || isCdrLoggingEnabled();
        final ConnectionState mqttState = mqttConnection != null ? mqttConnection.getConnectionState() : ConnectionState.DISCONNECTED;

        return new SurveyNotificationState(
                fileLogging,
                isUploadScanningActive(),
                isNsAnalyticsScanningActive(),
                mqttState,
                isMqttDroppingMessages(),
                GrpcConnectionService.getConnectedState(),
                cellularController.isScanningActive(),
                wifiController.isScanningActive(),
                bluetoothController.isScanningActive(),
                gnssController.isScanningActive(),
                isPhoneStateLoggingEnabled(),
                isCdrLoggingEnabled(),
                isPausedForBattery(),
                getCurrentBatteryLevel(),
                isPausedForQueueBackpressure(),
                surveyStallWatchdog != null ? surveyStallWatchdog.getStalledMinutes() : null,
                missionSessionTracker.sessionStartTimeMs(),
                surveyCoordinator != null && surveyCoordinator.policy().getOverrideOn(),
                DestinationStatusRules.INSTANCE.serviceStatuses(
                        surveyStateHolder.getState().getValue().getDestinations(),
                        isPausedForBattery() || isPausedForQueueBackpressure(),
                        PreferenceUtils.isAutoUploadEnabled(this),
                        System.currentTimeMillis()),
                LeftOutItems.INSTANCE.of(surveyStateHolder.getState().getValue(),
                        new AndroidWifiEnvironment(this).scanState() == WifiScanState.WIFI_OFF));
    }

    /**
     * @return True if a survey is running with no record types (a device status only MQTT stream), so
     * no records are expected and a record count that never moves is not a stall.
     */
    private boolean isRecordingNoRecordTypes()
    {
        if (surveyCoordinator == null) return false;
        final SurveyOrchestrator orchestrator = surveyCoordinator.getOrchestrator();
        return orchestrator.isRecording() && orchestrator.runningSetup().getRecordTypes().isEmpty();
    }

    /**
     * Creates the watchdog that flips the notification title to "No records for N min" when a survey
     * is running but nothing is being recorded, and that re-posts the notification if the user swiped
     * it away (which Android 14 and later allow for foreground services).
     */
    private SurveyStallWatchdog createStallWatchdog()
    {
        return new SurveyStallWatchdog(serviceHandler,
                () -> missionSessionTracker.getRecordCount().get(),
                this::isAnySurveyActive,
                () -> isPausedForBattery() || isPausedForQueueBackpressure() || isRecordingNoRecordTypes(),
                this::getStallThresholdMs,
                () -> {
                    updateServiceNotification();
                    return Unit.INSTANCE;
                },
                () -> {
                    repostNotificationIfMissing();
                    return Unit.INSTANCE;
                });
    }

    /**
     * The quiet period that counts as a stall: three times the longest scan interval currently in
     * use, with a five minute floor so the default intervals never trip it on a short gap.
     */
    private long getStallThresholdMs()
    {
        final List<Integer> intervals = new ArrayList<>();
        if (cellularController.isScanningActive()) intervals.add(cellularController.getScanRateMs());
        if (wifiController.isScanningActive()) intervals.add(wifiController.getScanRateMs());
        if (bluetoothController.isScanningActive()) intervals.add(bluetoothController.getScanRateMs());
        if (gnssController.isScanningActive()) intervals.add(gnssController.getScanRateMs());
        return SurveyStallWatchdog.Companion.thresholdFor(intervals);
    }

    /**
     * Re-posts the survey notification if it is no longer showing, which happens when the user swipes
     * it away on Android 14 and later while the service keeps running.
     */
    private void repostNotificationIfMissing()
    {
        if (!foregroundStarted.get()) return;
        final NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) return;

        for (StatusBarNotification active : notificationManager.getActiveNotifications())
        {
            if (active.getId() == NetworkSurveyConstants.LOGGING_NOTIFICATION_ID) return;
        }

        Timber.i("The survey notification is no longer showing, re-posting it");
        updateServiceNotification();
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
        gnssController.stopAllLogging();
        if (deviceStatusCsvLogger != null) deviceStatusCsvLogger.enableLogging(false);
    }

    /**
     * Close out the notification since we no longer need this service.
     */
    private void shutdownNotifications()
    {
        stopForeground(true);
        foregroundStarted.set(false);
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
                // Applies a changed survey setup to the running survey and moves a running MQTT
                // stream to a changed broker. A configuration push never starts an idle survey.
                mdmOverrideController.onRestrictionsChanged();
                // A push while overridden only raises the Review flag, which nothing else republishes.
                updateServiceNotification();

                cellularController.onMdmPreferenceChanged();
                wifiController.onMdmPreferenceChanged();
                bluetoothController.onMdmPreferenceChanged();
                gnssController.onMdmPreferenceChanged();

                deviceStatusCsvLogger.onMdmPreferenceChanged();
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

    private MqttConnectionInfo getMdmBrokerConnectionInfo()
    {
        return OrganizationBroker.from(new AndroidRestrictionsSource(this).snapshot());
    }

    /**
     * Get the connection information of the broker profile the user selected, with this phone's
     * client ID and device status setting.
     *
     * @return The connection settings to use for the MQTT broker, or null if no profile is selected.
     * @since 0.1.3
     */
    private BrokerConnectionInfo getUserBrokerConnectionInfo()
    {
        // The record types streamed come from the survey, not the connection info.
        return brokerProfileStore.activeConnectionInfo();
    }

    /**
     * Called when one or more of the logging states have changed so the MQTT backpressure mode can be
     * re-evaluated.
     *
     * @since 1.10.0
     */
    public void notifyLoggingChangedListeners()
    {
        // Re-evaluate backpressure behavior when logging state changes
        reevaluateMqttBackpressureMode();
    }

    /**
     * Re-evaluates the MQTT backpressure mode when other output states change.
     * If we were paused and user enables file logging, switch to drop mode and resume.
     * If we were dropping and no other outputs are active anymore, we stay in drop mode
     * (don't auto-pause - let the queue drain naturally).
     * <p>
     * This method should be called when any output state changes, such as when:
     * <ul>
     *   <li>File logging is enabled/disabled</li>
     *   <li>gRPC streaming connects/disconnects</li>
     *   <li>NS Analytics is enabled/disabled</li>
     * </ul>
     */
    public void reevaluateMqttBackpressureMode()
    {
        // Only relevant if MQTT queue is currently backed up
        if (!mqttConnection.isQueueBackpressureActive())
        {
            return;
        }

        boolean hasOtherOutputs = hasOtherActiveOutputs();

        if (hasOtherOutputs && isPausedDueToQueueBackpressure.get())
        {
            // User enabled logging/streaming while MQTT was paused - switch to drop mode
            Timber.i("Output enabled while MQTT paused - switching to drop mode and resuming scanning");

            isPausedDueToQueueBackpressure.set(false);
            mqttConnection.setDropMessages(true);

            // Only resume if not paused due to battery
            if (!isPausedForBattery())
            {
                cellularController.resumeScanning();
                wifiController.resumeScanning();
                bluetoothController.resumeScanning();
                gnssController.resumeScanning();
            }

            updateServiceNotification();
        }
        // Note: We don't auto-pause if other outputs are disabled while dropping.
        // Let the queue drain naturally and MQTT will resume normally.
    }

    /**
     * Clears both MQTT backpressure states (drop mode and pause mode) and resumes scanning if appropriate.
     * This method should be called when the MQTT connection is disconnected or otherwise no longer active.
     */
    private void clearMqttBackpressureStates()
    {
        if (mqttConnection.getAndSetDropMessages(false))
        {
            Timber.i("Clearing MQTT drop mode state");
        }

        if (isPausedDueToQueueBackpressure.getAndSet(false))
        {
            Timber.i("Clearing queue backpressure state");

            if (!isPausedForBattery())
            {
                cellularController.resumeScanning();
                wifiController.resumeScanning();
                bluetoothController.resumeScanning();
                gnssController.resumeScanning();
            }
        }
    }

    @Override
    public void onConnectionStateChange(ConnectionState connectionState)
    {
        destinationHealthReader.onMqttStateChanged(connectionState);

        if (connectionState == ConnectionState.DISCONNECTED)
        {
            clearMqttBackpressureStates();

            // A connection the MQTT client gave up on (bad credentials, invalid settings) must drop
            // its listeners, so the scanners and the wake lock it held can stop, and must end the
            // survey session if nothing else is running, or the next start would reuse the old
            // Mission ID. The library reports transient losses as CONNECTING, not DISCONNECTED. The
            // check runs on the survey control thread so a broker switch (disconnect then connect,
            // also on that thread) has finished before it looks, and so a synchronous give-up inside
            // connectToMqttBroker is handled after that method registered its listeners.
            if (surveyCoordinator != null && !destroyed)
            {
                surveyCoordinator.submit(() -> {
                    if (!isMqttStreamingActive())
                    {
                        releaseMqttListeners();
                        updateWakeLock();
                        onSurveyStopped();
                        surveyCoordinator.onRunningStateChanged();
                    }
                    return Unit.INSTANCE;
                });
            }
        } else if (connectionState == ConnectionState.CONNECTED && watchlistChangePublisher != null)
        {
            // Publish the full authoritative watchlist snapshot on every (re)connect, including the MQTT
            // client's silent auto-reconnects, so a backend that missed offline deltas is reconciled.
            watchlistChangePublisher.publishSnapshot();
        }

        // Refresh after the backpressure states are cleared so a disconnect never leaves a stale
        // drop mode line in the notification.
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
        gnssController.registerGnssFailureListener(gnssFailureListener);
    }

    /**
     * Clears the GNSS failure listener.
     *
     * @since 0.4.0
     */
    public void clearGnssFailureListener()
    {
        gnssController.clearGnssFailureListener();
    }

    /**
     * Class used for the client Binder.  Because we know this service always runs in the same process as its clients,
     * we don't need to deal with IPC.
     */
    public static class SurveyServiceBinder extends Binder
    {
        private NetworkSurveyService service;

        public SurveyServiceBinder(NetworkSurveyService service)
        {
            this.service = service;
        }

        /**
         * @return The service, or null once it has been destroyed.
         */
        public NetworkSurveyService getService()
        {
            return service;
        }

        public void onDestroy()
        {
            service = null;
        }
    }

    // ========================================
    // Streaming Queue Management Methods
    // ========================================

    /**
     * Handles changes to the streaming queue limit preference.
     */
    private void handleStreamingQueueLimitChange()
    {
        final int queueLimit = PreferenceUtils.getStreamingQueueLimit(this);
        Timber.d("Streaming queue limit changed to: %d", queueLimit);

        if (mqttConnection != null)
        {
            mqttConnection.setStreamingQueueLimit(queueLimit);
        }

        // Check if we should resume scanning due to the limit change
        if (isPausedDueToQueueBackpressure.get())
        {
            boolean shouldResume = false;

            if (queueLimit == NetworkSurveyConstants.STREAMING_QUEUE_LIMIT_DISABLED)
            {
                // Limit was disabled (set to 0), so resume
                Timber.i("Streaming queue limit disabled, resuming scanning");
                shouldResume = true;
            } else if (mqttConnection != null)
            {
                // Check if the new limit is greater than the current queue size
                int currentQueueSize = mqttConnection.getPendingMessageCount();
                if (queueLimit > currentQueueSize)
                {
                    Timber.i("New streaming queue limit (%d) > current queue size (%d), resuming scanning",
                            queueLimit, currentQueueSize);
                    shouldResume = true;
                }
            }

            if (shouldResume)
            {
                isPausedDueToQueueBackpressure.set(false);

                // Only resume if not paused due to battery
                if (!isPausedForBattery())
                {
                    cellularController.resumeScanning();
                    wifiController.resumeScanning();
                    bluetoothController.resumeScanning();
                    gnssController.resumeScanning();
                }
            }
        }
    }

    // ========================================
    // Battery Management Methods
    // ========================================

    /**
     * Handles changes to battery management preferences.
     */
    private void handleBatteryPreferenceChange()
    {
        final boolean batteryManagementEnabled = PreferenceUtils.isBatteryManagementEnabled(this);

        if (batteryManagementEnabled)
        {
            // Register battery monitor if not already registered
            if (batteryMonitor != null)
            {
                batteryMonitor.unregister(this); // Unregister first to avoid duplicate registration
                batteryMonitor.register(this);
                batteryMonitor.reevaluateThreshold(); // Force re-check with new threshold
            }
        } else
        {
            // Unregister battery monitor and resume if paused
            if (batteryMonitor != null)
            {
                batteryMonitor.unregister(this);

                // If we were paused, resume operations
                if (batteryPauseState != null && batteryPauseState.hasActiveOperations())
                {
                    Timber.i("Battery management disabled, resuming operations");
                    resumePreviousOperations();
                }
            }
        }
    }

    @Override
    public void onBatteryLevelChanged(int newLevel)
    {
        // The Survey screen warns as the battery nears the pause threshold, so it needs each change
        if (PreferenceUtils.isBatteryManagementEnabled(this) && isBeingUsed()) publishSurveyRuntimeState();
    }

    @Override
    public void onBatteryLevelBelowThreshold(int currentLevel, int threshold)
    {
        Timber.i("Battery level %d%% is below threshold %d%%, pausing operations", currentLevel, threshold);
        pauseAllOperations();
        updateServiceNotification();
    }

    @Override
    public void onBatteryLevelAboveThreshold(int currentLevel, int threshold)
    {
        Timber.i("Battery level %d%% is above threshold %d%%, resuming operations", currentLevel, threshold);
        resumePreviousOperations();
        updateServiceNotification();
    }

    /**
     * Pauses all active survey operations and saves their state.
     */
    private void pauseAllOperations()
    {
        // Create new pause state to track what was active
        batteryPauseState = new BatteryPauseState();

        // Save current scanning states
        batteryPauseState.setWasCellularScanningActive(cellularController.isScanningActive());
        batteryPauseState.setWasWifiScanningActive(wifiController.isScanningActive());
        batteryPauseState.setWasBluetoothScanningActive(bluetoothController.isScanningActive());
        batteryPauseState.setWasGnssScanningActive(gnssController.isScanningActive());
        batteryPauseState.setWasCdrScanningActive(cellularController.isCdrLoggingEnabled());

        // Save current logging states
        batteryPauseState.setWasCellularLoggingEnabled(cellularController.isLoggingEnabled());
        batteryPauseState.setWasWifiLoggingEnabled(wifiController.isLoggingEnabled());
        batteryPauseState.setWasBluetoothLoggingEnabled(bluetoothController.isLoggingEnabled());
        batteryPauseState.setWasGnssLoggingEnabled(gnssController.isLoggingEnabled());
        batteryPauseState.setWasCdrLoggingEnabled(cellularController.isCdrLoggingEnabled());

        // Save connection states
        batteryPauseState.setWasMqttConnectionActive(getMqttConnectionState() == ConnectionState.CONNECTED);
        batteryPauseState.setWasGrpcConnectionActive(isGrpcConnectionActive());

        // Save device status state
        batteryPauseState.setWasDeviceStatusActive(deviceStatusActive.get());

        // Persist the state
        saveBatteryPauseState();

        // Pause all controllers
        cellularController.pauseScanning();
        wifiController.pauseScanning();
        bluetoothController.pauseScanning();
        gnssController.pauseScanning();

        // Remove location listeners to save battery
        removeLocationListener();

        // Note: We don't disconnect MQTT/gRPC or stop logging completely
        // The controllers will skip their work when isPaused() returns true

        // Future optimization: Make connections pause-aware to reduce battery usage
        // Currently, connections stay active but receive no data when controllers are paused
        // This is acceptable as it avoids reconnection overhead, but gRPC queue polling
        // could be optimized to reduce battery drain during pause
    }

    /**
     * Resumes operations that were active before the battery pause.
     */
    private void resumePreviousOperations()
    {
        if (batteryPauseState == null)
        {
            Timber.w("No battery pause state found, nothing to resume");
            return;
        }

        // Resume all controllers (they will check their internal state)
        cellularController.resumeScanning();
        wifiController.resumeScanning();
        bluetoothController.resumeScanning();
        gnssController.resumeScanning();

        // Restore location listeners
        updateLocationListener();

        // Clear the saved state
        batteryPauseState = null;
        clearBatteryPauseState();
    }

    /**
     * Saves the battery pause state to preferences.
     */
    private void saveBatteryPauseState()
    {
        if (batteryPauseState == null) return;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor editor = prefs.edit();

        final String json = batteryPauseState.toJson();
        if (json != null)
        {
            editor.putString(PROPERTY_BATTERY_PAUSE_STATE, json);
            editor.apply();
        }
    }

    /**
     * Loads the battery pause state from preferences.
     */
    private BatteryPauseState loadBatteryPauseState()
    {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String json = prefs.getString(PROPERTY_BATTERY_PAUSE_STATE, null);

        if (json != null)
        {
            return BatteryPauseState.fromJson(json);
        }

        return null;
    }

    /**
     * Clears the saved battery pause state.
     */
    private void clearBatteryPauseState()
    {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.remove(PROPERTY_BATTERY_PAUSE_STATE);
        editor.apply();
    }

    /**
     * Checks if operations are currently paused due to low battery.
     *
     * @return true if paused due to battery, false otherwise
     */
    public boolean isPausedForBattery()
    {
        return batteryMonitor != null && batteryMonitor.isPausedDueToBattery();
    }

    /**
     * Gets the current battery level percentage.
     *
     * @return The current battery level (0-100), or -1 if unknown
     */
    public int getCurrentBatteryLevel()
    {
        return batteryMonitor != null ? batteryMonitor.getCurrentBatteryLevel() : -1;
    }

    // ============================================================================
    // IQueueBackpressureListener Implementation
    // ============================================================================

    @Override
    public void onQueueFull(int queueSize, int queueLimit)
    {
        if (hasOtherActiveOutputs())
        {
            // Other outputs are active - don't pause scanning, just drop MQTT messages
            Timber.w("MQTT queue full (%d >= %d), but other outputs active - switching to drop mode",
                    queueSize, queueLimit);

            // Don't re-enter drop mode if already dropping
            if (mqttConnection.getAndSetDropMessages(true))
            {
                return;
            }

            updateServiceNotification();
        } else
        {
            // Only MQTT is active - pause scanning to conserve battery
            Timber.w("MQTT queue full (%d >= %d), pausing scanning to conserve battery",
                    queueSize, queueLimit);

            if (isPausedDueToQueueBackpressure.getAndSet(true))
            {
                // Already paused, no need to pause again
                return;
            }

            // Pause all controllers
            cellularController.pauseScanning();
            wifiController.pauseScanning();
            bluetoothController.pauseScanning();
            gnssController.pauseScanning();

            updateServiceNotification();
        }
    }

    @Override
    public void onQueueDrained(int queueSize, int queueLimit)
    {
        Timber.i("MQTT queue drained (%d < %d/2)", queueSize, queueLimit);

        // If we were in drop mode, exit drop mode
        if (mqttConnection.getAndSetDropMessages(false))
        {
            Timber.i("MQTT queue drained - resuming message queueing");
            updateServiceNotification();
            return;
        }

        // If we were paused, resume scanning
        if (!isPausedDueToQueueBackpressure.getAndSet(false))
        {
            // Was not paused due to backpressure, nothing to resume
            return;
        }

        Timber.i("MQTT queue drained - resuming scanning");

        // Only resume if we're not also paused due to battery
        if (isPausedForBattery())
        {
            Timber.i("Not resuming scanning because battery is still low");
            // The backpressure pause ended even though scanning stays paused, so republish it
            updateServiceNotification();
            return;
        }

        // Resume all controllers
        cellularController.resumeScanning();
        wifiController.resumeScanning();
        bluetoothController.resumeScanning();
        gnssController.resumeScanning();

        updateServiceNotification();
    }

    /**
     * Checks if operations are currently paused due to streaming queue backpressure.
     *
     * @return true if paused due to queue backpressure, false otherwise
     */
    public boolean isPausedForQueueBackpressure()
    {
        return isPausedDueToQueueBackpressure.get();
    }

    // ============================================================================
    // MQTT Drop Mode State Management
    // ============================================================================

    /**
     * Checks if MQTT is currently dropping messages due to queue backpressure.
     * This is different from pause mode - in drop mode, scanning continues but MQTT messages are dropped.
     *
     * @return true if MQTT is dropping messages, false otherwise
     */
    public boolean isMqttDroppingMessages()
    {
        return mqttConnection != null && mqttConnection.isDropping();
    }
}
