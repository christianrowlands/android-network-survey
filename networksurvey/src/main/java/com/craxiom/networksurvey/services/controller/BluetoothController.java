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
import com.craxiom.networksurvey.util.NsCrashReporter;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * Handles all of the Bluetooth related logic for Network Survey Service to include file logging
 * and managing Bluetooth scanning.
 * <p>
 * The Bluetooth scanning process alternates between BLE scanning (10 seconds) and Classic
 * Bluetooth discovery (12 seconds, plus a 1 second buffer so the inquiry reaches its natural
 * end), with a minimum wait time of 1 second between cycles. This results in an implicit
 * minimum scan interval of 24 seconds that cannot be overridden, even if users set a lower
 * value in preferences.
 *
 * @noinspection NonPrivateFieldAccessedInSynchronizedContext
 */
public class BluetoothController extends AController
{
    private static final long BLE_SCAN_DURATION_MS = 10000; // 10 seconds
    private static final long CLASSIC_SCAN_DURATION_MS = 12000; // 12 seconds (Android default)
    /**
     * Grace period added to the Classic inquiry so that it reaches its natural completion instead
     * of being cut short by the phase advancing. This has to be included in the scan cycle
     * arithmetic in {@link #waitForNextInterval()} or every cycle drifts a second long.
     */
    private static final long CLASSIC_DISCOVERY_BUFFER_MS = 1000; // 1 second
    /**
     * How many consecutive failed Classic discovery starts to tolerate before reporting a
     * non-fatal. Chosen so a single transient refusal stays quiet while a genuinely dead Classic
     * leg gets surfaced within about a minute and a half of scanning.
     */
    private static final int CLASSIC_DISCOVERY_FAILURE_REPORT_THRESHOLD = 3;
    /**
     * How many consecutive scan cycles may find an inquiry already in progress before the adapter
     * is treated as wedged. This is deliberately far higher than
     * {@link #CLASSIC_DISCOVERY_FAILURE_REPORT_THRESHOLD} because an overlapping inquiry is a
     * normal condition rather than a failure: Android delivers {@link BluetoothDevice#ACTION_FOUND}
     * to every registered receiver, so another app's discovery still feeds this survey. At the
     * default scan rate this works out to roughly ten minutes of uninterrupted overlap.
     */
    private static final int CLASSIC_INQUIRY_OVERLAP_REPORT_THRESHOLD = 20;
    /**
     * Reason text used when {@link BluetoothAdapter#startDiscovery()} refuses to start an inquiry.
     */
    private static final String REASON_START_DISCOVERY_REFUSED = "startDiscovery() returned false";
    private static final long DUPLICATE_WINDOW_MS = 30000; // 30 seconds
    private static final int RSSI_CHANGE_THRESHOLD = 10; // dBm
    private static final int MAX_RECENT_DEVICES = 2000; // Maximum devices to track
    private static final long CLEANUP_INTERVAL_MS = 60000; // Cleanup every 60 seconds

    private enum ScanPhase
    {
        IDLE,
        BLE_SCANNING,
        CLASSIC_SCANNING,
        WAITING_NEXT_INTERVAL
    }

    private final AtomicBoolean bluetoothScanningActive = new AtomicBoolean(false);
    private final AtomicBoolean bluetoothLoggingEnabled = new AtomicBoolean(false);
    private final ScheduledThreadPoolExecutor bluetoothScanExecutor = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> bluetoothScanFuture;
    private volatile ScanPhase currentScanPhase = ScanPhase.IDLE;
    private final AtomicInteger consecutiveClassicDiscoveryFailures = new AtomicInteger(0);
    private final AtomicBoolean classicDiscoveryFailureReported = new AtomicBoolean(false);
    private final AtomicInteger consecutiveClassicInquiryOverlaps = new AtomicInteger(0);
    private final AtomicBoolean classicInquiryOverlapReported = new AtomicBoolean(false);
    private final AtomicInteger classicDevicesFoundThisCycle = new AtomicInteger(0);
    /**
     * Set by the broadcast receiver whenever the Bluetooth stack reports inquiry activity, no
     * matter which app started the inquiry. It is what tells a wedged adapter apart from another
     * app simply holding a long running discovery, because only the former leaves this false for
     * the whole of an overlap streak.
     */
    private volatile boolean sawClassicDiscoveryActivity = false;
    private final Map<String, DeviceInfo> recentDevices = new HashMap<>();
    private long lastCleanupTime = 0;

    private record DeviceInfo(long lastSeenTime, int lastRssi)
    {
    }

    private final Handler serviceHandler;
    private final SurveyRecordProcessor surveyRecordProcessor;
    private final Handler uiThreadHandler;

    private final BluetoothSurveyRecordLogger bluetoothSurveyRecordLogger;
    private final BluetoothCsvLogger bluetoothCsvLogger;
    private volatile int bluetoothScanRateMs;
    private BleScanCallbackImpl bluetoothScanCallback;
    private BluetoothBroadcastReceiverImpl bluetoothBroadcastReceiver;

    public BluetoothController(NetworkSurveyService surveyService, ExecutorService executorService,
                               Looper serviceLooper, Handler serviceHandler,
                               SurveyRecordProcessor surveyRecordProcessor, Handler uiThreadHandler)
    {
        super(surveyService, executorService);

        this.serviceHandler = serviceHandler;
        this.surveyRecordProcessor = surveyRecordProcessor;
        this.uiThreadHandler = uiThreadHandler;

        bluetoothSurveyRecordLogger = new BluetoothSurveyRecordLogger(surveyService, serviceLooper);
        bluetoothCsvLogger = new BluetoothCsvLogger(surveyService);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onDestroy()
    {
        // Sync on the bluetoothLoggingEnabled to ensure cleaning up resources (e.g. assigning null
        // to the surveyService) does not cause a NPE if logging is still being enabled or disabled.
        synchronized (bluetoothLoggingEnabled)
        {
            bluetoothSurveyRecordLogger.onDestroy();
            bluetoothCsvLogger.onDestroy();

            // Cancel any pending tasks
            if (bluetoothScanFuture != null)
            {
                bluetoothScanFuture.cancel(true);
                bluetoothScanFuture = null;
            }

            bluetoothScanExecutor.shutdown();
            try
            {
                if (!bluetoothScanExecutor.awaitTermination(1, TimeUnit.SECONDS))
                {
                    bluetoothScanExecutor.shutdownNow();
                }
            } catch (InterruptedException e)
            {
                bluetoothScanExecutor.shutdownNow();
            }

            try
            {
                stopBleScanning();
            } catch (Exception e)
            {
                Timber.w(e, "Error stopping BLE scanner in onDestroy");
            }

            // Clear references in the callback objects to break the reference chain
            // Even if Android Framework binder stubs retain these callbacks, they won't
            // hold strong references to the controller/service after this
            if (bluetoothBroadcastReceiver != null)
            {
                bluetoothBroadcastReceiver.onDestroy();
            }
            if (bluetoothScanCallback != null)
            {
                bluetoothScanCallback.onDestroy();
            }

            bluetoothBroadcastReceiver = null;
            bluetoothScanCallback = null;

            // Clear recent devices to free memory
            synchronized (recentDevices)
            {
                recentDevices.clear();
            }

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

            bluetoothBroadcastReceiver = new BluetoothBroadcastReceiverImpl(this);

            bluetoothScanCallback = new BleScanCallbackImpl(this);
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

            resetClassicDiscoveryHealth();

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

            // Cancel any existing Bluetooth scan task
            if (bluetoothScanFuture != null)
            {
                bluetoothScanFuture.cancel(true);
                bluetoothScanFuture = null;
            }

            // Start the alternating scan cycle
            startNextScanCycle();

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

            // Cancel any scheduled tasks
            if (bluetoothScanFuture != null)
            {
                bluetoothScanFuture.cancel(true);
                bluetoothScanFuture = null;
                Timber.d("Cancelled Bluetooth scanning task");
            }

            // Stop based on current phase
            switch (currentScanPhase)
            {
                case BLE_SCANNING:
                    stopBleScanning();
                    break;
                case CLASSIC_SCANNING:
                    if (hasBtScanPermission())
                    {
                        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                        if (bluetoothAdapter != null)
                        {
                            bluetoothAdapter.cancelDiscovery();
                            Timber.d("Cancelled Classic discovery");
                        }
                    }
                    break;
                default:
                    // Nothing to stop
                    break;
            }

            currentScanPhase = ScanPhase.IDLE;

            try
            {
                surveyService.unregisterReceiver(bluetoothBroadcastReceiver);
            } catch (Exception e)
            {
                // Ignore cleanup errors
                // Timber.v(e, "Could not unregister the Bluetooth receiver");
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
        NetworkSurveyService surveyServiceLocal = surveyService;
        if (surveyServiceLocal == null) return false;

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

                uiThreadHandler.post(() -> Toast.makeText(surveyServiceLocal.getApplicationContext(), surveyServiceLocal.getString(R.string.turn_on_bluetooth), Toast.LENGTH_SHORT).show());
                serviceHandler.post(() -> {
                    try
                    {
                        final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        surveyServiceLocal.startActivity(enableBtIntent);
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

    /**
     * Determines if a device should be logged based on duplicate filtering.
     * A device is logged if:
     * - It's a new device
     * - It was last seen more than DUPLICATE_WINDOW_MS ago
     * - Its RSSI changed significantly (more than RSSI_CHANGE_THRESHOLD)
     *
     * @param device The Bluetooth device
     * @param rssi   The current RSSI value
     * @return true if the device should be logged, false otherwise
     */
    private boolean shouldLogDevice(BluetoothDevice device, int rssi)
    {
        if (device == null || device.getAddress() == null) return false;

        String address = device.getAddress();
        long currentTime = System.currentTimeMillis();

        synchronized (recentDevices)
        {
            DeviceInfo oldInfo = recentDevices.get(address);

            if (oldInfo == null)
            {
                // New device - log it
                recentDevices.put(address, new DeviceInfo(currentTime, rssi));
                // Trigger cleanup if needed
                cleanupRecentDevicesIfNeeded(currentTime);
                return true;
            }

            // Check if enough time has passed or RSSI changed significantly
            long timeSinceLastSeen = currentTime - oldInfo.lastSeenTime;
            int rssiDiff = Math.abs(rssi - oldInfo.lastRssi);
            boolean shouldLog = (timeSinceLastSeen > DUPLICATE_WINDOW_MS) ||
                    (rssiDiff > RSSI_CHANGE_THRESHOLD);

            if (shouldLog)
            {
                recentDevices.put(address, new DeviceInfo(currentTime, rssi));
            }

            // Trigger cleanup if needed
            cleanupRecentDevicesIfNeeded(currentTime);

            return shouldLog;
        }
    }

    /**
     * Cleans up old device entries from the recentDevices map to prevent memory growth.
     * This method should be called with the recentDevices lock held.
     *
     * @param currentTime The current system time in milliseconds.
     */
    private void cleanupRecentDevicesIfNeeded(long currentTime)
    {
        // Only run cleanup if it's been more than CLEANUP_INTERVAL_MS since last cleanup
        // or if we're over the max size
        if (currentTime - lastCleanupTime < CLEANUP_INTERVAL_MS &&
                recentDevices.size() <= MAX_RECENT_DEVICES)
        {
            return;
        }

        lastCleanupTime = currentTime;

        // Remove entries older than DUPLICATE_WINDOW_MS
        recentDevices.entrySet().removeIf(entry ->
                currentTime - entry.getValue().lastSeenTime > DUPLICATE_WINDOW_MS);

        // If still over max size, remove oldest entries
        if (recentDevices.size() > MAX_RECENT_DEVICES)
        {
            // Sort by lastSeenTime and remove oldest entries
            List<Map.Entry<String, DeviceInfo>> entries = new ArrayList<>(recentDevices.entrySet());
            entries.sort(Comparator.comparingLong(a -> a.getValue().lastSeenTime));

            int toRemove = entries.size() - MAX_RECENT_DEVICES;
            for (int i = 0; i < toRemove; i++)
            {
                recentDevices.remove(entries.get(i).getKey());
            }
            Timber.d("Cleaned up %d old Bluetooth device entries, %d remaining", toRemove, recentDevices.size());
        }
    }

    /**
     * Starts the next scan cycle based on the current phase.
     * The scan cycle goes: BLE -> Classic -> Wait -> Repeat
     */
    private void startNextScanCycle()
    {
        if (!bluetoothScanningActive.get())
        {
            Timber.d("Bluetooth scanning is inactive, stopping scan cycle");
            return;
        }

        // Check if scanning is paused for battery management
        if (isPaused())
        {
            Timber.d("Bluetooth scanning is paused for battery management");
            // Schedule next check after a delay
            bluetoothScanFuture = bluetoothScanExecutor.schedule(this::startNextScanCycle,
                    bluetoothScanRateMs, TimeUnit.MILLISECONDS);
            return;
        }

        synchronized (bluetoothLoggingEnabled)
        {
            if (surveyService == null) return;

            switch (currentScanPhase)
            {
                case IDLE:
                case WAITING_NEXT_INTERVAL:
                    // Start with BLE scanning
                    startBleScanning();
                    break;

                case BLE_SCANNING:
                    // Move to Classic scanning
                    stopBleScanning();
                    startClassicDiscovery();
                    break;

                case CLASSIC_SCANNING:
                    // Classic discovery auto-stops, move to waiting
                    waitForNextInterval();
                    break;
            }
        }
    }

    /**
     * Starts BLE scanning for a limited duration.
     */
    @SuppressLint("MissingPermission")
    private void startBleScanning()
    {
        if (!hasBtScanPermission())
        {
            Timber.w("Missing Bluetooth scan permission, cannot start BLE scanning");
            waitForNextInterval();
            return;
        }

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null)
        {
            Timber.e("BluetoothAdapter is null, cannot start BLE scanning");
            waitForNextInterval();
            return;
        }

        final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null)
        {
            Timber.e("BluetoothLeScanner is null, cannot start BLE scanning");
            waitForNextInterval();
            return;
        }

        currentScanPhase = ScanPhase.BLE_SCANNING;

        final ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
        scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);

        // Always use immediate reporting to ensure we get advertisement data
        // Batch scanning with report delay equal to scan duration was causing
        // results to be lost when stopScan() was called
        scanSettingsBuilder.setReportDelay(0);

        bluetoothLeScanner.startScan(Collections.emptyList(), scanSettingsBuilder.build(), bluetoothScanCallback);

        // Schedule BLE scan stop after duration
        bluetoothScanFuture = bluetoothScanExecutor.schedule(this::startNextScanCycle,
                BLE_SCAN_DURATION_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops BLE scanning.
     */
    @SuppressLint("MissingPermission")
    private void stopBleScanning()
    {
        if (!hasBtScanPermission())
        {
            Timber.w("Missing Bluetooth scan permission, cannot stop BLE scanning");
            return;
        }

        if (bluetoothScanCallback == null)
        {
            Timber.w("BLE scan callback is null, cannot stop BLE scanning");
            return;
        }

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null)
        {
            final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner != null)
            {
                bluetoothLeScanner.stopScan(bluetoothScanCallback);
                Timber.d("Stopped BLE scanning");
            }
        }
    }

    /**
     * Starts Classic Bluetooth discovery.
     */
    @SuppressLint("MissingPermission")
    private void startClassicDiscovery()
    {
        if (!hasBtScanPermission())
        {
            Timber.w("Missing Bluetooth scan permission, cannot start Classic discovery");
            waitForNextInterval();
            return;
        }

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null)
        {
            Timber.e("BluetoothAdapter is null, cannot start Classic discovery");
            waitForNextInterval();
            return;
        }

        classicDevicesFoundThisCycle.set(0);

        if (bluetoothAdapter.isDiscovering())
        {
            // Either our own previous inquiry is still running, since the name resolution tail that
            // follows an inquiry is unbounded, or another app is discovering. Skipping is the right
            // move, and Classic records keep flowing either way because ACTION_FOUND goes to every
            // registered receiver rather than only to whoever started the inquiry.
            recordClassicInquiryOverlap();
        } else
        {
            // startDiscovery() returns false when the inquiry is refused. Discarding that result
            // would make a dead Classic leg indistinguishable from a quiet RF environment, because
            // BLE results keep flowing either way.
            if (!bluetoothAdapter.startDiscovery())
            {
                recordClassicDiscoveryNotStarted(REASON_START_DISCOVERY_REFUSED);

                // Only the BLE phase ran this cycle, so only its duration is charged against the
                // configured interval. Subtracting the full cycle time here would make a broken
                // Classic leg quietly scan BLE faster than the user asked for.
                waitForNextInterval(BLE_SCAN_DURATION_MS);
                return;
            }

            resetClassicDiscoveryHealth();
        }

        currentScanPhase = ScanPhase.CLASSIC_SCANNING;

        // Classic discovery stops automatically after ~12 seconds. Advance a moment after that so
        // the inquiry runs to its natural completion rather than being truncated.
        bluetoothScanFuture = bluetoothScanExecutor.schedule(this::startNextScanCycle,
                CLASSIC_SCAN_DURATION_MS + CLASSIC_DISCOVERY_BUFFER_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Clears the Classic discovery health tracking so that a fresh scanning session, or a cycle
     * that started an inquiry successfully, begins from a clean slate. Re-arming the report latches
     * here is what allows a second failure episode later in a long survey to be reported; the
     * consecutive failure thresholds are what keep a flapping adapter from being noisy.
     */
    private void resetClassicDiscoveryHealth()
    {
        consecutiveClassicDiscoveryFailures.set(0);
        classicDiscoveryFailureReported.set(false);
        consecutiveClassicInquiryOverlaps.set(0);
        classicInquiryOverlapReported.set(false);
    }

    /**
     * Records that a scan cycle found an inquiry already in progress, and reports a non-fatal once
     * the overlap has persisted with no sign that any inquiry is actually running.
     * <p>
     * An overlap on its own is benign and must not be reported. When an app really is discovering,
     * this receiver still sees ACTION_FOUND and ACTION_DISCOVERY_FINISHED and the survey still
     * captures Classic devices, so nothing is lost. An adapter wedged reporting isDiscovering()
     * with nothing behind it delivers neither broadcast, and that is the only shape of this
     * condition where Classic capture is genuinely dead.
     */
    private void recordClassicInquiryOverlap()
    {
        final int overlapCount = consecutiveClassicInquiryOverlaps.incrementAndGet();

        // A streak that is just beginning starts listening for discovery activity from scratch.
        if (overlapCount == 1) sawClassicDiscoveryActivity = false;

        Timber.d("Bluetooth Classic discovery was skipped because an inquiry was already in progress. Consecutive overlaps: %d",
                overlapCount);

        if (overlapCount >= CLASSIC_INQUIRY_OVERLAP_REPORT_THRESHOLD
                && !sawClassicDiscoveryActivity
                && classicInquiryOverlapReported.compareAndSet(false, true))
        {
            NsCrashReporter.INSTANCE.recordException(
                    new IllegalStateException("The Bluetooth adapter reported an inquiry in progress for "
                            + overlapCount + " consecutive scan cycles without any discovery activity"),
                    "The Bluetooth adapter has reported isDiscovering() for " + overlapCount
                            + " consecutive scan cycles without delivering a single discovery broadcast,"
                            + " so Bluetooth Classic capture is not running. BLE scanning is unaffected.");
        }
    }

    /**
     * Records that a Classic discovery cycle failed to start, and reports a non-fatal once the
     * failures stop looking transient.
     * <p>
     * Timber is only planted in debug builds, so a log on its own would never reach a released
     * device. Without the non-fatal, a Classic leg that has silently stopped collecting is
     * invisible both to the user and to us, since BLE keeps producing records throughout.
     * <p>
     * Note that this is a regular flavor diagnostic. The CDR flavor ships without Crashlytics and
     * its NsCrashReporter is a no-op that forwards to Timber, so in a CDR release build this
     * reports nowhere at all. Covering that flavor needs a user visible surface instead.
     *
     * @param reason Why the inquiry did not start, used in the log and the report.
     */
    private void recordClassicDiscoveryNotStarted(String reason)
    {
        final int failureCount = consecutiveClassicDiscoveryFailures.incrementAndGet();
        Timber.w("Bluetooth Classic discovery did not start because %s. Consecutive failures: %d",
                reason, failureCount);

        if (failureCount >= CLASSIC_DISCOVERY_FAILURE_REPORT_THRESHOLD
                && classicDiscoveryFailureReported.compareAndSet(false, true))
        {
            // NsCrashReporter is a Kotlin object, so Java reaches it through INSTANCE.
            NsCrashReporter.INSTANCE.recordException(
                    new IllegalStateException("Bluetooth Classic discovery repeatedly failed to start because " + reason),
                    "Bluetooth Classic discovery has not started for " + failureCount
                            + " consecutive scan cycles. The last cycle heard "
                            + classicDevicesFoundThisCycle.get() + " Classic device(s) from any"
                            + " in progress inquiry. BLE scanning is unaffected.");
        }
    }

    /**
     * Waits for the next scan interval, charging a full scan cycle against the configured interval.
     * <p>
     * The Classic phase occupies its inquiry duration plus the buffer, so the buffer has to be
     * counted here too or the cycle overruns the interval the user configured by a second every
     * time.
     */
    private void waitForNextInterval()
    {
        waitForNextInterval(BLE_SCAN_DURATION_MS + CLASSIC_SCAN_DURATION_MS + CLASSIC_DISCOVERY_BUFFER_MS);
    }

    /**
     * Waits for the next scan interval, charging only the phases that actually ran.
     *
     * @param scanningTimeMs How long this cycle spent scanning, subtracted from the configured
     *                       interval so that the cycle as a whole lands on that interval.
     */
    private void waitForNextInterval(long scanningTimeMs)
    {
        currentScanPhase = ScanPhase.WAITING_NEXT_INTERVAL;

        final long waitTime = Math.max(1000, bluetoothScanRateMs - scanningTimeMs);

        Timber.d("Bluetooth scan cycle complete, waiting %d ms before the next cycle", waitTime);

        bluetoothScanFuture = bluetoothScanExecutor.schedule(this::startNextScanCycle,
                waitTime, TimeUnit.MILLISECONDS);
    }

    /**
     * Static nested class for BLE scan callback to avoid memory leaks.
     * The Android Framework's BleScanCallbackWrapper may retain the callback reference
     * after stopScan() is called. By using a static class with a nullable reference,
     * we can break the reference chain in onDestroy while the binder stub is still held.
     */
    private static class BleScanCallbackImpl extends ScanCallback
    {
        private BluetoothController controller;

        BleScanCallbackImpl(BluetoothController controller)
        {
            this.controller = controller;
        }

        void onDestroy()
        {
            controller = null;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result)
        {
            final BluetoothController ctrl = controller;
            if (ctrl == null) return;

            if (ctrl.isPaused())
            {
                Timber.v("Bluetooth scan result received but scanning is paused, ignoring");
                return;
            }

            if (ctrl.shouldLogDevice(result.getDevice(), result.getRssi()))
            {
                ctrl.surveyRecordProcessor.onBluetoothScanUpdate(result);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results)
        {
            final BluetoothController ctrl = controller;
            if (ctrl == null) return;

            if (ctrl.isPaused())
            {
                Timber.v("Bluetooth batch scan results received but scanning is paused, ignoring");
                return;
            }

            List<ScanResult> filteredResults = new ArrayList<>();
            for (ScanResult result : results)
            {
                if (ctrl.shouldLogDevice(result.getDevice(), result.getRssi()))
                {
                    filteredResults.add(result);
                }
            }
            if (!filteredResults.isEmpty())
            {
                ctrl.surveyRecordProcessor.onBluetoothScanUpdate(filteredResults);
            }
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
    }

    /**
     * Static nested class for Bluetooth Classic broadcast receiver to avoid memory leaks.
     * Similar to BleScanCallbackImpl, this prevents the receiver from holding a strong
     * reference to the controller after the service is destroyed.
     */
    private static class BluetoothBroadcastReceiverImpl extends BroadcastReceiver
    {
        private BluetoothController controller;

        BluetoothBroadcastReceiverImpl(BluetoothController controller)
        {
            this.controller = controller;
        }

        void onDestroy()
        {
            controller = null;
        }

        @Override
        public void onReceive(Context context, Intent intent)
        {
            final BluetoothController ctrl = controller;
            if (ctrl == null) return;

            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction()))
            {
                // Recorded before the paused check because this is a health signal about the
                // adapter rather than about this survey: some inquiry is running and producing
                // results, which is exactly what a wedged adapter never does.
                ctrl.sawClassicDiscoveryActivity = true;

                if (ctrl.isPaused())
                {
                    Timber.v("Bluetooth classic device found but scanning is paused, ignoring");
                    return;
                }

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

                // Counted before the de-duplication filter so the completion log reflects what was
                // heard rather than what survived filtering.
                ctrl.classicDevicesFoundThisCycle.incrementAndGet();

                if (ctrl.shouldLogDevice(device, rssi))
                {
                    ctrl.surveyRecordProcessor.onBluetoothClassicScanUpdate(device, rssi);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction()))
            {
                // This action has always been registered but never handled. It is the only signal
                // that an inquiry ran to completion, which is what distinguishes a working Classic
                // leg that found nothing from one that never started. Like ACTION_FOUND it is sent
                // for any app's inquiry, so the count below is not necessarily ours.
                ctrl.sawClassicDiscoveryActivity = true;

                Timber.d("Bluetooth Classic discovery finished, %d device(s) were heard during the inquiry",
                        ctrl.classicDevicesFoundThisCycle.get());
            }
        }
    }
}
