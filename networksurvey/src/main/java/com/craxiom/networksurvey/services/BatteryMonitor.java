package com.craxiom.networksurvey.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;

import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import timber.log.Timber;

/**
 * Monitors battery level changes and notifies listeners when the battery level
 * crosses the configured threshold. Includes debouncing to prevent rapid state
 * changes due to battery level fluctuations.
 */
public class BatteryMonitor extends BroadcastReceiver
{
    /**
     * Debounce delay in milliseconds to prevent rapid state changes.
     * Battery level can fluctuate slightly, so we wait before triggering state changes.
     */
    private static final long DEBOUNCE_DELAY_MS = 5000; // 5 seconds

    private final Context context;
    private final Set<IBatteryLevelListener> listeners = new CopyOnWriteArraySet<>();
    private int currentBatteryLevel = -1;
    private boolean isRegistered = false;
    private boolean isPausedDueToBattery = false;

    // Handler for debouncing
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingNotification;

    /**
     * Interface for receiving battery level notifications.
     */
    public interface IBatteryLevelListener
    {
        /**
         * Called when battery level changes.
         *
         * @param newLevel The new battery level percentage
         */
        void onBatteryLevelChanged(int newLevel);

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
     * Starts the battery monitor and registers it to receive battery change broadcasts.
     * This should be called once when the service starts.
     */
    public void startMonitoring()
    {
        if (isRegistered)
        {
            Timber.w("BatteryMonitor is already registered");
            return;
        }

        final IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final Intent batteryStatus = context.registerReceiver(this, filter);
        isRegistered = true;

        // Get initial battery level
        if (batteryStatus != null)
        {
            updateBatteryLevel(batteryStatus);
        }

        Timber.i("BatteryMonitor started");
    }

    /**
     * Registers a listener to receive battery level notifications.
     *
     * @param listener The listener to add
     */
    public void register(IBatteryLevelListener listener)
    {
        if (listener != null)
        {
            listeners.add(listener);
            // Notify the new listener of the current battery level
            if (currentBatteryLevel >= 0)
            {
                listener.onBatteryLevelChanged(currentBatteryLevel);
            }
            Timber.d("Battery listener registered, total listeners: %d", listeners.size());
        }
    }

    /**
     * Unregisters a listener from receiving battery level notifications.
     *
     * @param listener The listener to remove
     */
    public void unregister(IBatteryLevelListener listener)
    {
        if (listener != null)
        {
            listeners.remove(listener);
        }
    }

    /**
     * Stops the battery monitor and unregisters it from receiving broadcasts.
     * This should be called when the service is destroyed.
     */
    public void stopMonitoring()
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
            listeners.clear();

            // Cancel any pending notifications
            if (pendingNotification != null)
            {
                handler.removeCallbacks(pendingNotification);
                pendingNotification = null;
            }

            Timber.i("BatteryMonitor stopped");
        } catch (Exception e)
        {
            Timber.e(e, "Error stopping BatteryMonitor");
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

        // Notify all listeners of battery level change
        for (IBatteryLevelListener listener : listeners)
        {
            try
            {
                listener.onBatteryLevelChanged(currentBatteryLevel);
            } catch (Exception e)
            {
                Timber.e(e, "Error notifying listener of battery level change");
            }
        }

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
        // Get the threshold from preferences
        final int threshold = PreferenceUtils.getBatteryThresholdPercent(context);

        // Check if battery management is enabled
        if (!PreferenceUtils.isBatteryManagementEnabled(context) || threshold <= 0)
        {
            // If feature is disabled but we were paused, notify to resume
            if (isPausedDueToBattery)
            {
                isPausedDueToBattery = false;
                if (!listeners.isEmpty())
                {
                    notifyAboveThresholdDebounced(currentLevel, threshold);
                }
            }
            return;
        }

        // Special handling for initialization (previousLevel is -1)
        if (previousLevel < 0)
        {
            // This is the first battery reading, set state based on current level
            if (currentLevel <= threshold)
            {
                isPausedDueToBattery = true;
                Timber.i("Initial battery level %d%% is at or below threshold %d%%, setting paused state",
                        currentLevel, threshold);
                if (!listeners.isEmpty())
                {
                    notifyBelowThresholdDebounced(currentLevel, threshold);
                }
            } else
            {
                isPausedDueToBattery = false;
                Timber.i("Initial battery level %d%% is above threshold %d%%, not pausing",
                        currentLevel, threshold);
            }
            return;
        }

        // Check for crossing below threshold
        if (!isPausedDueToBattery && currentLevel <= threshold)
        {
            // Battery dropped to or below threshold
            isPausedDueToBattery = true;
            if (!listeners.isEmpty())
            {
                notifyBelowThresholdDebounced(currentLevel, threshold);
            }
        }
        // Check for crossing above threshold (must be coming from below)
        else if (isPausedDueToBattery && currentLevel > threshold && previousLevel <= threshold)
        {
            // Battery rose above threshold after being below it
            isPausedDueToBattery = false;
            if (!listeners.isEmpty())
            {
                notifyAboveThresholdDebounced(currentLevel, threshold);
            }
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
            if (isPausedDueToBattery)
            {
                Timber.i("Battery level %d%% is at or below threshold %d%%, notifying listeners to pause",
                        currentLevel, threshold);
                for (IBatteryLevelListener listener : listeners)
                {
                    try
                    {
                        listener.onBatteryLevelBelowThreshold(currentLevel, threshold);
                    } catch (Exception e)
                    {
                        Timber.e(e, "Error notifying listener of battery below threshold");
                    }
                }
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
            if (!isPausedDueToBattery)
            {
                Timber.i("Battery level %d%% is above threshold %d%%, notifying listeners to resume",
                        currentLevel, threshold);
                for (IBatteryLevelListener listener : listeners)
                {
                    try
                    {
                        listener.onBatteryLevelAboveThreshold(currentLevel, threshold);
                    } catch (Exception e)
                    {
                        Timber.e(e, "Error notifying listener of battery above threshold");
                    }
                }
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
        if (currentBatteryLevel <= 0)
        {
            Timber.w("Cannot reevaluate threshold - no battery level available");
            return;
        }

        // Get the current threshold from preferences
        final int threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        final boolean batteryManagementEnabled = PreferenceUtils.isBatteryManagementEnabled(context);

        Timber.d("Reevaluating battery threshold - current level: %d%%, threshold: %d%%, enabled: %b",
                currentBatteryLevel, threshold, batteryManagementEnabled);

        // Determine what the state should be based on current level and threshold
        final boolean shouldBePaused = batteryManagementEnabled && threshold > 0 && currentBatteryLevel <= threshold;

        // If state needs to change, update it and notify
        if (shouldBePaused != isPausedDueToBattery)
        {
            isPausedDueToBattery = shouldBePaused;

            if (!listeners.isEmpty())
            {
                if (shouldBePaused)
                {
                    Timber.i("Battery threshold changed - pausing operations (level %d%% <= threshold %d%%)",
                            currentBatteryLevel, threshold);
                    notifyBelowThresholdDebounced(currentBatteryLevel, threshold);
                } else
                {
                    Timber.i("Battery threshold changed - resuming operations (level %d%% > threshold %d%% or disabled)",
                            currentBatteryLevel, threshold);
                    notifyAboveThresholdDebounced(currentBatteryLevel, threshold);
                }
            }
        } else
        {
            Timber.d("Battery threshold reevaluated - no state change needed");
        }
    }
}