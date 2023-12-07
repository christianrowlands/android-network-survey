package com.craxiom.networksurvey.services.controller;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.craxiom.networksurvey.CalculationUtils;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.logging.CdmaCsvLogger;
import com.craxiom.networksurvey.logging.CellularSurveyRecordLogger;
import com.craxiom.networksurvey.logging.GsmCsvLogger;
import com.craxiom.networksurvey.logging.LteCsvLogger;
import com.craxiom.networksurvey.logging.NrCsvLogger;
import com.craxiom.networksurvey.logging.PhoneStateRecordLogger;
import com.craxiom.networksurvey.logging.UmtsCsvLogger;
import com.craxiom.networksurvey.model.LogTypeState;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.ArrayList;
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
 */
public class CellularController extends AController
{
    private static final int PING_RATE_MS = 10_000;
    private static final int DEFAULT_SUBSCRIPTION_ID = Integer.MAX_VALUE; // AKA SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
    private final AtomicBoolean cellularScanningActive = new AtomicBoolean(false);

    private final AtomicBoolean cellularLoggingEnabled = new AtomicBoolean(false);

    private final AtomicInteger cellularScanningTaskId = new AtomicInteger();

    private final Handler serviceHandler;
    private final SurveyRecordProcessor surveyRecordProcessor;

    private volatile int cellularScanRateMs;

    private final List<TelephonyManagerWrapper> telephonyManagerList = new ArrayList<>();
    private final Map<Integer, TelephonyManager.CellInfoCallback> cellInfoCallbackMap = new HashMap<>();
    private List<SubscriptionInfo> activeSubscriptionInfoList = new ArrayList<>();
    private int simCount = 0;

    private final CellularSurveyRecordLogger cellularSurveyRecordLogger;
    private final PhoneStateRecordLogger phoneStateRecordLogger;
    private final NrCsvLogger nrCsvLogger;
    private final LteCsvLogger lteCsvLogger;
    private final UmtsCsvLogger umtsCsvLogger;
    private final CdmaCsvLogger cdmaCsvLogger;
    private final GsmCsvLogger gsmCsvLogger;
    private PhoneStateListener phoneStateListener;

    public CellularController(NetworkSurveyService surveyService, ExecutorService executorService,
                              Looper serviceLooper, Handler serviceHandler,
                              SurveyRecordProcessor surveyRecordProcessor)
    {
        super(surveyService, executorService);
        this.serviceHandler = serviceHandler;
        this.surveyRecordProcessor = surveyRecordProcessor;

        cellularSurveyRecordLogger = new CellularSurveyRecordLogger(surveyService, serviceLooper);
        phoneStateRecordLogger = new PhoneStateRecordLogger(surveyService, serviceLooper);
        nrCsvLogger = new NrCsvLogger(surveyService, serviceLooper);
        lteCsvLogger = new LteCsvLogger(surveyService, serviceLooper);
        umtsCsvLogger = new UmtsCsvLogger(surveyService, serviceLooper);
        cdmaCsvLogger = new CdmaCsvLogger(surveyService, serviceLooper);
        gsmCsvLogger = new GsmCsvLogger(surveyService, serviceLooper);
    }

    public boolean isLoggingEnabled()
    {
        return cellularLoggingEnabled.get();
    }

    public boolean isScanningActive()
    {
        return cellularScanningActive.get();
    }

    public int getScanRateMs()
    {
        return cellularScanRateMs;
    }

    public void onRolloverPreferenceChanged()
    {
        cellularSurveyRecordLogger.onSharedPreferenceChanged();
        phoneStateRecordLogger.onSharedPreferenceChanged();
        nrCsvLogger.onSharedPreferenceChanged();
        lteCsvLogger.onSharedPreferenceChanged();
        umtsCsvLogger.onSharedPreferenceChanged();
        cdmaCsvLogger.onSharedPreferenceChanged();
        gsmCsvLogger.onSharedPreferenceChanged();
    }

    /**
     * Called to indicate that an MDM preference changed, which should trigger a re-read of the
     * preferences.
     */
    public void onMdmPreferenceChanged()
    {
        cellularSurveyRecordLogger.onMdmPreferenceChanged();
        phoneStateRecordLogger.onMdmPreferenceChanged();
        nrCsvLogger.onSharedPreferenceChanged();
        lteCsvLogger.onSharedPreferenceChanged();
        umtsCsvLogger.onSharedPreferenceChanged();
        cdmaCsvLogger.onSharedPreferenceChanged();
        gsmCsvLogger.onSharedPreferenceChanged();
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
    }

    /**
     * Called to indicate that the cellular scan rate preference changed, which should trigger a
     * re-read of the preference.
     */
    public void refreshScanRate()
    {
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

            Timber.i("Toggling cellular logging to %s", enable);

            boolean successful = false;
            if (enable)
            {
                LogTypeState types = PreferenceUtils.getLogTypePreference(surveyService.getApplicationContext());

                if (types.geoPackage)
                {
                    successful = cellularSurveyRecordLogger.enableLogging(true) &&
                            phoneStateRecordLogger.enableLogging(true);
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
                    phoneStateRecordLogger.enableLogging(false);
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
                phoneStateRecordLogger.enableLogging(false);
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

    public void startPhoneStateListener()
    {

        // The onServiceStateChanged required API level 29.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            // Add a listener for the Service State information if we have access to the Telephony Manager
            final TelephonyManager telephonyManager = (TelephonyManager) surveyService.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null && surveyService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            {
                Timber.d("Adding the Telephony Manager Service State Listener");

                // Sadly we have to use the service handler for this because the PhoneStateListener constructor calls
                // Looper.myLooper(), which needs to be run from a thread where the looper is prepared. The better option
                // is to use the constructor that takes an executor service, but that is only supported in Android 10+.
                serviceHandler.post(() -> {
                    phoneStateListener = new PhoneStateListener()
                    {
                        @Override
                        public void onServiceStateChanged(ServiceState serviceState)
                        {
                            execute(() -> surveyRecordProcessor.onServiceStateChanged(serviceState, telephonyManager));
                        }

                        // We can't use this because you have to be a system app to get the READ_PRECISE_PHONE_STATE permission.
                        // So this is unused for now, but maybe at some point in the future we can make use of it.
                        /*@Override
                        public void onRegistrationFailed(@NonNull CellIdentity cellIdentity, @NonNull String chosenPlmn, int domain, int causeCode, int additionalCauseCode)
                        {
                            execute(() -> surveyRecordProcessor.onRegistrationFailed(cellIdentity, domain, causeCode, additionalCauseCode, telephonyManager));
                        }*/
                    };

                    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
                });
            }
        }
    }

    public void stopPhoneStateListener()
    {

        if (phoneStateListener != null)
        {
            final TelephonyManager telephonyManager = (TelephonyManager) surveyService.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null && surveyService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            {
                Timber.d("Removing the Telephony Manager Service State Listener");

                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
        }
    }

    /**
     * Create the Cellular Scan callback that will be notified of Cellular scan events once
     * {@link #startCellularRecordScanning()} is called.
     */
    public void initializeCellularScanningResources()
    {
        final TelephonyManager telephonyManager = (TelephonyManager) surveyService.getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager == null)
        {
            Timber.e("Unable to get access to the Telephony Manager.  No network information will be displayed");
            return;
        }

        SubscriptionManager subscriptionManager = SubscriptionManager.from(surveyService.getApplicationContext());
        if (ActivityCompat.checkSelfPermission(surveyService, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
        {
            activeSubscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
            simCount = activeSubscriptionInfoList.size();
            Timber.i("Found %s active SIMs", simCount);

            for (SubscriptionInfo subscriptionInfo : activeSubscriptionInfoList)
            {
                int subId = subscriptionInfo.getSubscriptionId();
                telephonyManagerList.add(new TelephonyManagerWrapper(telephonyManager.createForSubscriptionId(subId), subId));
            }
        } else
        {
            Timber.e("Unable to get access to the Subscription Manager. Can't get survey information from other SIMs");
            telephonyManagerList.add(new TelephonyManagerWrapper(telephonyManager, DEFAULT_SUBSCRIPTION_ID));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            for (TelephonyManagerWrapper wrapper : telephonyManagerList)
            {
                cellInfoCallbackMap.put(wrapper.getSubscriptionId(), new TelephonyManager.CellInfoCallback()
                {
                    final int subscriptionId = wrapper.getSubscriptionId();

                    @Override
                    public void onCellInfo(@NonNull List<CellInfo> cellInfo)
                    {
                        String dataNetworkType = "Unknown";
                        String voiceNetworkType = "Unknown";
                        if (ActivityCompat.checkSelfPermission(surveyService, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                        {
                            dataNetworkType = CalculationUtils.getNetworkType(wrapper.getTelephonyManager().getDataNetworkType());
                            voiceNetworkType = CalculationUtils.getNetworkType(wrapper.getTelephonyManager().getVoiceNetworkType());
                        }

                        surveyRecordProcessor.onCellInfoUpdate(cellInfo, dataNetworkType, voiceNetworkType, subscriptionId);
                    }

                    @Override
                    public void onError(int errorCode, @Nullable Throwable detail)
                    {
                        super.onError(errorCode, detail);
                        Timber.w(detail, "Received an error from the Telephony Manager when requesting a cell info update; errorCode=%s", errorCode);
                    }
                });
            }
        }
    }

    /**
     * Runs one cellular scan. This is used to prime the UI in the event that the scan interval is really long.
     */
    public void runSingleScan()
    {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                {
                    for (TelephonyManagerWrapper wrapper : telephonyManagerList)
                    {
                        TelephonyManager.CellInfoCallback callback = cellInfoCallbackMap.get(wrapper.getSubscriptionId());
                        if (callback != null)
                        {
                            wrapper.getTelephonyManager().requestCellInfoUpdate(executorService, callback);
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
                                surveyRecordProcessor.onCellInfoUpdate(subscriptionTelephonyManager.getAllCellInfo(),
                                        CalculationUtils.getNetworkType(subscriptionTelephonyManager.getDataNetworkType()),
                                        CalculationUtils.getNetworkType(subscriptionTelephonyManager.getVoiceNetworkType()),
                                        wrapper.getSubscriptionId());
                            }
                        } catch (Throwable t)
                        {
                            Timber.e(t, "Something went wrong when trying to get the cell info for a single scan");
                        }
                    });
                }
            } catch (SecurityException e)
            {
                Timber.e(e, "Could not get the required permissions to get the network details");
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
            if (cellularScanningActive.getAndSet(true)) return;

            final TelephonyManager telephonyManager = (TelephonyManager) surveyService.getSystemService(Context.TELEPHONY_SERVICE);

            if (telephonyManager == null || !surveyService.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            {
                Timber.w("Unable to get access to the Telephony Manager.  No network information will be displayed");
                return;
            }

            final int handlerTaskId = cellularScanningTaskId.incrementAndGet();

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

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        {
                            for (TelephonyManagerWrapper wrapper : telephonyManagerList)
                            {
                                TelephonyManager.CellInfoCallback callback = cellInfoCallbackMap.get(wrapper.getSubscriptionId());
                                if (callback != null)
                                {
                                    wrapper.getTelephonyManager().requestCellInfoUpdate(executorService, callback);
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
                                        surveyRecordProcessor.onCellInfoUpdate(subscriptionTelephonyManager.getAllCellInfo(),
                                                CalculationUtils.getNetworkType(subscriptionTelephonyManager.getDataNetworkType()),
                                                CalculationUtils.getNetworkType(subscriptionTelephonyManager.getVoiceNetworkType()),
                                                wrapper.getSubscriptionId());
                                    }
                                } catch (Throwable t)
                                {
                                    Timber.e(t, "Failed to pass the cellular info to the survey record processor");
                                }
                            });
                        }

                        serviceHandler.postDelayed(this, cellularScanRateMs);
                    } catch (SecurityException e)
                    {
                        Timber.e(e, "Could not get the required permissions to get the network details");
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

        surveyService.updateLocationListener();
    }

    /**
     * If any of the loggers are still active, this stops them all just to be safe. If they are not active then nothing
     * changes.
     */
    public void stopAllLogging()
    {
        if (cellularSurveyRecordLogger != null) cellularSurveyRecordLogger.enableLogging(false);
        if (phoneStateRecordLogger != null) phoneStateRecordLogger.enableLogging(false);
        if (nrCsvLogger != null) nrCsvLogger.enableLogging(false);
        if (lteCsvLogger != null) lteCsvLogger.enableLogging(false);
        if (umtsCsvLogger != null) umtsCsvLogger.enableLogging(false);
        if (cdmaCsvLogger != null) cdmaCsvLogger.enableLogging(false);
        if (gsmCsvLogger != null) gsmCsvLogger.enableLogging(false);
    }

    /**
     * @param enable Value used to set {@code cellularLoggingEnabled}, and cellular and device
     *               status listeners. If {@code true} listeners are registered, otherwise they
     *               are unregistered.
     */
    private void toggleCellularConfig(boolean enable, LogTypeState types)
    {
        cellularLoggingEnabled.set(enable);
        if (enable)
        {
            if (types != null)
            {
                if (types.geoPackage)
                {
                    surveyService.registerCellularSurveyRecordListener(cellularSurveyRecordLogger);
                    surveyService.registerDeviceStatusListener(phoneStateRecordLogger);
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
            surveyService.unregisterDeviceStatusListener(phoneStateRecordLogger);
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
            Timber.v("Ping Exit Value: %s", exitValue);
        } catch (Exception e)
        {
            Timber.e(e, "An exception occurred trying to send out a ping ");
        }
    }
}
