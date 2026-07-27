package com.craxiom.networksurvey.services.controller;

import static com.craxiom.networksurvey.listeners.CdrSmsObserver.SMS_URI;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.SimChangeReceiver;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.CdrSmsObserver;
import com.craxiom.networksurvey.logging.CdmaCsvLogger;
import com.craxiom.networksurvey.logging.CdrLogger;
import com.craxiom.networksurvey.logging.CellularSurveyRecordLogger;
import com.craxiom.networksurvey.logging.GsmCsvLogger;
import com.craxiom.networksurvey.logging.LteCsvLogger;
import com.craxiom.networksurvey.logging.NrCsvLogger;
import com.craxiom.networksurvey.logging.PhoneStateCsvLogger;
import com.craxiom.networksurvey.logging.PhoneStateRecordLogger;
import com.craxiom.networksurvey.logging.UmtsCsvLogger;
import com.craxiom.networksurvey.model.LogTypeState;
import com.craxiom.networksurvey.model.NetworkTechnologyInfo;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.util.CalculationUtils;
import com.craxiom.networksurvey.util.NsUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.craxiom.networksurvey.util.TelephonyDiagnostics;
import com.craxiom.networksurvey.util.TelephonyStateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * Handles all of the cellular related logic for Network Survey Service to include file logging
 * and managing the cellular scanning.
 *
 * @noinspection NonPrivateFieldAccessedInSynchronizedContext
 */
public class CellularController extends AController
{
    private static final int PING_RATE_MS = 10_000;
    public static final int DEFAULT_SUBSCRIPTION_ID = Integer.MAX_VALUE; // AKA SubscriptionManager.DEFAULT_SUBSCRIPTION_ID

    private final AtomicBoolean cellularScanningActive = new AtomicBoolean(false);
    private final AtomicBoolean airplaneModeActive = new AtomicBoolean(false);

    private final AtomicBoolean cellularLoggingEnabled = new AtomicBoolean(false);

    private final AtomicInteger cellularScanningTaskId = new AtomicInteger();

    private final Handler serviceHandler;
    private final SurveyRecordProcessor surveyRecordProcessor;

    private volatile int cellularScanRateMs;

    private final List<TelephonyManagerWrapper> telephonyManagerList = new ArrayList<>();
    private final Map<Integer, CellInfoCallbackWrapper> cellInfoCallbackMap = new HashMap<>();
    private final Map<Integer, CellInfoCallbackImpl.CellInfoCallbackListener> cellInfoListenerMap = new HashMap<>();
    // Using Object type to prevent class loading issues on devices below API 31
    private final Map<Integer, Object> displayInfoCallbackMap = new HashMap<>();
    private final Object activeSubscriptionInfoListLock = new Object();
    private List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<>();

    private final CellularSurveyRecordLogger cellularSurveyRecordLogger;
    private final PhoneStateRecordLogger phoneStateRecordLogger;
    private final PhoneStateCsvLogger phoneStateCsvLogger;
    private final NrCsvLogger nrCsvLogger;
    private final LteCsvLogger lteCsvLogger;
    private final UmtsCsvLogger umtsCsvLogger;
    private final CdmaCsvLogger cdmaCsvLogger;
    private final GsmCsvLogger gsmCsvLogger;
    private final Map<Integer, PhoneStateListener> phoneStateListenerMap = new HashMap<>();
    private BroadcastReceiver simBroadcastReceiver;

    private final AtomicBoolean phoneStateLoggingEnabled = new AtomicBoolean(false);
    private final AtomicBoolean phoneStateAutoStartedByCellular = new AtomicBoolean(false);

    private final AtomicBoolean cdrLoggingEnabled = new AtomicBoolean(false);
    private final AtomicBoolean cdrStarted = new AtomicBoolean(false);
    private final CdrLogger cdrLogger;
    private final Map<Integer, PhoneStateListener> phoneStateCdrListenerMap = new HashMap<>();
    private ContentObserver smsObserver;

    public CellularController(NetworkSurveyService surveyService, ExecutorService executorService,
                              Looper serviceLooper, Handler serviceHandler,
                              SurveyRecordProcessor surveyRecordProcessor)
    {
        super(surveyService, executorService);
        this.serviceHandler = serviceHandler;
        this.surveyRecordProcessor = surveyRecordProcessor;

        cellularSurveyRecordLogger = new CellularSurveyRecordLogger(surveyService, serviceLooper);
        phoneStateRecordLogger = new PhoneStateRecordLogger(surveyService, serviceLooper);
        phoneStateCsvLogger = new PhoneStateCsvLogger(surveyService);
        nrCsvLogger = new NrCsvLogger(surveyService);
        lteCsvLogger = new LteCsvLogger(surveyService);
        umtsCsvLogger = new UmtsCsvLogger(surveyService);
        cdmaCsvLogger = new CdmaCsvLogger(surveyService);
        gsmCsvLogger = new GsmCsvLogger(surveyService);
        cdrLogger = new CdrLogger(surveyService);
    }

    @Override
    public void onDestroy()
    {
        // Sync on the cellularLoggingEnabled to ensure cleaning up resources (e.g. assigning null
        // to the surveyService) does not cause a NPE if logging is still being enabled or disabled.
        synchronized (cellularLoggingEnabled)
        {
            // Unregister the SIM broadcast receiver to prevent memory leak
            if (simBroadcastReceiver != null && surveyService != null)
            {
                LocalBroadcastManager.getInstance(surveyService).unregisterReceiver(simBroadcastReceiver);
                simBroadcastReceiver = null;
            }

            // Clear callback maps to prevent memory leaks from pending TelephonyManager callbacks
            // The cellInfoListenerMap holds strong references to listeners that capture this controller,
            // clearing it breaks the reference chain even if framework binder stubs hold the callbacks
            synchronized (activeSubscriptionInfoListLock)
            {
                cellInfoCallbackMap.clear();
                cellInfoListenerMap.clear();
                displayInfoCallbackMap.clear();
                telephonyManagerList.clear();
            }

            cellularSurveyRecordLogger.onDestroy();
            phoneStateRecordLogger.onDestroy();
            phoneStateCsvLogger.onDestroy();
            nrCsvLogger.onDestroy();
            lteCsvLogger.onDestroy();
            umtsCsvLogger.onDestroy();
            cdmaCsvLogger.onDestroy();
            gsmCsvLogger.onDestroy();
            super.onDestroy();
        }
    }

    public boolean isLoggingEnabled()
    {
        return cellularLoggingEnabled.get();
    }

    public boolean isScanningActive()
    {
        return cellularScanningActive.get();
    }

    public boolean isPhoneStateLoggingEnabled()
    {
        return phoneStateLoggingEnabled.get();
    }

    public boolean isPhoneStateAutoStartedByCellular()
    {
        return phoneStateAutoStartedByCellular.get();
    }

    public void setPhoneStateAutoStartedByCellular(boolean autoStarted)
    {
        phoneStateAutoStartedByCellular.set(autoStarted);
    }

    public boolean isCdrLoggingEnabled()
    {
        return cdrLoggingEnabled.get();
    }

    public boolean isAirplaneModeActive()
    {
        return airplaneModeActive.get();
    }

    public int getScanRateMs()
    {
        return cellularScanRateMs;
    }

    public int getSimCount()
    {
        synchronized (activeSubscriptionInfoListLock)
        {
            return activeSubscriptionInfoList.size();
        }
    }

    public List<SubscriptionInfo> getActiveSubscriptionInfoList()
    {
        synchronized (activeSubscriptionInfoListLock)
        {
            return Collections.unmodifiableList(activeSubscriptionInfoList);
        }
    }

    public void onRolloverPreferenceChanged()
    {
        cellularSurveyRecordLogger.onSharedPreferenceChanged();
        phoneStateRecordLogger.onSharedPreferenceChanged();
        phoneStateCsvLogger.onSharedPreferenceChanged();
        nrCsvLogger.onSharedPreferenceChanged();
        lteCsvLogger.onSharedPreferenceChanged();
        umtsCsvLogger.onSharedPreferenceChanged();
        cdmaCsvLogger.onSharedPreferenceChanged();
        gsmCsvLogger.onSharedPreferenceChanged();
        cdrLogger.onSharedPreferenceChanged();
    }

    /**
     * Called to indicate that an MDM preference changed, which should trigger a re-read of the
     * preferences.
     */
    public void onMdmPreferenceChanged()
    {
        cellularSurveyRecordLogger.onMdmPreferenceChanged();
        phoneStateRecordLogger.onMdmPreferenceChanged();
        phoneStateCsvLogger.onSharedPreferenceChanged();
        nrCsvLogger.onSharedPreferenceChanged();
        lteCsvLogger.onSharedPreferenceChanged();
        umtsCsvLogger.onSharedPreferenceChanged();
        cdmaCsvLogger.onSharedPreferenceChanged();
        gsmCsvLogger.onSharedPreferenceChanged();
        cdrLogger.onMdmPreferenceChanged();
    }

    public void onLogFileTypePreferenceChanged()
    {
        synchronized (cellularLoggingEnabled)
        {
            if (cellularLoggingEnabled.get())
            {
                final boolean originalLoggingState = cellularLoggingEnabled.get();
                toggleLogging(false);
                toggleLogging(true);
                final boolean newLoggingState = cellularLoggingEnabled.get();
                if (originalLoggingState != newLoggingState)
                {
                    Timber.i("Logging state changed from %s to %s", originalLoggingState, newLoggingState);
                }
            }
        }

        synchronized (phoneStateLoggingEnabled)
        {
            if (phoneStateLoggingEnabled.get())
            {
                togglePhoneStateLogging(false);
                togglePhoneStateLogging(true);
            }
        }
    }

    /**
     * Called to indicate that the cellular scan rate preference changed, which should trigger a
     * re-read of the preference.
     */
    public void refreshScanRate()
    {
        if (surveyService == null) return;

        cellularScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS, surveyService.getApplicationContext());
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
    public Boolean toggleLogging(boolean enable)
    {
        synchronized (cellularLoggingEnabled)
        {
            final boolean originalLoggingState = cellularLoggingEnabled.get();
            if (originalLoggingState == enable) return originalLoggingState;

            if (surveyService == null) return null;

            Timber.i("Toggling cellular logging to %s", enable);

            boolean successful = false;
            if (enable)
            {
                LogTypeState types = PreferenceUtils.getLogTypePreference(surveyService.getApplicationContext());

                if (types.geoPackage)
                {
                    successful = cellularSurveyRecordLogger.enableLogging(true);
                }
                if (types.csv)
                {
                    successful = nrCsvLogger.enableLogging(true) &&
                            lteCsvLogger.enableLogging(true) &&
                            umtsCsvLogger.enableLogging(true) &&
                            cdmaCsvLogger.enableLogging(true) &&
                            gsmCsvLogger.enableLogging(true);
                }

                if (successful)
                {
                    toggleCellularConfig(true, types);
                } else
                {
                    Timber.e("Unsuccessful in enabling cellular logging");
                    // at least one of the loggers failed to toggle;
                    // disable all of them and set local config to false
                    cellularSurveyRecordLogger.enableLogging(false);
                    nrCsvLogger.enableLogging(false);
                    lteCsvLogger.enableLogging(false);
                    umtsCsvLogger.enableLogging(false);
                    cdmaCsvLogger.enableLogging(false);
                    gsmCsvLogger.enableLogging(false);
                    toggleCellularConfig(false, null);
                }
            } else
            {
                // If we are disabling logging, then we need to disable both geoPackage and CSV just
                // in case the user changed the setting after they started logging.
                cellularSurveyRecordLogger.enableLogging(false);
                nrCsvLogger.enableLogging(false);
                lteCsvLogger.enableLogging(false);
                umtsCsvLogger.enableLogging(false);
                cdmaCsvLogger.enableLogging(false);
                gsmCsvLogger.enableLogging(false);
                toggleCellularConfig(false, null);
                successful = true;
            }

            surveyService.updateServiceNotification();
            surveyService.notifyLoggingChangedListeners();

            final boolean newLoggingState = cellularLoggingEnabled.get();
            if (successful && newLoggingState) initializePing();

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
                    surveyService.registerCdrEventListener(cdrLogger);
                } else
                {
                    surveyService.unregisterCdrEventListener(cdrLogger);
                }
            }

            surveyService.updateServiceNotification();
            surveyService.notifyLoggingChangedListeners();

            final boolean newLoggingState = cdrLoggingEnabled.get();

            return successful ? newLoggingState : null;
        }
    }

    /**
     * Toggles the phone state file logging setting.
     * <p>
     * It is possible that an error occurs while trying to enable or disable logging. In that event
     * null will be returned indicating that logging could not be toggled.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @return The new state of logging. True if it is enabled, or false if it is disabled. Null is
     * returned if the toggling was unsuccessful.
     */
    public Boolean togglePhoneStateLogging(boolean enable)
    {
        synchronized (phoneStateLoggingEnabled)
        {
            final boolean originalLoggingState = phoneStateLoggingEnabled.get();
            if (originalLoggingState == enable) return originalLoggingState;

            if (surveyService == null) return null;

            Timber.i("Toggling phone state logging to %s", enable);

            boolean successful = false;
            if (enable)
            {
                LogTypeState types = PreferenceUtils.getLogTypePreference(surveyService.getApplicationContext());

                if (types.geoPackage)
                {
                    successful = phoneStateRecordLogger.enableLogging(true);
                }
                if (types.csv)
                {
                    successful = phoneStateCsvLogger.enableLogging(true);
                }

                if (successful)
                {
                    phoneStateLoggingEnabled.set(true);
                    if (types.geoPackage)
                    {
                        surveyService.registerPhoneStateListener(phoneStateRecordLogger);
                    }
                    if (types.csv)
                    {
                        surveyService.registerPhoneStateListener(phoneStateCsvLogger);
                    }
                } else
                {
                    Timber.e("Unsuccessful in enabling phone state logging");
                    phoneStateRecordLogger.enableLogging(false);
                    phoneStateCsvLogger.enableLogging(false);
                }
            } else
            {
                phoneStateLoggingEnabled.set(false);
                phoneStateRecordLogger.enableLogging(false);
                phoneStateCsvLogger.enableLogging(false);
                surveyService.unregisterPhoneStateListener(phoneStateRecordLogger);
                surveyService.unregisterPhoneStateListener(phoneStateCsvLogger);
                successful = true;
            }

            surveyService.updateServiceNotification();
            surveyService.notifyLoggingChangedListeners();

            final boolean newLoggingState = phoneStateLoggingEnabled.get();

            return successful ? newLoggingState : null;
        }
    }

    /**
     * Attempts to auto-start phone state logging if it is not already running. This method is
     * synchronized to prevent race conditions between the check and the toggle when called from
     * the auto-include logic.
     *
     * @return True if phone state logging was started, false if it was already running.
     */
    public synchronized boolean autoStartPhoneStateIfNotRunning()
    {
        if (!phoneStateLoggingEnabled.get())
        {
            togglePhoneStateLogging(true);
            phoneStateAutoStartedByCellular.set(true);
            return true;
        }
        return false;
    }

    /**
     * Sends out a ping to the Google DNS IP Address (8.8.8.8) every n seconds.  This allows the data connection to
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
                    Timber.e(e, "An exception occurred trying to send out a ping");
                }
            }
        }, PING_RATE_MS);
    }

    public synchronized void startPhoneStateListener()
    {
        // The onServiceStateChanged required API level 29.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            if (surveyService == null) return;

            clearPhoneStateListeners();

            for (TelephonyManagerWrapper wrapper : telephonyManagerList)
            {
                if (surveyService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
                {
                    int subscriptionId = wrapper.getSubscriptionId();
                    Timber.d("Adding the Telephony Manager Service State Listener for subscription ID %s", subscriptionId);

                    // Sadly we have to use the service handler for this because the PhoneStateListener constructor calls
                    // Looper.myLooper(), which needs to be run from a thread where the looper is prepared. The better option
                    // is to use the constructor that takes an executor service, but that is only supported in Android 10+.
                    serviceHandler.post(() -> {
                        PhoneStateListener phoneStateListener = new PhoneStateListener()
                        {
                            @Override
                            public void onServiceStateChanged(ServiceState serviceState)
                            {
                                boolean isInAirplaneMode = serviceState.getState() == ServiceState.STATE_POWER_OFF;
                                airplaneModeActive.set(isInAirplaneMode);

                                if (isPaused())
                                {
                                    Timber.v("Service state changed but scanning is paused, ignoring");
                                    return;
                                }

                                // Don't process service state changes when in airplane mode
                                if (!isInAirplaneMode)
                                {
                                    execute(() -> surveyRecordProcessor.onServiceStateChanged(serviceState, wrapper.getTelephonyManager(), subscriptionId));
                                }
                            }

                            // We can't use this because you have to be a system app to get the READ_PRECISE_PHONE_STATE permission.
                            // So this is unused for now, but maybe at some point in the future we can make use of it.
                            /*@Override
                            public void onRegistrationFailed(@NonNull CellIdentity cellIdentity, @NonNull String chosenPlmn, int domain, int causeCode, int additionalCauseCode)
                            {
                                execute(() -> surveyRecordProcessor.onRegistrationFailed(cellIdentity, domain, causeCode, additionalCauseCode, telephonyManager));
                            }*/
                        };

                        phoneStateListenerMap.put(subscriptionId, phoneStateListener);
                        wrapper.getTelephonyManager().listen(phoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
                    });
                }
            }
        }
    }

    public synchronized void stopPhoneStateListener()
    {
        telephonyManagerList.forEach(wrapper -> {
            if (wrapper != null && surveyService != null)
            {
                final TelephonyManager telephonyManager = wrapper.getTelephonyManager();
                if (telephonyManager != null && surveyService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
                {
                    try
                    {
                        PhoneStateListener phoneStateListener = phoneStateListenerMap.get(wrapper.getSubscriptionId());
                        if (phoneStateListener != null)
                        {
                            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
                            phoneStateListenerMap.remove(wrapper.getSubscriptionId());
                        }
                    } catch (Exception e)
                    {
                        // This is expected if a SIM card is added or removed because the telephony
                        // service will have changed out from under us.
                        Timber.e(e, "An exception occurred trying to remove the PhoneStateListener");
                    }
                }
            }
        });
    }

    /**
     * Initialize the resources needed for the Cellular Controller. This method causes the various listeners and
     * receivers to be registered so that the cellular controller is ready to start scanning using the
     * {@link #startCellularRecordScanning()} method.
     */
    public void initialize()
    {
        initializeCellularScanningResources();

        registerSimStateChangeReceiver();
    }

    /**
     * Create the Cellular Scan callback that will be notified of Cellular scan events once
     * {@link #startCellularRecordScanning()} is called.
     * <p>
     * Synchronized to ensure that the activeSubscriptionInfoList, telephonyManagerList, and cellInfoCallbackMap are not
     * modified while they are being used. Therefore, make sure to synchronize any other methods that use these lists.
     */
    public synchronized void initializeCellularScanningResources()
    {
        // Ok, 3 synchronized keywords in one method is a bit much, but cellularLoggingEnabled is
        // used to make sure that surveyService won't be assigned null while we are using it.
        synchronized (cellularLoggingEnabled)
        {
            if (surveyService == null) return;

            final TelephonyManager telephonyManager = (TelephonyManager) surveyService.getSystemService(Context.TELEPHONY_SERVICE);

            if (telephonyManager == null)
            {
                Timber.e("Unable to get access to the Telephony Manager.  No network information will be displayed");
                return;
            }

            // Synchronizing the activeSubscriptionInfoListLock for all the telephony resources
            synchronized (activeSubscriptionInfoListLock)
            {
                // Clear the lists because this could be a re-initialization if the SIM state changes
                activeSubscriptionInfoList.clear();
                telephonyManagerList.clear();
                cellInfoCallbackMap.clear();
                cellInfoListenerMap.clear();
                displayInfoCallbackMap.clear();

                SubscriptionManager subscriptionManager = SubscriptionManager.from(surveyService.getApplicationContext());
                if (ActivityCompat.checkSelfPermission(surveyService, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                {
                    activeSubscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
                    if (activeSubscriptionInfoList == null)
                    {
                        Timber.i("The returned active subscription info list was null.");
                        activeSubscriptionInfoList = new ArrayList<>();
                    }

                    Timber.i("Found %s active SIMs", activeSubscriptionInfoList.size());

                    // We only want to use the subscription info list if there are two active SIMs.  If there is only
                    // one active SIM, then we will just use the default subscription ID which gets filtered out in
                    // the SurveyRecordProcessor. This prevents the "slot" field from getting set on all the records.
                    if (activeSubscriptionInfoList.size() >= 2)
                    {
                        for (SubscriptionInfo subscriptionInfo : activeSubscriptionInfoList)
                        {
                            int subId = subscriptionInfo.getSubscriptionId();
                            String myPhoneNumber = subscriptionInfo.getNumber();
                            telephonyManagerList.add(new TelephonyManagerWrapper(telephonyManager.createForSubscriptionId(subId), subId, myPhoneNumber));
                        }
                    } else
                    {
                        String myPhoneNumber = NsUtils.getMyPhoneNumber(surveyService, telephonyManager);
                        telephonyManagerList.add(new TelephonyManagerWrapper(telephonyManager, DEFAULT_SUBSCRIPTION_ID, myPhoneNumber));
                    }
                } else
                {
                    String myPhoneNumber = NsUtils.getMyPhoneNumber(surveyService, telephonyManager);
                    Timber.e("Unable to get access to the Subscription Manager. Can't get survey information from other SIMs");
                    telephonyManagerList.add(new TelephonyManagerWrapper(telephonyManager, DEFAULT_SUBSCRIPTION_ID, myPhoneNumber));
                }
            }

            // Skip callback creation on Android 10 due to framework bug with ParcelableException
            // See: https://issuetracker.google.com/issues/141438333
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            {
                for (TelephonyManagerWrapper wrapper : telephonyManagerList)
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    {
                        // Only instantiate OverrideNetworkTypeListener on API 31+ using factory
                        Object listener = TelephonyCallbackFactory.createOverrideNetworkTypeListener();
                        if (listener != null)
                        {
                            displayInfoCallbackMap.put(wrapper.getSubscriptionId(), listener);
                        }
                    }

                    // Create cell info callback using factory to avoid class loading issues on older devices
                    final int subscriptionId = wrapper.getSubscriptionId();

                    // Create the listener and store it with a strong reference to prevent premature GC
                    CellInfoCallbackImpl.CellInfoCallbackListener listener = new CellInfoCallbackImpl.CellInfoCallbackListener()
                    {
                        @Override
                        public void onCellInfoReceived(@NonNull List<CellInfo> cellInfo)
                        {
                            // Skip processing if in airplane mode
                            if (airplaneModeActive.get())
                            {
                                Timber.v("Cell info received but airplane mode is active, ignoring");
                                return;
                            }

                            // Skip processing if paused for battery management
                            if (isPaused())
                            {
                                Timber.v("Cell info received but scanning is paused, ignoring");
                                return;
                            }

                            TelephonyManager telephonyManager = wrapper.getTelephonyManager();
                            if (surveyService == null) return;

                            String networkOperatorName = telephonyManager.getNetworkOperatorName();
                            SignalStrength signalStrength = telephonyManager.getSignalStrength();

                            NetworkTechnologyInfo technologyInfo = buildNetworkTechnologyInfo(telephonyManager, subscriptionId, "N/A");

                            surveyRecordProcessor.onCellInfoUpdate(cellInfo, technologyInfo,
                                    subscriptionId, networkOperatorName, signalStrength);
                        }

                        @Override
                        public void onCellInfoError(int errorCode, @Nullable Throwable detail)
                        {
                            // Error logging is handled in CellInfoCallbackImpl
                        }
                    };

                    // Store listener with strong reference to keep it alive during normal operation
                    cellInfoListenerMap.put(subscriptionId, listener);

                    // Create callback wrapper
                    CellInfoCallbackWrapper callback = TelephonyCallbackFactory.createCellInfoCallback(listener);

                    if (callback != null)
                    {
                        cellInfoCallbackMap.put(wrapper.getSubscriptionId(), callback);
                    }
                }
            }
        }
    }

    /**
     * Builds the {@link NetworkTechnologyInfo} snapshot shown on the cellular details top card.
     * <p>
     * On API 31+ this uses the cached {@link ServiceState} and display info (populated by the
     * {@link OverrideNetworkTypeListener} on the same executor, so no per-scan blocking binder call)
     * to infer the voice bearer and NR mode, and it prefers the display network type for the Data
     * field (which stays on the cellular RAT during Wi-Fi calling, where
     * {@code getDataNetworkType()} reports IWLAN). On API 26-30, or before the display-info callback
     * has first fired, it falls back to the legacy {@code getVoiceNetworkType()}/
     * {@code getDataNetworkType()} strings and leaves the enriched fields empty.
     * <p>
     * The carrier aggregation bandwidths are the one deliberate exception to the no-per-scan-binder
     * rule: they come from a fresh {@link TelephonyManager#getServiceState()} call on every scan
     * (API 28+). The cached ServiceState only refreshes on registration events, and modems do not
     * push indications for pure SCell add/remove, so a cached value can lag the true carrier
     * aggregation state by an unbounded interval. A fresh read bounds the staleness by the scan
     * interval and also covers API 28-30, where the display-info callback is unavailable. The
     * bandwidths are logged on LTE/NR survey records, so they must reflect scan time.
     *
     * @param telephonyManager The subscription-specific telephony manager to read from.
     * @param subscriptionId   The subscription (SIM) the snapshot belongs to.
     * @param fallbackOverride The override display string to use when the display-info callback has
     *                         no value (preserves the differing per-scan-path literals).
     * @return The technology snapshot with pre-resolved display strings.
     */
    private NetworkTechnologyInfo buildNetworkTechnologyInfo(TelephonyManager telephonyManager,
                                                             int subscriptionId, String fallbackOverride)
    {
        String dataDisplay = NetworkSurveyConstants.UNKNOWN;
        String voiceDisplay = NetworkSurveyConstants.UNKNOWN;
        String overrideDisplay = fallbackOverride;
        TelephonyStateUtils.NrMode nrMode = TelephonyStateUtils.NrMode.NONE;
        int[] cellBandwidthsKhz = null;
        List<TelephonyStateUtils.RegistrationRow> registrationRows = null;

        int dataNetworkTypeInt = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        boolean hasPhoneStatePermission = surveyService != null
                && ActivityCompat.checkSelfPermission(surveyService, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
        if (hasPhoneStatePermission)
        {
            synchronized (cellularLoggingEnabled)
            {
                dataNetworkTypeInt = telephonyManager.getDataNetworkType();
                dataDisplay = CalculationUtils.getNetworkType(dataNetworkTypeInt);
                voiceDisplay = CalculationUtils.getNetworkType(telephonyManager.getVoiceNetworkType());
            }
        }

        int rawOverride = -1;
        int rawDisplay = -1;
        int baseRat = dataNetworkTypeInt;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            Object displayInfoListener = displayInfoCallbackMap.get(subscriptionId);
            if (displayInfoListener != null)
            {
                rawOverride = TelephonyCallbackFactory.getOverrideNetworkType(displayInfoListener);
                rawDisplay = TelephonyCallbackFactory.getDisplayNetworkType(displayInfoListener);
                overrideDisplay = CalculationUtils.getOverrideNetworkType(rawOverride);

                // Prefer the display network type: it is the cellular RAT the status bar badges and
                // stays NR during Wi-Fi calling, where getDataNetworkType() reports IWLAN. Both -1
                // (never reported) and 0 (NETWORK_TYPE_UNKNOWN) mean "not known yet".
                if (rawDisplay > 0)
                {
                    dataDisplay = CalculationUtils.getNetworkType(rawDisplay);
                    baseRat = rawDisplay;
                }
                nrMode = TelephonyStateUtils.deriveNrMode(baseRat, rawOverride);

                ServiceState serviceState = TelephonyCallbackFactory.getServiceState(displayInfoListener);
                if (serviceState != null)
                {
                    registrationRows = TelephonyStateUtils.extractRows(serviceState);
                    voiceDisplay = voiceBearerDisplay(TelephonyStateUtils.deriveVoiceBearer(registrationRows), voiceDisplay);
                }
            }
        }

        if (hasPhoneStatePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            try
            {
                // Read fresh rather than from the cached callback ServiceState (see the javadoc):
                // carrier aggregation flaps with traffic without firing onServiceStateChanged, so
                // only a scan-time read reflects the aggregation state the records are logged with.
                // On Android 11+ the result is location-sanitized without fine location, but the
                // bandwidth list is not location data and survives sanitization.
                ServiceState freshServiceState;
                synchronized (cellularLoggingEnabled)
                {
                    freshServiceState = telephonyManager.getServiceState();
                }
                if (freshServiceState != null)
                {
                    cellBandwidthsKhz = freshServiceState.getCellBandwidths();
                }
            } catch (Exception e)
            {
                Timber.w(e, "Could not read the ServiceState for the cell bandwidths");
            }
        }

        // Debug-only diagnostic snapshot, correlated with the values above.
        TelephonyDiagnostics.logNetworkTypeSnapshot(surveyService, telephonyManager, subscriptionId, rawOverride, rawDisplay);

        return new NetworkTechnologyInfo(subscriptionId, voiceDisplay, dataDisplay, overrideDisplay,
                rawOverride, baseRat, nrMode, cellBandwidthsKhz, registrationRows);
    }

    /**
     * Resolves an inferred {@link TelephonyStateUtils.VoiceBearerResult} to a display string.
     *
     * @param result   The derived voice bearer.
     * @param fallback The value to use if the context is unavailable.
     * @return The display string (e.g. "Wi-Fi Calling", "VoNR", "GSM", "None").
     */
    private String voiceBearerDisplay(TelephonyStateUtils.VoiceBearerResult result, String fallback)
    {
        if (surveyService == null) return fallback;
        return switch (result.type())
        {
            case WIFI_CALLING -> surveyService.getString(R.string.voice_bearer_wifi_calling);
            case VOLTE -> NetworkSurveyConstants.VOLTE;
            case VONR -> NetworkSurveyConstants.VONR;
            case CIRCUIT_SWITCHED -> CalculationUtils.getNetworkType(result.circuitSwitchedRat());
            case NONE -> surveyService.getString(R.string.voice_bearer_none);
        };
    }

    /**
     * Registers a receiver for SIM state change events.
     */
    private void registerSimStateChangeReceiver()
    {
        if (surveyService == null) return;

        simBroadcastReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                if (intent == null) return;

                Timber.i("SIM State Change Detected. Refreshing the active subscription info list");

                boolean phoneStateWasEnabled = !phoneStateListenerMap.isEmpty();
                boolean cdrWasEnabled = cdrStarted.get();

                if (phoneStateWasEnabled) stopPhoneStateListener();
                if (cdrWasEnabled) stopCdrEvents();

                initializeCellularScanningResources();

                // Stop and start the phone state listener so that it will be listening to the new SIM(s),
                // only if they were started before the SIM change.
                if (phoneStateWasEnabled) startPhoneStateListener();
                if (cdrWasEnabled) startCdrEvents();
            }
        };

        LocalBroadcastManager.getInstance(surveyService).registerReceiver(simBroadcastReceiver,
                new IntentFilter(SimChangeReceiver.SIM_CHANGED_INTENT));
    }

    /**
     * Runs one cellular scan. This is used to prime the UI in the event that the scan interval is really long.
     * <p>
     * Need to synchronize it because we use the resources that are initialized on SIM changes
     * such as telephonyManagerList.
     */
    public synchronized void runSingleScan()
    {
        if (surveyService == null) return;

        final TelephonyManager telephonyManager = (TelephonyManager) surveyService.getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager == null || !surveyService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
        {
            Timber.w("Unable to get access to the Telephony Manager.  No network information will be displayed");
            return;
        }

        // The service handler can be null if this service has been stopped but the activity still has a reference to this old service
        if (serviceHandler == null) return;

        serviceHandler.postDelayed(() -> {
            try
            {
                synchronized (activeSubscriptionInfoListLock)
                {
                    // Android 10 (API 29) has a framework bug in TelephonyManager.CellInfoCallback where
                    // ParcelableException can be null, causing NPE in the framework code when onError is called.
                    // We skip the callback approach on Android 10 and use the same fallback as pre-API 29.
                    // See: https://issuetracker.google.com/issues/141438333
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    {
                        for (TelephonyManagerWrapper wrapper : telephonyManagerList)
                        {
                            // Skipping the override network type listener because we don't need it for a single scan

                            CellInfoCallbackWrapper callbackWrapper = cellInfoCallbackMap.get(wrapper.getSubscriptionId());
                            if (callbackWrapper != null)
                            {
                                TelephonyManager.CellInfoCallback androidCallback = TelephonyCallbackFactory.getAndroidCellInfoCallback(callbackWrapper);
                                if (androidCallback != null)
                                {
                                    wrapper.getTelephonyManager().requestCellInfoUpdate(executorService, androidCallback);
                                }
                            } else
                            {
                                Timber.wtf("Could not find the callback for the subscription ID %s", wrapper.getSubscriptionId());
                            }
                        }
                    } else
                    {
                        execute(() -> {
                            try
                            {
                                for (TelephonyManagerWrapper wrapper : telephonyManagerList)
                                {
                                    TelephonyManager subscriptionTelephonyManager = wrapper.getTelephonyManager();

                                    SignalStrength signalStrength = null;
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                                    {
                                        signalStrength = subscriptionTelephonyManager.getSignalStrength();
                                    }

                                    NetworkTechnologyInfo technologyInfo = buildNetworkTechnologyInfo(
                                            subscriptionTelephonyManager, wrapper.getSubscriptionId(), "None");

                                    surveyRecordProcessor.onCellInfoUpdate(subscriptionTelephonyManager.getAllCellInfo(),
                                            technologyInfo,
                                            wrapper.getSubscriptionId(),
                                            subscriptionTelephonyManager.getNetworkOperatorName(),
                                            signalStrength);
                                }
                            } catch (Throwable t)
                            {
                                Timber.e(t, "Something went wrong when trying to get the cell info for a single scan");
                            }
                        });
                    }
                }
            } catch (SecurityException e)
            {
                Timber.e(e, "Could not get the required permissions to get the network details");
            } catch (Exception e)
            {
                Timber.e(e, "An exception occurred trying to get the latest cellular information for a single scan");
            }
        }, 1_000);
    }

    /**
     * Gets the {@link TelephonyManager}, and then starts a regular poll of cellular records.
     * <p>
     * This method only starts scanning if the scan is not already active.
     */
    public void startCellularRecordScanning()
    {
        synchronized (cellularLoggingEnabled)
        {
            if (surveyService == null) return;

            if (cellularScanningActive.getAndSet(true)) return;

            final TelephonyManager telephonyManager = (TelephonyManager) surveyService.getSystemService(Context.TELEPHONY_SERVICE);

            if (telephonyManager == null || !surveyService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            {
                Timber.w("Unable to get access to the Telephony Manager.  No network information will be displayed");
                return;
            }

            final int handlerTaskId = cellularScanningTaskId.incrementAndGet();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                for (TelephonyManagerWrapper wrapper : telephonyManagerList)
                {
                    Object displayInfoListener = displayInfoCallbackMap.get(wrapper.getSubscriptionId());
                    if (displayInfoListener instanceof TelephonyCallback)
                    {
                        wrapper.getTelephonyManager().registerTelephonyCallback(executorService, (TelephonyCallback) displayInfoListener);
                    }
                }
            }

            serviceHandler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        if (!cellularScanningActive.get() || cellularScanningTaskId.get() != handlerTaskId)
                        {
                            Timber.i("Stopping the handler that pulls the latest cellular information; taskId=%d", handlerTaskId);
                            return;
                        }

                        // Check if scanning is paused for battery management
                        if (isPaused())
                        {
                            // Keep the handler alive but skip actual scanning
                            serviceHandler.postDelayed(this, cellularScanRateMs);
                            return;
                        }

                        // Need to synchronize because we use resources that are initialized on SIM
                        // changes such as telephonyManagerList
                        synchronized (activeSubscriptionInfoListLock)
                        {
                            // Android 10 (API 29) has a framework bug in TelephonyManager.CellInfoCallback where
                            // ParcelableException can be null, causing NPE in the framework code when onError is called.
                            // We skip the callback approach on Android 10 and use the same fallback as pre-API 29.
                            // See: https://issuetracker.google.com/issues/141438333
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            {
                                for (TelephonyManagerWrapper wrapper : telephonyManagerList)
                                {
                                    CellInfoCallbackWrapper callbackWrapper = cellInfoCallbackMap.get(wrapper.getSubscriptionId());
                                    if (callbackWrapper != null)
                                    {
                                        TelephonyManager.CellInfoCallback androidCallback = TelephonyCallbackFactory.getAndroidCellInfoCallback(callbackWrapper);
                                        if (androidCallback != null)
                                        {
                                            wrapper.getTelephonyManager().requestCellInfoUpdate(executorService, androidCallback);
                                        }
                                    } else
                                    {
                                        Timber.wtf("Could not find the callback for the subscription ID %s", wrapper.getSubscriptionId());
                                    }
                                }
                            } else
                            {
                                execute(() -> {
                                    try
                                    {
                                        for (TelephonyManagerWrapper wrapper : telephonyManagerList)
                                        {
                                            TelephonyManager subscriptionTelephonyManager = wrapper.getTelephonyManager();

                                            SignalStrength signalStrength = null;
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                                            {
                                                signalStrength = subscriptionTelephonyManager.getSignalStrength();
                                            }

                                            NetworkTechnologyInfo technologyInfo = buildNetworkTechnologyInfo(
                                                    subscriptionTelephonyManager, wrapper.getSubscriptionId(), "N/A");

                                            surveyRecordProcessor.onCellInfoUpdate(subscriptionTelephonyManager.getAllCellInfo(),
                                                    technologyInfo,
                                                    wrapper.getSubscriptionId(),
                                                    subscriptionTelephonyManager.getNetworkOperatorName(),
                                                    signalStrength);
                                        }
                                    } catch (Throwable t)
                                    {
                                        Timber.e(t, "Failed to pass the cellular info to the survey record processor");
                                    }
                                });
                            }
                        }

                        serviceHandler.postDelayed(this, cellularScanRateMs);
                    } catch (SecurityException e)
                    {
                        Timber.e(e, "Could not get the required permissions to get the network details");
                    } catch (Exception e)
                    {
                        Timber.e(e, "An exception occurred trying to get the latest cellular information");
                    }
                }
            }, 1_000);

            surveyService.updateLocationListener();
        }
    }

    /**
     * Stop polling for cellular scan updates.
     */
    public void stopCellularRecordScanning()
    {
        Timber.d("Setting the cellular scanning active flag to false");
        cellularScanningActive.set(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            for (TelephonyManagerWrapper wrapper : telephonyManagerList)
            {
                Object displayInfoListener = displayInfoCallbackMap.get(wrapper.getSubscriptionId());
                if (displayInfoListener instanceof TelephonyCallback)
                {
                    wrapper.getTelephonyManager().unregisterTelephonyCallback((TelephonyCallback) displayInfoListener);
                }
            }
        }

        if (surveyService != null) surveyService.updateLocationListener();
    }

    /**
     * If any of the loggers are still active, this stops them all just to be safe. If they are not active then nothing
     * changes.
     */
    public void stopAllLogging()
    {
        toggleLogging(false);
        togglePhoneStateLogging(false);
        toggleCdrLogging(false);
    }

    /**
     * @param enable Value used to set {@code cellularLoggingEnabled}, and cellular and device
     *               status listeners. If {@code true} listeners are registered, otherwise they
     *               are unregistered.
     */
    private void toggleCellularConfig(boolean enable, LogTypeState types)
    {
        if (surveyService == null) return;

        cellularLoggingEnabled.set(enable);
        if (enable)
        {
            if (types != null)
            {
                if (types.geoPackage)
                {
                    surveyService.registerCellularSurveyRecordListener(cellularSurveyRecordLogger);
                }
                if (types.csv)
                {
                    surveyService.registerCellularSurveyRecordListener(nrCsvLogger);
                    surveyService.registerCellularSurveyRecordListener(lteCsvLogger);
                    surveyService.registerCellularSurveyRecordListener(umtsCsvLogger);
                    surveyService.registerCellularSurveyRecordListener(cdmaCsvLogger);
                    surveyService.registerCellularSurveyRecordListener(gsmCsvLogger);
                }
            } else
            {
                throw new IllegalArgumentException("LogTypeState cannot be null when enabling cellular logging");
            }
        } else
        {
            surveyService.unregisterCellularSurveyRecordListener(cellularSurveyRecordLogger);
            surveyService.unregisterCellularSurveyRecordListener(nrCsvLogger);
            surveyService.unregisterCellularSurveyRecordListener(lteCsvLogger);
            surveyService.unregisterCellularSurveyRecordListener(umtsCsvLogger);
            surveyService.unregisterCellularSurveyRecordListener(cdmaCsvLogger);
            surveyService.unregisterCellularSurveyRecordListener(gsmCsvLogger);
        }
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
            //Timber.v("Ping Exit Value: %s", exitValue);
        } catch (Exception e)
        {
            Timber.e(e, "An exception occurred trying to send out a ping ");
        }
    }

    /**
     * Initialize and start the handler that listens for phone state events to create CDR events.
     * <p>
     * This method only starts the CDR listener if it is not already active.
     */
    public void startCdrEvents()
    {
        if (surveyService == null) return;

        if (ActivityCompat.checkSelfPermission(surveyService, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
        {
            // Not notifying the user here because if the user toggled this via the UI then we already checked this
            // permission. The way we can reach this point without the permission is if CDR was turned on via MDM
            // control, in which case a user notification is not necessary.
            Timber.e("Unable to get the READ_PHONE_STATE permission. CDR logging won't work.");
            return;
        }

        synchronized (activeSubscriptionInfoListLock)
        {
            if (cdrStarted.getAndSet(true)) return;

            // Add a listener for the Service State information if we have access to the Telephony Manager
            if (surveyService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            {
                if (ActivityCompat.checkSelfPermission(surveyService, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
                {
                    ContentResolver contentResolver = surveyService.getContentResolver();
                    smsObserver = new CdrSmsObserver(serviceHandler, contentResolver, this, surveyRecordProcessor, executorService);
                    contentResolver.registerContentObserver(SMS_URI, true, smsObserver);
                }

                Timber.d("Adding the Telephony Manager Service State Listener for CDR events");

                clearPhoneStateCdrListeners();

                for (TelephonyManagerWrapper wrapper : telephonyManagerList)
                {
                    if (surveyService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
                    {
                        // Sadly we have to use the service handler for this because the PhoneStateListener constructor calls
                        // Looper.myLooper(), which needs to be run from a thread where the looper is prepared. The better option
                        // is to use the constructor that takes an executor service, but that is only supported in Android 10+.
                        serviceHandler.post(() -> {
                            PhoneStateListener phoneStateCdrListener = new PhoneStateListener()
                            {
                                @Override
                                public void onCallStateChanged(int state, String otherPhoneNumber)
                                {
                                    if (isPaused())
                                    {
                                        Timber.v("Call state changed but scanning is paused, ignoring");
                                        return;
                                    }
                                    execute(() -> surveyRecordProcessor.onCallStateChanged(state, otherPhoneNumber,
                                            wrapper.getTelephonyManager(), wrapper.getPhoneNumber(), wrapper.getSubscriptionId()));
                                }

                                @Override
                                public void onServiceStateChanged(ServiceState serviceState)
                                {
                                    boolean isInAirplaneMode = serviceState.getState() == ServiceState.STATE_POWER_OFF;
                                    airplaneModeActive.set(isInAirplaneMode);

                                    if (isInAirplaneMode)
                                    {
                                        Timber.v("CDR service state changed but airplane mode is active, ignoring");
                                        return;
                                    }

                                    if (isPaused())
                                    {
                                        Timber.v("CDR service state changed but scanning is paused, ignoring");
                                        return;
                                    }
                                    execute(() -> surveyRecordProcessor.onCdrServiceStateChanged(serviceState,
                                            wrapper.getTelephonyManager(), wrapper.getSubscriptionId()));
                                }
                            };

                            synchronized (phoneStateCdrListenerMap)
                            {
                                phoneStateCdrListenerMap.put(wrapper.getSubscriptionId(), phoneStateCdrListener);
                                wrapper.getTelephonyManager().listen(phoneStateCdrListener, PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_SERVICE_STATE);
                            }
                        });
                    }
                }
            }
        }
    }

    /**
     * Clears the phone state listeners for the cellular status events and delete everything in the map.
     * See {@link #clearPhoneStateCdrListeners()} for clearing the listener for the CDR logging.
     */
    private synchronized void clearPhoneStateListeners()
    {
        phoneStateListenerMap.forEach((subscriptionId, listener) -> {
            TelephonyManagerWrapper wrapper = getTelephonyManagerForSubscription(subscriptionId);
            if (wrapper != null)
            {
                TelephonyManager telephonyManager = wrapper.getTelephonyManager();
                if (telephonyManager != null)
                {
                    telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
                }
            }
        });

        phoneStateListenerMap.clear();
    }

    /**
     * Clears the phone state listeners for CDR events and delete everything in the map.
     */
    private void clearPhoneStateCdrListeners()
    {
        // Synchronized to prevent adding or removing in the start/stop methods while clearing here
        synchronized (phoneStateCdrListenerMap)
        {
            phoneStateCdrListenerMap.forEach((subscriptionId, listener) -> {
                TelephonyManagerWrapper wrapper = getTelephonyManagerForSubscription(subscriptionId);
                if (wrapper != null)
                {
                    TelephonyManager telephonyManager = wrapper.getTelephonyManager();
                    if (telephonyManager != null)
                    {
                        telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
                    }
                }
            });

            phoneStateCdrListenerMap.clear();
        }
    }

    /**
     * Remove the phone state listener for CDR events.
     */
    public void stopCdrEvents()
    {
        Timber.d("Setting the cdr active flag to false");

        synchronized (phoneStateCdrListenerMap)
        {
            telephonyManagerList.forEach(wrapper -> {
                if (wrapper != null && surveyService != null)
                {
                    final TelephonyManager telephonyManager = wrapper.getTelephonyManager();
                    if (telephonyManager != null && surveyService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
                    {
                        try
                        {
                            int subscriptionId = wrapper.getSubscriptionId();
                            final PhoneStateListener listener = phoneStateCdrListenerMap.get(subscriptionId);
                            if (listener != null)
                            {
                                telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
                                phoneStateCdrListenerMap.remove(subscriptionId);
                            }
                        } catch (Exception e)
                        {
                            // This is expected if a SIM card is added or removed because the telephony
                            // service will have changed out from under us.
                            Timber.e(e, "An exception occurred trying to remove the PhoneStateListener");
                        }
                    }
                }
            });
        }

        if (smsObserver != null)
        {
            surveyService.getContentResolver().unregisterContentObserver(smsObserver);
        }

        cdrStarted.set(false);
    }

    public TelephonyManagerWrapper getTelephonyManagerForSubscription(int subscriptionId)
    {
        for (TelephonyManagerWrapper wrapper : telephonyManagerList)
        {
            if (wrapper.getSubscriptionId() == subscriptionId)
            {
                return wrapper;
            }
        }
        return null;
    }
}
