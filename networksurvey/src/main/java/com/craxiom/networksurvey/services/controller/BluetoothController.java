package com.craxiom.networksurvey.services.controller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.logging.BluetoothCsvLogger;
import com.craxiom.networksurvey.logging.BluetoothSurveyRecordLogger;
import com.craxiom.networksurvey.model.LogTypeState;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * Handles all of the Bluetooth related logic for Network Survey Service to include file logging
 * and managing Bluetooth scanning.
 *
 * @noinspection NonPrivateFieldAccessedInSynchronizedContext
 */
public class BluetoothController extends AController
{
    private final AtomicBoolean bluetoothScanningActive = new AtomicBoolean(false);
    private final AtomicBoolean bluetoothLoggingEnabled = new AtomicBoolean(false);
    private final AtomicInteger bluetoothScanningTaskId = new AtomicInteger();

    private final Handler serviceHandler;
    private final SurveyRecordProcessor surveyRecordProcessor;
    private final Handler uiThreadHandler;

    private final BluetoothSurveyRecordLogger bluetoothSurveyRecordLogger;
    private final BluetoothCsvLogger bluetoothCsvLogger;
    private volatile int bluetoothScanRateMs;
    private ScanCallback bluetoothScanCallback;
    private BroadcastReceiver bluetoothBroadcastReceiver;

    public BluetoothController(NetworkSurveyService surveyService, ExecutorService executorService,
                               Looper serviceLooper, Handler serviceHandler,
                               SurveyRecordProcessor surveyRecordProcessor, Handler uiThreadHandler)
    {
        super(surveyService, executorService);

        this.serviceHandler = serviceHandler;
        this.surveyRecordProcessor = surveyRecordProcessor;
        this.uiThreadHandler = uiThreadHandler;

        bluetoothSurveyRecordLogger = new BluetoothSurveyRecordLogger(surveyService, serviceLooper);
        bluetoothCsvLogger = new BluetoothCsvLogger(surveyService, serviceLooper);
    }

    @Override
    public void onDestroy()
    {
        // Sync on the bluetoothLoggingEnabled to ensure cleaning up resources (e.g. assigning null
        // to the surveyService) does not cause a NPE if logging is still being enabled or disabled.
        synchronized (bluetoothLoggingEnabled)
        {
            bluetoothSurveyRecordLogger.onDestroy();
            bluetoothCsvLogger.onDestroy();

            bluetoothBroadcastReceiver = null;
            bluetoothScanCallback = null;

            super.onDestroy();
        }
    }

    public boolean isLoggingEnabled()
    {
        return bluetoothLoggingEnabled.get();
    }

    public boolean isScanningActive()
    {
        return bluetoothScanningActive.get();
    }

    public int getScanRateMs()
    {
        return bluetoothScanRateMs;
    }

    public void onRolloverPreferenceChanged()
    {
        bluetoothSurveyRecordLogger.onSharedPreferenceChanged();
        bluetoothCsvLogger.onSharedPreferenceChanged();
    }

    /**
     * Called to indicate that an MDM preference changed, which should trigger a re-read of the
     * preferences.
     */
    public void onMdmPreferenceChanged()
    {
        bluetoothSurveyRecordLogger.onMdmPreferenceChanged();
        bluetoothCsvLogger.onMdmPreferenceChanged();
    }

    public void onLogFileTypePreferenceChanged()
    {
        synchronized (bluetoothLoggingEnabled)
        {
            if (bluetoothLoggingEnabled.get())
            {
                final boolean originalLoggingState = bluetoothLoggingEnabled.get();
                toggleLogging(false);
                toggleLogging(true);
                final boolean newLoggingState = bluetoothLoggingEnabled.get();
                if (originalLoggingState != newLoggingState)
                {
                    Timber.i("Logging state changed from %s to %s", originalLoggingState, newLoggingState);
                }
            }
        }
    }

    /**
     * Called to indicate that the BT scan rate preference changed, which should trigger a
     * re-read of the preference.
     */
    public void refreshScanRate()
    {
        if (surveyService == null) return;

        bluetoothScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS, surveyService.getApplicationContext());
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
    public Boolean toggleLogging(boolean enable)
    {
        synchronized (bluetoothLoggingEnabled)
        {
            final boolean originalLoggingState = bluetoothLoggingEnabled.get();
            if (originalLoggingState == enable) return originalLoggingState;

            if (surveyService == null) return null;

            Timber.i("Toggling Bluetooth logging to %s", enable);

            boolean successful = false;
            if (enable)
            {
                // First check to see if Bluetooth is enabled
                final boolean bluetoothEnabled = isBluetoothEnabledAndPermissionsGranted(true);
                if (!bluetoothEnabled) return null;

                LogTypeState types = PreferenceUtils.getLogTypePreference(surveyService.getApplicationContext());
                if (types.geoPackage)
                {
                    successful = bluetoothSurveyRecordLogger.enableLogging(true);
                }
                if (types.csv)
                {
                    successful = bluetoothCsvLogger.enableLogging(true);
                }

                if (successful)
                {
                    toggleBtConfig(true, types);
                } else
                {
                    // at least one of the loggers failed to toggle;
                    // disable all of them and set local config to false
                    bluetoothSurveyRecordLogger.enableLogging(false);
                    bluetoothCsvLogger.enableLogging(false);
                    toggleBtConfig(false, null);
                }
            } else
            {
                // If we are disabling logging, then we need to disable both geoPackage and CSV just
                // in case the user changed the setting after they started logging.
                bluetoothSurveyRecordLogger.enableLogging(false);
                bluetoothCsvLogger.enableLogging(false);
                toggleBtConfig(false, null);
                successful = true;
            }

            surveyService.updateServiceNotification();
            surveyService.notifyLoggingChangedListeners();

            final boolean newLoggingState = bluetoothLoggingEnabled.get();

            return successful ? newLoggingState : null;
        }
    }

    /**
     * Create the Bluetooth Scan broadcast receiver that will be notified of Bluetooth scan events once
     * {@link #startBluetoothRecordScanning()} is called.
     */
    public void initializeBtScanningResources()
    {
        // Syncing on the bluetoothLoggingEnabled to ensure that surveyService won't be assigned null while we are using it
        synchronized (bluetoothLoggingEnabled)
        {
            if (surveyService == null) return;

            final BluetoothManager bluetoothManager = (BluetoothManager) surveyService.getSystemService(Context.BLUETOOTH_SERVICE);

            if (bluetoothManager == null)
            {
                Timber.e("The BluetoothManager is null. Bluetooth survey won't work");
                return;
            }

            final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter == null)
            {
                Timber.e("The BluetoothAdapter is null. Bluetooth survey won't work");
                return;
            }

            bluetoothBroadcastReceiver = new BroadcastReceiver()
            {
                @Override
                public void onReceive(Context context, Intent intent)
                {
                    if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction()))
                    {
                        final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (device == null)
                        {
                            Timber.e("Received a null BluetoothDevice in the broadcast action found call");
                            return;
                        }

                        int rssi = Short.MIN_VALUE;
                        if (intent.hasExtra(BluetoothDevice.EXTRA_RSSI))
                        {
                            rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                        }

                        if (rssi == Short.MIN_VALUE) return;

                        surveyRecordProcessor.onBluetoothClassicScanUpdate(device, rssi);
                    }
                }
            };

            bluetoothScanCallback = new ScanCallback()
            {
                @Override
                public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result)
                {
                    surveyRecordProcessor.onBluetoothScanUpdate(result);
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results)
                {
                    surveyRecordProcessor.onBluetoothScanUpdate(results);
                }

                @Override
                public void onScanFailed(int errorCode)
                {
                    if (errorCode == SCAN_FAILED_ALREADY_STARTED)
                    {
                        Timber.i("Bluetooth scan already started, so this scan failed");
                    } else
                    {
                        Timber.e("A Bluetooth scan failed, ignoring the results.");
                    }
                }
            };
        }
    }

    /**
     * Note that the {@link Manifest.permission#BLUETOOTH_SCAN} permission was added in Android 12, so this method
     * returns true for all older versions.
     *
     * @return True if the {@link Manifest.permission#BLUETOOTH_SCAN} permission has been granted. False otherwise.
     */
    private boolean hasBtScanPermission()
    {
        // The BLUETOOTH_SCAN permission was added in Android 12
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;

        if (ActivityCompat.checkSelfPermission(surveyService, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The BLUETOOTH_SCAN permission has not been granted");
            return false;
        }

        return true;
    }

    /**
     * Note that the {@link Manifest.permission#BLUETOOTH_CONNECT} permission was added in Android 12, so this method
     * returns true for all older versions.
     * <p>
     * The {@link Manifest.permission#BLUETOOTH_CONNECT} permission is required ONLY for getting the bluetooth device
     * name, so don't fail BT survey if it is missing, but we probably want to notify the user.
     *
     * @return True if the {@link Manifest.permission#BLUETOOTH_CONNECT} permission has been granted. False otherwise.
     */
    private boolean hasBtConnectPermission()
    {
        // The BLUETOOTH_CONNECT permission was added in Android 12
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;

        if (ActivityCompat.checkSelfPermission(surveyService, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The BLUETOOTH_CONNECT permission has not been granted");
            return false;
        }

        return true;
    }

    /**
     * Register a listener for Bluetooth scans, and then kick off a scheduled Bluetooth scan.
     * <p>
     * This method only starts scanning if the scan is not already active and we have the required permissions.
     */
    // We should not be able to get here without the permissions already granted, but we also check at the beginning of the method
    @SuppressLint("MissingPermission")
    public void startBluetoothRecordScanning()
    {
        synchronized (bluetoothLoggingEnabled)
        {
            if (surveyService == null) return;

            if (!hasBtScanPermission())
            {
                uiThreadHandler.post(() -> Toast.makeText(surveyService.getApplicationContext(), surveyService.getString(R.string.grant_bluetooth_scan_permission), Toast.LENGTH_LONG).show());
                return;
            }

            if (!hasBtConnectPermission())
            {
                uiThreadHandler.post(() -> Toast.makeText(surveyService.getApplicationContext(), surveyService.getString(R.string.grant_bluetooth_connect_permission), Toast.LENGTH_LONG).show());
                return;
            }

            if (bluetoothScanningActive.getAndSet(true)) return;

            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null)
            {
                Timber.e("The BluetoothAdapter is null. Bluetooth survey won't work");
                bluetoothScanningActive.set(false);
                return;
            }

            final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null)
            {
                Timber.e("The BluetoothLeScanner is null, unable to perform Bluetooth LE scans.");
                bluetoothScanningActive.set(false);
                return;
            }

            final IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            surveyService.registerReceiver(bluetoothBroadcastReceiver, intentFilter);

            final ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
            scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
            scanSettingsBuilder.setReportDelay(bluetoothScanRateMs);
            bluetoothLeScanner.startScan(Collections.emptyList(), scanSettingsBuilder.build(), bluetoothScanCallback);

            final int handlerTaskId = bluetoothScanningTaskId.incrementAndGet();

            serviceHandler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        if (!bluetoothScanningActive.get() || bluetoothScanningTaskId.get() != handlerTaskId)
                        {
                            Timber.i("Stopping the handler that pulls the latest Bluetooth information; taskId=%d", handlerTaskId);
                            return;
                        }

                        // Calling start Discovery scans for BT Classic (BR/EDR) devices as well. However, it also seems
                        // it allows for getting some BLE devices as well, but we seem to get more with the BLE scanner above
                        if (!bluetoothAdapter.isDiscovering())
                        {
                            bluetoothAdapter.startDiscovery();
                        } else
                        {
                            Timber.d("Bluetooth discovery already in progress, not starting a new discovery.");
                        }

                        serviceHandler.postDelayed(this, bluetoothScanRateMs);
                    } catch (Exception e)
                    {
                        Timber.e(e, "Could not run a Bluetooth scan");
                    }
                }
            }, 1_000);

            surveyService.updateLocationListener();
        }
    }

    /**
     * Unregister the Bluetooth scan callback and stop the scanning service handler.
     */
    @SuppressLint("MissingPermission") // Permissions are checked in the first part of the method
    public void stopBluetoothRecordScanning()
    {
        synchronized (bluetoothLoggingEnabled)
        {
            if (surveyService == null) return;

            bluetoothScanningActive.set(false);

            if (!hasBtConnectPermission() || !hasBtScanPermission())
            {
                Timber.i("Missing a Bluetooth permission, can't stop BT scanning");
                return;
            }

            try
            {
                final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter != null)
                {
                    bluetoothAdapter.cancelDiscovery();

                    final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    if (bluetoothLeScanner != null)
                    {
                        bluetoothLeScanner.stopScan(bluetoothScanCallback);
                    }
                }
                surveyService.unregisterReceiver(bluetoothBroadcastReceiver);
            } catch (Exception e)
            {
                Timber.v(e, "Could not stop the Bluetooth Scan Callback");
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

    private void toggleBtConfig(boolean enable, LogTypeState types)
    {
        if (surveyService == null) return;

        bluetoothLoggingEnabled.set(enable);
        if (enable)
        {
            if (types != null)
            {
                if (types.geoPackage)
                {
                    surveyService.registerBluetoothSurveyRecordListener(bluetoothSurveyRecordLogger);
                }
                if (types.csv)
                {
                    surveyService.registerBluetoothSurveyRecordListener(bluetoothCsvLogger);
                }
            } else
            {
                throw new IllegalArgumentException("LogTypeState cannot be null when enabling Bluetooth logging");
            }
        } else
        {
            surveyService.unregisterBluetoothSurveyRecordListener(bluetoothSurveyRecordLogger);
            surveyService.unregisterBluetoothSurveyRecordListener(bluetoothCsvLogger);
        }
    }

    /**
     * Checks to see if the Bluetooth adapter is present, if Bluetooth is enabled, and if the
     * proper permissions are granted.
     * <p>
     * After the check to see if Bluetooth is enabled, if Bluetooth is currently disabled and {@code promptEnable} is
     * true, the user is then prompted to turn on Bluetooth.  Even if the user turns on Bluetooth, this method will
     * still return false since the call to enable Bluetooth is asynchronous.
     *
     * @param promptEnable If true, and Bluetooth is currently disabled, the user will be presented with a UI to turn on
     *                     Bluetooth.
     * @return True if Bluetooth is enabled, false if it is not. Also returns false if the
     * permissions are not granted.
     */
    @SuppressLint("MissingPermission")
    // Permissions are checked in the first part of this method call
    private boolean isBluetoothEnabledAndPermissionsGranted(boolean promptEnable)
    {
        if (surveyService == null) return false;

        if (!hasBtConnectPermission() || !hasBtScanPermission())
        {
            Timber.i("Missing a Bluetooth permission, can't enable BT scanning");
            return false;
        }

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) return false;

        boolean isEnabled = true;

        if (!bluetoothAdapter.isEnabled())
        {
            isEnabled = false;

            if (promptEnable)
            {
                Timber.i("Bluetooth is disabled, prompting the user to enable it");

                uiThreadHandler.post(() -> Toast.makeText(surveyService.getApplicationContext(), surveyService.getString(R.string.turn_on_bluetooth), Toast.LENGTH_SHORT).show());
                serviceHandler.post(() -> {
                    try
                    {
                        final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        surveyService.startActivity(enableBtIntent);
                    } catch (Exception e)
                    {
                        // An IllegalStateException can occur when the fragment is no longer attached to the activity
                        Timber.e(e, "Could not kick off the Bluetooth Enable Intent");
                    }
                });
            }
        }

        return isEnabled;
    }
}
