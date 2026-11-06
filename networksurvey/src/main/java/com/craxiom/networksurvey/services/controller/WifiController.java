package com.craxiom.networksurvey.services.controller;

import android.os.Handler;
import android.os.Looper;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.logging.WifiCsvLogger;
import com.craxiom.networksurvey.scout.ScoutManager;
import com.craxiom.networksurvey.scout.model.WifiSource;
import com.craxiom.networksurvey.board.v1.ApObservation;
import com.craxiom.networksurvey.scout.wifi.ScoutHeard;
import com.craxiom.networksurvey.scout.wifi.ScoutSourceWatcher;
import com.craxiom.networksurvey.scout.wifi.ScoutWifiHost;
import com.craxiom.networksurvey.scout.wifi.ScoutWifiSource;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.survey.model.RecordType;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

/**
 * Handles all of the Wi-Fi related logic for Network Survey Service to include file logging
 * and managing the Wi-Fi scanning.
 * <p>
 * Wi-Fi comes from one {@link WifiScanSource} at a time: the phone's own scanner
 * ({@link PhoneWifiSource}) or the paired NS Scout ({@link ScoutWifiSource}), never both. This
 * class is the facade the service talks to either way: scanning active, the scan interval, pause
 * and resume, and the Wi-Fi CSV logger work the same for both. The source switches under the
 * same lock that guards logging and scanning, and never while a survey records, so one Mission
 * never mixes Wi-Fi from two radios. Faster scans for live views, and the scan throttle that
 * limits them, apply only to the phone.
 *
 * @noinspection NonPrivateFieldAccessedInSynchronizedContext
 */
public class WifiController extends AController implements ScoutWifiHost
{
    private final AtomicBoolean wifiLoggingEnabled = new AtomicBoolean(false);

    private final SurveyRecordProcessor surveyRecordProcessor;
    private final ScoutManager scoutManager;
    private final ScoutSourceWatcher sourceWatcher;
    private final WifiSourceSelector selector;
    private final WifiCsvLogger wifiCsvLogger;

    private volatile int wifiScanRateMs;

    public WifiController(NetworkSurveyService surveyService, ExecutorService executorService,
                          Looper serviceLooper, Handler serviceHandler,
                          SurveyRecordProcessor surveyRecordProcessor)
    {
        super(surveyService, executorService);

        this.surveyRecordProcessor = surveyRecordProcessor;

        wifiCsvLogger = new WifiCsvLogger(surveyService);
        scoutManager = ScoutManager.getInstance(surveyService);
        sourceWatcher = new ScoutSourceWatcher(scoutManager);

        final Map<WifiSource, WifiScanSource> sources = new EnumMap<>(WifiSource.class);
        sources.put(WifiSource.PHONE, new PhoneWifiSource(this, wifiLoggingEnabled, surveyRecordProcessor));
        sources.put(WifiSource.SCOUT, new ScoutWifiSource(scoutManager, this, surveyService.getPrimaryLocationListener()));
        selector = new WifiSourceSelector(sources, scoutManager.getWifiSource().getValue());
    }

    @Override
    public void onDestroy()
    {
        // Sync on the wifiLoggingEnabled to ensure cleaning up resources (e.g. assigning null
        // to the surveyService) does not cause a NPE if logging is still being enabled or disabled.
        synchronized (wifiLoggingEnabled)
        {
            sourceWatcher.stop();
            selector.destroy();
            LiveScanRates.clear(RecordType.WIFI);
            wifiCsvLogger.onDestroy();
            super.onDestroy();
        }
    }

    public boolean isLoggingEnabled()
    {
        return wifiLoggingEnabled.get();
    }

    /**
     * @return True while something needs Wi-Fi and the selected source is running for it, paused
     * or not.
     */
    public boolean isScanningActive()
    {
        return selector.isActive();
    }

    /**
     * @return Where Wi-Fi comes from right now.
     */
    public WifiSource getWifiSource()
    {
        return selector.getSource();
    }

    /**
     * @return True while the NS Scout is the Wi-Fi source and holds a capture for it.
     */
    public boolean isScoutInUse()
    {
        return selector.isActive() && selector.getSource() == WifiSource.SCOUT;
    }

    @Override
    public int getScanRateMs()
    {
        return wifiScanRateMs;
    }

    @Override
    protected boolean canScanFaster()
    {
        return selector.getCurrent().canScanFaster();
    }

    @Override
    protected void onLiveViewsChanged()
    {
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return;
            if (selector.isActive()) selector.getCurrent().onLiveViewsChanged();
            publishLiveScanRate();
        }
    }

    /**
     * Asks to change where Wi-Fi comes from. Refused while a survey records, and for the Scout
     * when none is paired. A switch while Wi-Fi runs stops the old source before starting the new
     * one, so the two never run together.
     *
     * @param source The source the user chose.
     * @return What happened.
     */
    public WifiSourceSwitch requestWifiSource(WifiSource source)
    {
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return WifiSourceSwitch.UNCHANGED;

            final WifiSourceSwitch result = selector.request(source, surveyService.isAnySurveyActive(),
                    scoutManager.getPairedScout().getValue() != null);
            if (result == WifiSourceSwitch.SWITCHED)
            {
                scoutManager.setWifiSource(source);
                onSourceChanged();
            }
            Timber.i("Wi-Fi source request %s: %s", source, result);
            return result;
        }
    }

    /**
     * Starts following the Scout manager's own source changes (pairing, removing the Scout).
     * Called once the service is set up.
     */
    public void startWatchingWifiSource()
    {
        sourceWatcher.start(this::applyWifiSource);
    }

    private void applyWifiSource(WifiSource source)
    {
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return;
            if (source == WifiSource.SCOUT && selector.getSource() != source && surveyService.isAnySurveyActive())
            {
                // Pairing finished while a survey records: one Mission never mixes Wi-Fi from two
                // radios, so the phone stays the source until the user switches after the survey.
                Timber.i("The Scout was paired during a survey; keeping the phone as the Wi-Fi source");
                scoutManager.setWifiSource(selector.getSource());
                return;
            }
            if (selector.apply(source))
            {
                Timber.i("The Wi-Fi source is now %s", source);
                onSourceChanged();
            }
        }
    }

    /**
     * Republishes everything that depends on the source. Called with the lock held.
     */
    private void onSourceChanged()
    {
        publishLiveScanRate();
        surveyService.updateLocationListener();
        surveyService.updateServiceNotification();
        surveyService.notifyLoggingChangedListeners();
    }

    public void onRolloverPreferenceChanged()
    {
        wifiCsvLogger.onSharedPreferenceChanged();
    }

    /**
     * Called to indicate that an MDM preference changed, which should trigger a re-read of the
     * preferences.
     */
    public void onMdmPreferenceChanged()
    {
        wifiCsvLogger.onMdmPreferenceChanged();
    }

    /**
     * Called to indicate that the Wi-Fi scan rate preference changed, which should trigger a
     * re-read of the preference.
     */
    public void refreshScanRate()
    {
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return;

            final int oldScanRateMs = wifiScanRateMs;
            wifiScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
                    NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS, surveyService.getApplicationContext());

            if (selector.isActive() && oldScanRateMs != wifiScanRateMs)
            {
                Timber.i("Wi-Fi scan rate changed from %d ms to %d ms", oldScanRateMs, wifiScanRateMs);
                selector.getCurrent().onScanRateChanged();
                publishLiveScanRate();
                surveyService.updateLocationListener();
            }
        }
    }

    @Override
    protected void publishLiveScanRate()
    {
        if (selector.isActive() && !isPaused() && selector.getSource() == WifiSource.PHONE)
        {
            LiveScanRates.publish(RecordType.WIFI, this);
        } else
        {
            LiveScanRates.clear(RecordType.WIFI);
        }
    }

    @Override
    public void pauseScanning()
    {
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return;
            super.pauseScanning();
            selector.pause();
        }
    }

    @Override
    public void resumeScanning()
    {
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return;
            super.resumeScanning();
            selector.resume();
        }
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
                // No radio check, as in earlier versions: scans arrive while Wi-Fi is off when Wi-Fi
                // scanning is on, and the scan loop picks up again as soon as the radio comes on.
                successful = wifiCsvLogger.enableLogging(true);

                if (successful)
                {
                    toggleWifiConfig(true);
                } else
                {
                    // the logger failed to toggle; disable it and set local config to false
                    wifiCsvLogger.enableLogging(false);
                    toggleWifiConfig(false);
                }
            } else
            {
                // Unregister first: that hands the logger the Scout's open window, which it can
                // only take while it is still open.
                toggleWifiConfig(false);
                wifiCsvLogger.enableLogging(false);
                successful = true;
            }

            surveyService.updateServiceNotification();
            surveyService.notifyLoggingChangedListeners();

            final boolean newLoggingState = wifiLoggingEnabled.get();

            return successful ? newLoggingState : null;
        }
    }

    /**
     * Kept for the service's startup order; the sources set themselves up when they start.
     */
    public void initializeWifiScanningResources()
    {
        startWatchingWifiSource();
    }

    /**
     * Starts the selected Wi-Fi source. Only starts it if Wi-Fi is not already running.
     */
    public void startWifiRecordScanning()
    {
        // Using wifiLoggingEnabled as the lock object because it is also used in the toggleLogging method
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return;
            if (!selector.start()) return;

            publishLiveScanRate();
            surveyService.updateLocationListener();
        }
    }

    /**
     * Gives {@code listener}, a sink about to leave, the Wi-Fi records a source still holds back
     * (the Scout's open aggregation window). The rest of the window still goes to the listeners
     * that stay. Does nothing while Wi-Fi is not running, and never blocks on another thread.
     *
     * @param listener The sink that is about to be removed.
     */
    public void deliverPendingWifiTo(IWifiSurveyRecordListener listener)
    {
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null || !selector.isActive()) return;
            selector.getCurrent().peekPending(beacons -> surveyRecordProcessor.deliverScoutBeaconsTo(listener, beacons));
        }
    }

    /**
     * Stops the selected Wi-Fi source. A Scout is told to Stop when nothing else holds a capture.
     */
    public void stopWifiRecordScanning()
    {
        // Using wifiLoggingEnabled as the lock object because it is also used in the toggleLogging method
        synchronized (wifiLoggingEnabled)
        {
            if (surveyService == null) return;

            selector.stop();
            publishLiveScanRate();
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

    private void toggleWifiConfig(boolean enable)
    {
        if (surveyService == null) return;

        wifiLoggingEnabled.set(enable);
        if (enable)
        {
            surveyService.registerWifiSurveyRecordListener(wifiCsvLogger);
        } else
        {
            surveyService.unregisterWifiSurveyRecordListener(wifiCsvLogger);
        }
    }

    // ScoutWifiHost, called on the Scout Wi-Fi thread.

    @Override
    public int scanRateMs()
    {
        return wifiScanRateMs;
    }

    @Override
    public int liveViewIntervalMs()
    {
        return getLiveViewIntervalMs();
    }

    @Override
    public Long surveyStartMs()
    {
        final NetworkSurveyService service = surveyService;
        return service != null ? service.getSurveySessionStartTimeMs() : null;
    }

    @Override
    public void onScoutBeacons(List<ScoutHeard<ApObservation>> beacons)
    {
        surveyRecordProcessor.onScoutBeacons(beacons);
    }

    @Override
    public void onScoutSnapshot(List<ScoutHeard<ApObservation>> beacons)
    {
        surveyRecordProcessor.onScoutSnapshot(beacons);
    }
}
