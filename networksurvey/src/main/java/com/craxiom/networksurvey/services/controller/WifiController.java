package com.craxiom.networksurvey.services.controller;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.logging.WifiCsvLogger;
import com.craxiom.networksurvey.logging.WifiSurveyRecordLogger;
import com.craxiom.networksurvey.model.LogTypeState;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * Handles all of the Wi-Fi related logic for Network Survey Service to include file logging
 * and managing the Wi-Fi scanning.
 *
 * @noinspection NonPrivateFieldAccessedInSynchronizedContext
 */
public class WifiController extends AController
{
    private final AtomicBoolean wifiScanningActive = new AtomicBoolean(false);
    private final AtomicBoolean wifiLoggingEnabled = new AtomicBoolean(false);
    private final AtomicInteger wifiScanningTaskId = new AtomicInteger();

    private final Handler serviceHandler;
    private final SurveyRecordProcessor surveyRecordProcessor;
    private final Handler uiThreadHandler;

    private volatile int wifiScanRateMs;
    private final WifiSurveyRecordLogger wifiSurveyRecordLogger;
    private final WifiCsvLogger wifiCsvLogger;
    private BroadcastReceiver wifiScanReceiver;

    public WifiController(NetworkSurveyService surveyService, ExecutorService executorService,
                          Looper serviceLooper, Handler serviceHandler,
                          SurveyRecordProcessor surveyRecordProcessor, Handler uiThreadHandler)
    {
        super(surveyService, executorService);

        this.serviceHandler = serviceHandler;
        this.surveyRecordProcessor = surveyRecordProcessor;
        this.uiThreadHandler = uiThreadHandler;

        wifiSurveyRecordLogger = new WifiSurveyRecordLogger(surveyService, serviceLooper);
        wifiCsvLogger = new WifiCsvLogger(surveyService, serviceLooper);
    }

    @Override
    public void onDestroy()
    {
        // Sync on the wifiLoggingEnabled to ensure cleaning up resources (e.g. assigning null
        // to the surveyService) does not cause a NPE if logging is still being enabled or disabled.
        synchronized (wifiLoggingEnabled)
        {
            wifiSurveyRecordLogger.onDestroy();
            wifiCsvLogger.onDestroy();
            super.onDestroy();
        }
    }

    public boolean isLoggingEnabled()
    {
        return wifiLoggingEnabled.get();
    }

    public boolean isScanningActive()
    {
        return wifiScanningActive.get();
    }

    public int getScanRateMs()
    {
        return wifiScanRateMs;
    }

    public void onRolloverPreferenceChanged()
    {
        wifiSurveyRecordLogger.onSharedPreferenceChanged();
        wifiCsvLogger.onSharedPreferenceChanged();
    }

    /**
     * Called to indicate that an MDM preference changed, which should trigger a re-read of the
     * preferences.
     */
    public void onMdmPreferenceChanged()
    {
        wifiSurveyRecordLogger.onMdmPreferenceChanged();
        wifiCsvLogger.onMdmPreferenceChanged();
    }

    public void onLogFileTypePreferenceChanged()
    {
        synchronized (wifiLoggingEnabled)
        {
            if (wifiLoggingEnabled.get())
            {
                final boolean originalLoggingState = wifiLoggingEnabled.get();
                toggleLogging(false);
                toggleLogging(true);
                final boolean newLoggingState = wifiLoggingEnabled.get();
                if (originalLoggingState != newLoggingState)
                {
                    Timber.i("Logging state changed from %s to %s", originalLoggingState, newLoggingState);
                }
            }
        }
    }

    /**
     * Called to indicate that the Wi-Fi scan rate preference changed, which should trigger a
     * re-read of the preference.
     */
    public void refreshScanRate()
    {
        if (surveyService == null) return;

        wifiScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS, surveyService.getApplicationContext());
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
    public Boolean toggleLogging(boolean enable)
    {
        if (surveyService == null) return null;

        synchronized (wifiLoggingEnabled)
        {
            final boolean originalLoggingState = wifiLoggingEnabled.get();
            if (originalLoggingState == enable) return originalLoggingState;

            Timber.i("Toggling wifi logging to %s", enable);

            boolean successful = false;
            if (enable)
            {
                // First check to see if Wi-Fi is enabled
                final boolean wifiEnabled = isWifiEnabled(true);
                if (!wifiEnabled) return null;

                LogTypeState types = PreferenceUtils.getLogTypePreference(surveyService.getApplicationContext());
                if (types.geoPackage)
                {
                    successful = wifiSurveyRecordLogger.enableLogging(true);
                }
                if (types.csv)
                {
                    successful = wifiCsvLogger.enableLogging(true);
                }

                if (successful)
                {
                    toggleWifiConfig(true, types);
                } else
                {
                    // at least one of the loggers failed to toggle;
                    // disable all of them and set local config to false
                    wifiSurveyRecordLogger.enableLogging(false);
                    wifiCsvLogger.enableLogging(false);
                    toggleWifiConfig(false, null);
                }
            } else
            {
                // If we are disabling logging, then we need to disable both geoPackage and CSV just
                // in case the user changed the setting after they started logging.
                wifiSurveyRecordLogger.enableLogging(false);
                wifiCsvLogger.enableLogging(false);
                toggleWifiConfig(false, null);
                successful = true;
            }

            surveyService.updateServiceNotification();
            surveyService.notifyLoggingChangedListeners();

            final boolean newLoggingState = wifiLoggingEnabled.get();

            return successful ? newLoggingState : null;
        }
    }

    /**
     * Create the Wi-Fi Scan broadcast receiver that will be notified of Wi-Fi scan events once
     * {@link #startWifiRecordScanning()} is called.
     */
    public void initializeWifiScanningResources()
    {
        // Syncing on the wifiLoggingEnabled to ensure that surveyService won't be assigned null while we are using it
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return;

            final WifiManager wifiManager = (WifiManager) surveyService.getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null)
            {
                Timber.e("The WifiManager is null. Wi-Fi survey won't work");
                return;
            }

            wifiScanReceiver = new BroadcastReceiver()
            {
                @Override
                public void onReceive(Context c, Intent intent)
                {
                    boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                    if (success)
                    {
                        synchronized (wifiLoggingEnabled)
                        {
                            if (surveyService == null) return;

                            if (ActivityCompat.checkSelfPermission(surveyService.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                                    != PackageManager.PERMISSION_GRANTED)
                            {
                                Timber.e("Could not get the Wi-FI scan results because the ACCESS_FINE_LOCATION permission is not granted.");
                                return;
                            }
                            final List<ScanResult> results = wifiManager.getScanResults();
                            if (results == null)
                            {
                                Timber.d("Null wifi scan results");
                                return;
                            }

                            surveyRecordProcessor.onWifiScanUpdate(results);
                        }
                    } else
                    {
                        Timber.e("A Wi-Fi scan failed, ignoring the results.");
                    }
                }
            };
        }
    }

    /**
     * Register a listener for Wi-Fi scans, and then kick off a scheduled Wi-Fi scan.
     * <p>
     * This method only starts scanning if the scan is not already active.
     */
    public void startWifiRecordScanning()
    {
        // Using wifiLoggingEnabled as the lock object because it is also used in the toggleLogging method
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return;

            if (wifiScanningActive.getAndSet(true)) return;

            final IntentFilter scanResultsIntentFilter = new IntentFilter();
            scanResultsIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            surveyService.registerReceiver(wifiScanReceiver, scanResultsIntentFilter);

            final WifiManager wifiManager = (WifiManager) surveyService.getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null)
            {
                Timber.wtf("The Wi-Fi manager is null, can't start scanning for Wi-Fi networks.");
                wifiScanningActive.set(false);
                return;
            }

            final int handlerTaskId = wifiScanningTaskId.incrementAndGet();

            serviceHandler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        if (!wifiScanningActive.get() || wifiScanningTaskId.get() != handlerTaskId)
                        {
                            Timber.i("Stopping the handler that pulls the latest wifi information, taskId=%d", handlerTaskId);
                            return;
                        }

                        boolean success = wifiManager.startScan();

                        if (!success) Timber.e("Kicking off a Wi-Fi scan failed");

                        serviceHandler.postDelayed(this, wifiScanRateMs);
                    } catch (Exception e)
                    {
                        Timber.e(e, "Could not run a Wi-Fi scan");
                    }
                }
            }, 2_000);

            surveyService.updateLocationListener();
        }
    }

    /**
     * Unregister the Wi-Fi scan broadcast receiver and stop the scanning service handler.
     */
    public void stopWifiRecordScanning()
    {
        // Using wifiLoggingEnabled as the lock object because it is also used in the toggleLogging method
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return;

            wifiScanningActive.set(false);

            try
            {
                surveyService.unregisterReceiver(wifiScanReceiver);
            } catch (Exception e)
            {
                // Because we are extra cautious and want to make sure that we unregister the receiver, when the service
                // is shutdown we call this method to make sure we stop any active scan and unregister the receiver even if
                // we don't have one registered.
                Timber.v(e, "Could not unregister the NetworkSurveyService Wi-Fi Scan Receiver");
            }

            surveyService.updateLocationListener();
        }
    }

    /**
     * If any of the loggers are still active, this stops them all just to be safe. If they are not active then nothing
     * changes.
     */
    public void stopAllLogging()
    {
        toggleLogging(false);
    }

    private void toggleWifiConfig(boolean enable, LogTypeState types)
    {
        if (surveyService == null) return;

        wifiLoggingEnabled.set(enable);
        if (enable)
        {
            if (types != null)
            {
                if (types.geoPackage)
                {
                    surveyService.registerWifiSurveyRecordListener(wifiSurveyRecordLogger);
                }
                if (types.csv)
                {
                    surveyService.registerWifiSurveyRecordListener(wifiCsvLogger);
                }
            } else
            {
                throw new IllegalArgumentException("LogTypeState cannot be null when enabling wifi logging");
            }
        } else
        {
            surveyService.unregisterWifiSurveyRecordListener(wifiSurveyRecordLogger);
            surveyService.unregisterWifiSurveyRecordListener(wifiCsvLogger);
        }
    }

    /**
     * Checks to see if the Wi-Fi manager is present, and if Wi-Fi is enabled.
     * <p>
     * After the check to see if Wi-Fi is enabled, if Wi-Fi is currently disabled and {@code promptEnable} is true, the
     * user is then prompted to turn on Wi-Fi.  Even if the user turns on Wi-Fi, this method will still return false
     * since the call to enable Wi-Fi is asynchronous.
     *
     * @param promptEnable If true, and Wi-Fi is currently disabled, the user will be presented with a UI to turn on Wi-Fi.
     * @return True if Wi-Fi is enabled, false if it is not.
     */
    private boolean isWifiEnabled(boolean promptEnable)
    {
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return false;

            boolean isEnabled = true;

            final WifiManager wifiManager = (WifiManager) surveyService.getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null) isEnabled = false;

            if (wifiManager != null && !wifiManager.isWifiEnabled())
            {
                isEnabled = false;

                if (promptEnable)
                {
                    Timber.i("Wi-Fi is disabled, prompting the user to enable it");

                    if (Build.VERSION.SDK_INT >= 29)
                    {
                        final Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                        panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        surveyService.startActivity(panelIntent);
                    } else
                    {
                        // Open the Wi-Fi setting pages after a couple seconds
                        uiThreadHandler.post(() -> Toast.makeText(surveyService.getApplicationContext(), surveyService.getString(R.string.turn_on_wifi), Toast.LENGTH_SHORT).show());
                        serviceHandler.postDelayed(() -> {
                            try
                            {
                                final Intent wifiSettingIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                                wifiSettingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                surveyService.startActivity(wifiSettingIntent);
                            } catch (Exception e)
                            {
                                // An IllegalStateException can occur when the fragment is no longer attached to the activity
                                Timber.e(e, "Could not kick off the Wifi Settings Intent for the older pre Android 10 setup");
                            }
                        }, 2000);
                    }
                }
            }

            return isEnabled;
        }
    }
}
