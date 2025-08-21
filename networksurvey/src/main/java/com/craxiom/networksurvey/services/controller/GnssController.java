package com.craxiom.networksurvey.services.controller;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IGnssFailureListener;
import com.craxiom.networksurvey.logging.GnssCsvLogger;
import com.craxiom.networksurvey.logging.GnssRecordLogger;
import com.craxiom.networksurvey.model.LogTypeState;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.util.GpsTestUtil;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * Handles all of the GNSS related logic for Network Survey Service to include file logging
 * and managing the GNSS scanning.
 * @noinspection NonPrivateFieldAccessedInSynchronizedContext
 */
public class GnssController extends AController
{
    /**
     * Time to wait between first location measurement received before considering this device does
     * not likely support raw GNSS collection.
     */
    private static final long TIME_TO_WAIT_FOR_GNSS_RAW_BEFORE_FAILURE = 1000L * 15L;

    /**
     * The threshold for the scan rate in milliseconds that determines if we should use the battery
     * optimized approach of registering and unregistering for GNSS measurements.
     */
    public static final int BATTERY_OPTIMIZATION_SCAN_RATE_THRESHOLD_MS = 30_000;

    private final AtomicBoolean gnssLoggingEnabled = new AtomicBoolean(false);
    private final AtomicBoolean gnssStarted = new AtomicBoolean(false);
    private final AtomicInteger gnssScanningTaskId = new AtomicInteger();
    private final AtomicInteger batteryOptimizedMeasurementCount = new AtomicInteger(0);

    private final ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> batteryOptimizedScanFuture;
    private final AtomicBoolean batteryOptimizedGnssMeasurement = new AtomicBoolean(false);

    private final Handler serviceHandler;
    private LocationManager locationManager = null;
    private final SurveyRecordProcessor surveyRecordProcessor;

    private volatile int gnssScanRateMs;
    private GnssMeasurementsEvent.Callback measurementListener;
    private IGnssFailureListener gnssFailureListener;
    private final GnssRecordLogger gnssRecordLogger;
    private final GnssCsvLogger gnssCsvLogger;
    private long firstGpsAcqTime = Long.MIN_VALUE;
    private boolean gnssRawSupportKnown = false;
    private boolean hasGnssRawFailureNagLaunched = false;
    
    // Track state for pause/resume
    private final AtomicBoolean wasActiveBeforePause = new AtomicBoolean(false);

    public GnssController(NetworkSurveyService surveyService, ExecutorService executorService,
                          Looper serviceLooper, Handler serviceHandler,
                          SurveyRecordProcessor surveyRecordProcessor)
    {
        super(surveyService, executorService);
        this.serviceHandler = serviceHandler;
        this.surveyRecordProcessor = surveyRecordProcessor;

        gnssRecordLogger = new GnssRecordLogger(surveyService, serviceLooper);
        gnssCsvLogger = new GnssCsvLogger(surveyService, serviceLooper);
    }

    @Override
    public void onDestroy()
    {
        // Sync on the gnssLoggingEnabled to ensure cleaning up resources (e.g. assigning null
        // to the surveyService) does not cause a NPE if logging is still being enabled or disabled.
        synchronized (gnssLoggingEnabled)
        {
            gnssRecordLogger.onDestroy();
            gnssCsvLogger.onDestroy();
            super.onDestroy();
        }
    }

    public boolean isLoggingEnabled()
    {
        return gnssLoggingEnabled.get();
    }

    public boolean isScanningActive()
    {
        return gnssStarted.get();
    }

    public int getScanRateMs()
    {
        return gnssScanRateMs;
    }

    public void onRolloverPreferenceChanged()
    {
        gnssRecordLogger.onSharedPreferenceChanged();
        gnssCsvLogger.onSharedPreferenceChanged();
    }

    /**
     * Called to indicate that an MDM preference changed, which should trigger a re-read of the
     * preferences.
     */
    public void onMdmPreferenceChanged()
    {
        gnssRecordLogger.onMdmPreferenceChanged();
        gnssCsvLogger.onMdmPreferenceChanged();
    }

    public void onLogFileTypePreferenceChanged()
    {
        synchronized (gnssLoggingEnabled)
        {
            if (gnssLoggingEnabled.get())
            {
                final boolean originalLoggingState = gnssLoggingEnabled.get();
                toggleLogging(false);
                toggleLogging(true);
                final boolean newLoggingState = gnssLoggingEnabled.get();
                if (originalLoggingState != newLoggingState)
                {
                    Timber.i("Logging state changed from %s to %s", originalLoggingState, newLoggingState);
                }
            }
        }
    }

    @Override
    public void pauseScanning()
    {
        synchronized (gnssLoggingEnabled)
        {
            if (surveyService == null) return;
            
            // Remember if scanning was active before pause
            wasActiveBeforePause.set(gnssStarted.get());
            
            // Call parent to set isPaused flag
            super.pauseScanning();
            
            // Unregister GNSS measurements callback to save battery
            if (gnssStarted.get() && locationManager != null)
            {
                try
                {
                    locationManager.unregisterGnssMeasurementsCallback(measurementListener);
                    Timber.d("Unregistered GNSS measurements callback for battery pause");
                } catch (Exception e)
                {
                    Timber.v(e, "Could not unregister GNSS measurements callback during pause");
                }
            }
        }
    }

    @Override
    public void resumeScanning()
    {
        synchronized (gnssLoggingEnabled)
        {
            if (surveyService == null) return;
            
            // Call parent to clear isPaused flag
            super.resumeScanning();
            
            // Re-register GNSS measurements callback if it was active before pause
            if (wasActiveBeforePause.get() && gnssStarted.get() && locationManager != null)
            {
                try
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    {
                        locationManager.registerGnssMeasurementsCallback(executorService, measurementListener);
                    } else
                    {
                        locationManager.registerGnssMeasurementsCallback(measurementListener);
                    }
                    Timber.d("Re-registered GNSS measurements callback after battery resume");
                } catch (Exception e)
                {
                    Timber.e(e, "Could not re-register GNSS measurements callback after resume");
                }
            }
        }
    }

    /**
     * Called to indicate that the GNSS scan rate preference changed, which should trigger a
     * re-read of the preference.
     */
    public void refreshScanRate()
    {
        if (surveyService == null) return;

        final int oldScanRateMs = gnssScanRateMs;
        gnssScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_GNSS_SCAN_INTERVAL_SECONDS, surveyService.getApplicationContext());
        
        // If scanning is active and the rate has changed, restart scanning to apply the new rate
        if (gnssStarted.get() && oldScanRateMs != gnssScanRateMs)
        {
            Timber.i("GNSS scan rate changed from %d ms to %d ms, restarting scanning to apply new rate", 
                    oldScanRateMs, gnssScanRateMs);
            
            // Check if we're crossing the battery optimization threshold
            boolean wasOptimized = oldScanRateMs >= BATTERY_OPTIMIZATION_SCAN_RATE_THRESHOLD_MS;
            boolean shouldBeOptimized = gnssScanRateMs >= BATTERY_OPTIMIZATION_SCAN_RATE_THRESHOLD_MS;
            
            // Only restart if we're crossing the threshold or in battery-optimized mode
            // (since battery-optimized mode uses a scheduled task with fixed delay)
            if (wasOptimized != shouldBeOptimized || wasOptimized)
            {
                // Stop current scanning (this handles cleanup of listeners and scheduled tasks)
                stopGnssRecordScanning();
                
                // Restart scanning with the new rate
                startGnssRecordScanning();
            }
        }
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
    public Boolean toggleLogging(boolean enable)
    {
        synchronized (gnssLoggingEnabled)
        {
            if (surveyService == null) return null;

            final boolean originalLoggingState = gnssLoggingEnabled.get();
            if (originalLoggingState == enable) return originalLoggingState;

            Timber.i("Toggling GNSS logging to %s", enable);

            boolean successful = false;
            if (enable)
            {
                LogTypeState types = PreferenceUtils.getLogTypePreference(surveyService.getApplicationContext());
                if (types.geoPackage)
                {
                    successful = gnssRecordLogger.enableLogging(true);
                }
                if (types.csv)
                {
                    successful = gnssCsvLogger.enableLogging(true);
                }

                if (successful)
                {
                    toggleGnssConfig(true, types);
                } else
                {
                    // at least one of the loggers failed to toggle;
                    // disable all of them and set local config to false
                    gnssRecordLogger.enableLogging(false);
                    gnssCsvLogger.enableLogging(false);
                    toggleGnssConfig(false, null);
                }
            } else
            {
                // If we are disabling logging, then we need to disable both geoPackage and CSV just
                // in case the user changed the setting after they started logging.
                gnssRecordLogger.enableLogging(false);
                gnssCsvLogger.enableLogging(false);
                toggleGnssConfig(false, null);
                successful = true;
            }

            surveyService.updateServiceNotification();
            surveyService.notifyLoggingChangedListeners();

            final boolean newLoggingState = gnssLoggingEnabled.get();

            return successful ? newLoggingState : null;
        }
    }

    /**
     * Create the callbacks for the {@link GnssMeasurementsEvent} and the {@link GnssStatus} that will be notified of
     * events from the location manager once {@link #startGnssRecordScanning()} is called.
     */
    public void initializeGnssScanningResources()
    {
        measurementListener = new GnssMeasurementsEvent.Callback()
        {
            @Override
            public void onGnssMeasurementsReceived(GnssMeasurementsEvent event)
            {
                gnssRawSupportKnown = true;
                
                // Skip processing if paused for battery management
                if (isPaused())
                {
                    Timber.d("GNSS measurement processing skipped - paused for battery management");
                    return;
                }
                
                if (handleBatteryOptimization() && surveyRecordProcessor != null)
                {
                    Timber.d("GNSS measurement received at %s", System.currentTimeMillis());
                    surveyRecordProcessor.onGnssMeasurements(event);
                }
            }
        };
    }

    /**
     * Starts GNSS record scanning if it is not already started.
     * <p>
     * This method handles registering the GNSS listeners with Android so we get notified of updates.
     * <p>
     * This method is not thread safe, so make sure to call this method from a synchronized block.
     */
    public boolean startGnssRecordScanning()
    {
        // Using gnssLoggingEnabled as the lock object because it is also used in the toggleLogging method
        synchronized (gnssLoggingEnabled)
        {
            if (surveyService == null) return false;

            if (gnssStarted.getAndSet(true)) return true;

            boolean success = false;

            final int handlerTaskId = gnssScanningTaskId.incrementAndGet();

            boolean hasPermissions = ContextCompat.checkSelfPermission(surveyService,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

            if (hasPermissions)
            {
                if (locationManager == null)
                {
                    locationManager = surveyService.getSystemService(LocationManager.class);
                    if (locationManager != null)
                    {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        {
                            if (gnssScanRateMs < BATTERY_OPTIMIZATION_SCAN_RATE_THRESHOLD_MS)
                            {
                                Timber.d("Registering the normal GNSS measurements listener since the scan rate was frequent enough");
                                batteryOptimizedGnssMeasurement.set(false);
                                locationManager.registerGnssMeasurementsCallback(executorService, measurementListener);
                            } else
                            {
                                // If the scan rate is greater than n seconds, then we use the battery optimized
                                // approach where we continually register and unregister for GNSS measurements.
                                // We take this approach because once you register for GNSS measurements it returns one
                                // batch of results every 1 second. Since this is more than we need, and having GNSS
                                // measurements on drains the battery, we will remove the listener after each batch.
                                batteryOptimizedGnssMeasurement.set(true);
                                batteryOptimizedScanFuture = pool.scheduleWithFixedDelay(this::runOneMeasurement,
                                        0, gnssScanRateMs, TimeUnit.MILLISECONDS);
                                Timber.d("Started battery-optimized GNSS scanning with interval %d ms", gnssScanRateMs);
                            }
                        } else
                        {
                            locationManager.registerGnssMeasurementsCallback(measurementListener);
                        }
                        surveyService.getPrimaryLocationListener().addGnssTimeoutCallback(this::checkForGnssTimeout);
                        Timber.i("Successfully registered the GNSS listeners");
                    }
                } else
                {
                    Timber.w("The location manager was null when registering the GNSS listeners");
                }

                // Only check for missed measurements when not in battery optimized mode
                // In battery optimized mode, we expect gaps between measurements
                if (!batteryOptimizedGnssMeasurement.get())
                {
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

                                serviceHandler.postDelayed(this, GpsTestUtil.getGnssTimeoutIntervalMs(gnssScanRateMs));
                            } catch (SecurityException e)
                            {
                                Timber.e(e, "Could not get the required permissions to check for missed GNSS measurement");
                            }
                        }
                    }, GpsTestUtil.getGnssTimeoutIntervalMs(gnssScanRateMs));
                }

                success = true;
            }

            surveyService.updateLocationListener();

            return success;
        }
    }

    /**
     * Unregisters from GPS/GNSS updates from the Android OS.
     * <p>
     * This method is not thread safe, so make sure to call this method from a synchronized block.
     */
    public void stopGnssRecordScanning()
    {
        // Using gnssLoggingEnabled as the lock object because it is also used in the toggleLogging method
        synchronized (gnssLoggingEnabled)
        {
            if (surveyService == null) return;

            if (!gnssStarted.getAndSet(false)) return;

            batteryOptimizedMeasurementCount.set(0);

            if (locationManager != null)
            {
                locationManager.unregisterGnssMeasurementsCallback(measurementListener);
                surveyService.getPrimaryLocationListener().clearGnssTimeoutCallback();
                locationManager = null;
            }

            if (batteryOptimizedScanFuture != null)
            {
                batteryOptimizedScanFuture.cancel(true);
                batteryOptimizedScanFuture = null;
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

    private void toggleGnssConfig(boolean enable, LogTypeState types)
    {
        if (surveyService == null) return;

        gnssLoggingEnabled.set(enable);
        if (enable)
        {
            if (types != null)
            {
                if (types.geoPackage)
                {
                    surveyService.registerGnssSurveyRecordListener(gnssRecordLogger);
                }
                if (types.csv)
                {
                    surveyService.registerGnssSurveyRecordListener(gnssCsvLogger);
                }
            } else
            {
                throw new IllegalArgumentException("LogTypeState cannot be null when enabling GNSS logging");
            }
        } else
        {
            surveyService.unregisterGnssSurveyRecordListener(gnssRecordLogger);
            surveyService.unregisterGnssSurveyRecordListener(gnssCsvLogger);
        }
    }

    /**
     * Registers a listener any GNSS failures. This can include timing out before we received any
     * GNSS measurements.
     */
    public void registerGnssFailureListener(IGnssFailureListener gnssFailureListener)
    {
        this.gnssFailureListener = gnssFailureListener;
    }

    /**
     * Clears the GNSS failure listener.
     */
    public void clearGnssFailureListener()
    {
        gnssFailureListener = null;
    }

    /**
     * Checks to see if the GNSS timeout has occurred. If we have waited longer than {@link #TIME_TO_WAIT_FOR_GNSS_RAW_BEFORE_FAILURE}
     * without any GNSS measurements coming in, we can assume that the device does not support raw GNSS measurements.
     * If that is the case then present that information to the user so they know their device won't support it.
     */
    private void checkForGnssTimeout()
    {
        synchronized (gnssLoggingEnabled)
        {
            if (surveyService == null) return;

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
                        surveyService.getPrimaryLocationListener().clearGnssTimeoutCallback(); // No need for the callback anymore
                    }
                }
            } else
            {
                surveyService.getPrimaryLocationListener().clearGnssTimeoutCallback();
            }
        }
    }

    /**
     * Registers for GNSS measurements so that we can get a single batch of measurements. This is
     * used when the scan rate is greater than n seconds, so that we can save battery life.
     */
    private void runOneMeasurement()
    {
        synchronized (gnssLoggingEnabled)
        {
            if (surveyService == null) return;
            
            // Skip measurement if paused for battery management
            if (isPaused())
            {
                Timber.d("GNSS scanning is paused for battery management");
                return;
            }

            boolean hasPermissions = ContextCompat.checkSelfPermission(surveyService,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

            if (hasPermissions)
            {
                batteryOptimizedMeasurementCount.set(0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                {
                    locationManager.registerGnssMeasurementsCallback(executorService, measurementListener);
                } else
                {
                    locationManager.registerGnssMeasurementsCallback(measurementListener);
                }
            } else
            {
                Timber.w("Could not run the single GNSS measurement because the app does not have the required permissions");
            }
        }
    }

    /**
     * If the scan rate is greater than n seconds, then we use the battery optimized approach where we continually
     * register and unregister for GNSS measurements. We take this approach because once you register for GNSS
     * measurements it returns one batch of results every 1 second. Since this is more than we need, and having GNSS
     * measurements on drains the battery, we will remove the listener after each batch. However, the first several
     * batches of results are not complete as there is a warm up period. So we need to ignore those results. This
     * method ignores the first 14 batches of results, and then unregisters the listener after the 15th batch. In
     * practice on a Pixel 8 Pro, the 7th batch of results was the first complete batch.
     *
     * @return True if the results are good to be used for sending to listeners.
     */
    private boolean handleBatteryOptimization()
    {
        if (batteryOptimizedGnssMeasurement.get())
        {
            synchronized (gnssLoggingEnabled)
            {
                if (batteryOptimizedMeasurementCount.incrementAndGet() >= 15)
                {
                    Timber.i("Saw the 15th GNSS measurement; unregistering the GNSS measurements callback to save battery");
                    locationManager.unregisterGnssMeasurementsCallback(measurementListener);
                    return true;
                } else
                {
                    return false;
                }
            }
        }

        return true;
    }
}
