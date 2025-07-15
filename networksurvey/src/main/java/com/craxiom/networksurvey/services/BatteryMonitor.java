package com.craxiom.networksurvey.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;

import com.craxiom.networksurvey.util.PreferenceUtils;

import timber.log.Timber;

/**
 * Monitors battery level changes and notifies listeners when the battery level
 * crosses the configured threshold. Includes debouncing to prevent rapid state
 * changes due to battery level fluctuations.
 *
 * @since 1.40.0
 */
public class BatteryMonitor extends BroadcastReceiver
{
    /**
     * Debounce delay in milliseconds to prevent rapid state changes.
     * Battery level can fluctuate slightly, so we wait before triggering state changes.
     */
    private static final long DEBOUNCE_DELAY_MS = 5000; // 5 seconds
    
    private final Context context;
    private BatteryLevelListener listener;
    private int currentBatteryLevel = -1;
    private boolean isRegistered = false;
    private boolean isPausedDueToBattery = false;
    
    // Handler for debouncing
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingNotification;
    
    /**
     * Interface for receiving battery level threshold crossing notifications.
     */
    public interface BatteryLevelListener
    {
        /**
         * Called when battery level drops to or below the threshold.
         *
         * @param currentLevel The current battery level percentage
         * @param threshold    The threshold that was crossed
         */
        void onBatteryLevelBelowThreshold(int currentLevel, int threshold);
        
        /**
         * Called when battery level rises above the threshold after being below it.
         *
         * @param currentLevel The current battery level percentage
         * @param threshold    The threshold that was crossed
         */
        void onBatteryLevelAboveThreshold(int currentLevel, int threshold);
    }
    
    /**
     * Creates a new BatteryMonitor instance.
     *
     * @param context The application context
     */
    public BatteryMonitor(Context context)
    {
        this.context = context.getApplicationContext();
    }
    
    /**
     * Registers the battery monitor to receive battery change broadcasts.
     *
     * @param listener The listener to notify of threshold crossings
     */
    public void register(BatteryLevelListener listener)
    {
        if (isRegistered)
        {
            Timber.w("BatteryMonitor is already registered");
            return;
        }
        
        this.listener = listener;
        
        final IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final Intent batteryStatus = context.registerReceiver(this, filter);
        isRegistered = true;
        
        // Get initial battery level
        if (batteryStatus != null)
        {
            updateBatteryLevel(batteryStatus);
        }
        
        Timber.i("BatteryMonitor registered");
    }
    
    /**
     * Unregisters the battery monitor from receiving broadcasts.
     */
    public void unregister()
    {
        if (!isRegistered)
        {
            Timber.w("BatteryMonitor is not registered");
            return;
        }
        
        try
        {
            context.unregisterReceiver(this);
            isRegistered = false;
            listener = null;
            
            // Cancel any pending notifications
            if (pendingNotification != null)
            {
                handler.removeCallbacks(pendingNotification);
                pendingNotification = null;
            }
            
            Timber.i("BatteryMonitor unregistered");
        } catch (Exception e)
        {
            Timber.e(e, "Error unregistering BatteryMonitor");
        }
    }
    
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction()))
        {
            updateBatteryLevel(intent);
        }
    }
    
    /**
     * Updates the battery level and checks for threshold crossings.
     *
     * @param batteryStatus The battery status intent
     */
    private void updateBatteryLevel(Intent batteryStatus)
    {
        final int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        
        if (level < 0 || scale <= 0)
        {
            Timber.w("Invalid battery level or scale: level=%d, scale=%d", level, scale);
            return;
        }
        
        final int batteryPercent = (int) ((level / (float) scale) * 100);
        
        // Only process if battery level actually changed
        if (batteryPercent == currentBatteryLevel)
        {
            return;
        }
        
        final int previousLevel = currentBatteryLevel;
        currentBatteryLevel = batteryPercent;
        
        Timber.d("Battery level changed from %d%% to %d%%", previousLevel, currentBatteryLevel);
        
        // Check if we need to notify about threshold crossing
        checkThresholdCrossing(previousLevel, currentBatteryLevel);
    }
    
    /**
     * Checks if the battery level has crossed the threshold and notifies the listener.
     * Includes debouncing to prevent rapid state changes.
     *
     * @param previousLevel The previous battery level
     * @param currentLevel  The current battery level
     */
    private void checkThresholdCrossing(int previousLevel, int currentLevel)
    {
        if (listener == null)
        {
            return;
        }
        
        // Get the threshold from preferences
        final int threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        
        // Check if battery management is enabled
        if (!PreferenceUtils.isBatteryManagementEnabled(context) || threshold <= 0)
        {
            // If feature is disabled but we were paused, notify to resume
            if (isPausedDueToBattery)
            {
                isPausedDueToBattery = false;
                notifyAboveThresholdDebounced(currentLevel, threshold);
            }
            return;
        }
        
        // Check for crossing below threshold
        if (!isPausedDueToBattery && currentLevel <= threshold)
        {
            // Battery dropped to or below threshold
            isPausedDueToBattery = true;
            notifyBelowThresholdDebounced(currentLevel, threshold);
        }
        // Check for crossing above threshold (must be coming from below)
        else if (isPausedDueToBattery && currentLevel > threshold && previousLevel <= threshold)
        {
            // Battery rose above threshold after being below it
            isPausedDueToBattery = false;
            notifyAboveThresholdDebounced(currentLevel, threshold);
        }
    }
    
    /**
     * Notifies the listener that battery is below threshold, with debouncing.
     */
    private void notifyBelowThresholdDebounced(final int currentLevel, final int threshold)
    {
        // Cancel any pending notification
        if (pendingNotification != null)
        {
            handler.removeCallbacks(pendingNotification);
        }
        
        pendingNotification = () -> {
            if (listener != null && isPausedDueToBattery)
            {
                Timber.i("Battery level %d%% is at or below threshold %d%%, notifying listener to pause", 
                        currentLevel, threshold);
                listener.onBatteryLevelBelowThreshold(currentLevel, threshold);
            }
            pendingNotification = null;
        };
        
        handler.postDelayed(pendingNotification, DEBOUNCE_DELAY_MS);
    }
    
    /**
     * Notifies the listener that battery is above threshold, with debouncing.
     */
    private void notifyAboveThresholdDebounced(final int currentLevel, final int threshold)
    {
        // Cancel any pending notification
        if (pendingNotification != null)
        {
            handler.removeCallbacks(pendingNotification);
        }
        
        pendingNotification = () -> {
            if (listener != null && !isPausedDueToBattery)
            {
                Timber.i("Battery level %d%% is above threshold %d%%, notifying listener to resume", 
                        currentLevel, threshold);
                listener.onBatteryLevelAboveThreshold(currentLevel, threshold);
            }
            pendingNotification = null;
        };
        
        handler.postDelayed(pendingNotification, DEBOUNCE_DELAY_MS);
    }
    
    /**
     * Gets the current battery level percentage.
     *
     * @return The current battery level (0-100), or -1 if unknown
     */
    public int getCurrentBatteryLevel()
    {
        return currentBatteryLevel;
    }
    
    /**
     * Checks if operations are currently paused due to low battery.
     *
     * @return true if paused due to battery level being below threshold
     */
    public boolean isPausedDueToBattery()
    {
        return isPausedDueToBattery;
    }
    
    /**
     * Forces a re-evaluation of the battery threshold. Useful when preferences change.
     */
    public void reevaluateThreshold()
    {
        if (currentBatteryLevel > 0)
        {
            // Simulate a battery level change to trigger threshold checking
            final int tempLevel = currentBatteryLevel;
            currentBatteryLevel = -1;
            checkThresholdCrossing(tempLevel - 1, tempLevel);
        }
    }
}