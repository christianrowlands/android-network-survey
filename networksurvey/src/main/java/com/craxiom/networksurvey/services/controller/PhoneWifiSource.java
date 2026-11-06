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
import android.os.SystemClock;

import androidx.core.app.ActivityCompat;

import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.ui.wifi.model.AndroidWifiThrottleProbe;
import com.craxiom.networksurvey.ui.wifi.model.WifiThrottling;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * The phone's own Wi-Fi scanner as a {@link WifiScanSource}: a scheduled task asks Android for a
 * scan at the controller's effective scan interval and a broadcast receiver hands the results to
 * the {@link SurveyRecordProcessor}. Scanning faster for a live view, and the scan throttle check
 * that allows it, apply only here.
 * <p>
 * Every method runs with the {@link WifiController}'s lock held, the same lock the scan task and
 * the receiver take, so the service reference never goes away underneath them.
 */
class PhoneWifiSource implements WifiScanSource
{
    /** How long after a start the first scan is asked for. */
    private static final long FIRST_SCAN_DELAY_MS = 2_000L;

    private final WifiController controller;
    private final Object lock;
    private final SurveyRecordProcessor surveyRecordProcessor;
    private final ScheduledThreadPoolExecutor wifiScanExecutor = new ScheduledThreadPoolExecutor(1);
    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context c, Intent intent)
        {
            onScanResults(intent);
        }
    };

    private ScheduledFuture<?> wifiScanFuture;
    // The interval the scheduled scan task repeats at, and when it last asked for a scan.
    private volatile int scheduledScanRateMs;
    private volatile long lastScanElapsedMs;
    private volatile WifiManager wifiManager;
    private volatile boolean running;
    private boolean receiverRegistered;

    PhoneWifiSource(WifiController controller, Object lock, SurveyRecordProcessor surveyRecordProcessor)
    {
        this.controller = controller;
        this.lock = lock;
        this.surveyRecordProcessor = surveyRecordProcessor;
        // Scan tasks are replaced whenever a live view opens or closes; drop the replaced ones at once.
        wifiScanExecutor.setRemoveOnCancelPolicy(true);
    }

    @Override
    public void start()
    {
        final NetworkSurveyService service = controller.surveyService;
        if (service == null) return;

        wifiManager = (WifiManager) service.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null)
        {
            Timber.wtf("The Wi-Fi manager is null, can't start scanning for Wi-Fi networks.");
            return;
        }

        running = true;
        registerReceiver(service);
        scheduleScans(FIRST_SCAN_DELAY_MS);
    }

    @Override
    public void stop()
    {
        running = false;
        if (wifiScanFuture != null)
        {
            wifiScanFuture.cancel(true);
            wifiScanFuture = null;
            Timber.d("Cancelled Wi-Fi scanning task");
        }
        unregisterReceiver();
    }

    @Override
    public void pause()
    {
        // The scan task keeps running and skips its scans while the controller is paused.
        unregisterReceiver();
        Timber.d("Unregistered Wi-Fi scan receiver for the pause");
    }

    @Override
    public void resume()
    {
        final NetworkSurveyService service = controller.surveyService;
        if (service == null || !running) return;
        registerReceiver(service);
        Timber.d("Re-registered Wi-Fi scan receiver after the pause");
    }

    @Override
    public void onLiveViewsChanged()
    {
        rescheduleFromLastScan();
    }

    @Override
    public void onScanRateChanged()
    {
        if (running) scheduleScans(FIRST_SCAN_DELAY_MS);
    }

    /**
     * Wi-Fi scans faster for a live view only when Android does not throttle scans. A throttled
     * phone allows 4 scans every 2 minutes and then refuses every scan until the window passes, so
     * faster scans would leave the survey with no Wi-Fi data at all.
     */
    @Override
    public boolean canScanFaster()
    {
        final WifiManager manager = wifiManager;
        if (manager == null) return false;
        return WifiThrottling.scansUnthrottled(Build.VERSION.SDK_INT, AndroidWifiThrottleProbe.isScanThrottleEnabled(manager));
    }

    @Override
    public void destroy()
    {
        wifiScanExecutor.shutdown();
    }

    /**
     * Moves the scan task to the current effective scan interval, with the next scan one interval
     * after the last, never sooner, so screens opening and closing in quick succession do not scan
     * in a burst. Does nothing while this source is stopped.
     */
    private void rescheduleFromLastScan()
    {
        synchronized (lock)
        {
            final NetworkSurveyService service = controller.surveyService;
            if (service == null || !running) return;

            final long sinceLastScanMs = SystemClock.elapsedRealtime() - lastScanElapsedMs;
            scheduleScans(Math.max(0, controller.getEffectiveScanRateMs() - sinceLastScanMs));
            service.updateLocationListener();
            controller.publishLiveScanRate();
        }
    }

    /**
     * Replaces the scan task with one that repeats at the current effective scan interval. The
     * broadcast receiver is left alone, so no scan result is missed while the task changes.
     *
     * @param initialDelayMs How long to wait before the first scan.
     */
    private void scheduleScans(long initialDelayMs)
    {
        if (wifiScanFuture != null)
        {
            // Not interrupted: the task may be the caller, and a scan already asked for still delivers.
            wifiScanFuture.cancel(false);
            wifiScanFuture = null;
        }

        final int rateMs = controller.getEffectiveScanRateMs();
        scheduledScanRateMs = rateMs;
        wifiScanFuture = wifiScanExecutor.scheduleWithFixedDelay(this::runScheduledScan, initialDelayMs, rateMs, TimeUnit.MILLISECONDS);

        Timber.d("Scheduled Wi-Fi scanning every %d ms, starting in %d ms", rateMs, initialDelayMs);
    }

    /**
     * Asks Android for a Wi-Fi scan; the results arrive through the broadcast receiver.
     */
    private void runScheduledScan()
    {
        try
        {
            if (!running)
            {
                Timber.i("Wi-Fi scanning is no longer active, skipping scan");
                return;
            }

            if (controller.isPaused())
            {
                Timber.d("Wi-Fi scanning is paused for battery management");
                return;
            }

            // The throttle can be turned back on while a live view is open. Going back to the scan
            // interval before scanning keeps the faster scans from using up Android's allowance.
            if (controller.getEffectiveScanRateMs() != scheduledScanRateMs)
            {
                rescheduleFromLastScan();
                return;
            }

            final WifiManager manager = wifiManager;
            if (manager == null) return;

            lastScanElapsedMs = SystemClock.elapsedRealtime();
            if (!manager.startScan())
            {
                // Continue trying on the next iteration; the scheduled task keeps running.
                Timber.e("Kicking off a Wi-Fi scan failed");
            }
        } catch (Exception e)
        {
            Timber.e(e, "Could not run a Wi-Fi scan");
        }
    }

    private void onScanResults(Intent intent)
    {
        if (!intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false))
        {
            Timber.e("A Wi-Fi scan failed, ignoring the results.");
            return;
        }

        synchronized (lock)
        {
            final NetworkSurveyService service = controller.surveyService;
            final WifiManager manager = wifiManager;
            if (service == null || manager == null || !running) return;

            if (ActivityCompat.checkSelfPermission(service.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
            {
                Timber.e("Could not get the Wi-FI scan results because the ACCESS_FINE_LOCATION permission is not granted.");
                return;
            }
            final List<ScanResult> results = manager.getScanResults();
            if (results == null)
            {
                Timber.d("Null wifi scan results");
                return;
            }

            surveyRecordProcessor.onWifiScanUpdate(results);
        }
    }

    private void registerReceiver(NetworkSurveyService service)
    {
        if (receiverRegistered) return;
        final IntentFilter scanResultsIntentFilter = new IntentFilter();
        scanResultsIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        service.registerReceiver(wifiScanReceiver, scanResultsIntentFilter);
        receiverRegistered = true;
    }

    private void unregisterReceiver()
    {
        final NetworkSurveyService service = controller.surveyService;
        if (!receiverRegistered || service == null) return;
        try
        {
            service.unregisterReceiver(wifiScanReceiver);
        } catch (Exception e)
        {
            Timber.v(e, "Could not unregister the Wi-Fi scan receiver");
        }
        receiverRegistered = false;
    }
}
