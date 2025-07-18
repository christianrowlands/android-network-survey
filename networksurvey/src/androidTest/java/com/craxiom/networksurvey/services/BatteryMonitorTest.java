package com.craxiom.networksurvey.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.SystemClock;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.truth.Truth.assertThat;

/**
 * Instrumented tests for BatteryMonitor class.
 * Tests battery level monitoring, threshold detection, and listener notifications.
 */
@RunWith(AndroidJUnit4.class)
public class BatteryMonitorTest
{
    private Context context;
    private BatteryMonitor batteryMonitor;
    private SharedPreferences preferences;
    
    // Test constants
    private static final int TEST_THRESHOLD = 15;
    private static final int BATTERY_LOW = 10;
    private static final int BATTERY_HIGH = 20;
    private static final int DEBOUNCE_DELAY_MS = 6000; // 5 second debounce + 1 second buffer
    
    @Before
    public void setUp()
    {
        context = ApplicationProvider.getApplicationContext();
        batteryMonitor = new BatteryMonitor(context);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Set up test preferences with battery management enabled (threshold > 0)
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, TEST_THRESHOLD)
                .apply();
    }
    
    @After
    public void tearDown()
    {
        batteryMonitor.stopMonitoring();
        
        // Clean up preferences
        preferences.edit()
                .remove(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT)
                .apply();
    }
    
    @Test
    public void testStartAndStopMonitoring()
    {
        // Start monitoring
        batteryMonitor.startMonitoring();
        
        // Try to start again - should log warning but not crash
        batteryMonitor.startMonitoring();
        
        // Stop monitoring
        batteryMonitor.stopMonitoring();
        
        // Try to stop again - should log warning but not crash
        batteryMonitor.stopMonitoring();
    }
    
    @Test
    public void testListenerRegistration()
    {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger batteryLevel = new AtomicInteger(-1);
        
        BatteryMonitor.IBatteryLevelListener listener = new BatteryMonitor.IBatteryLevelListener()
        {
            @Override
            public void onBatteryLevelChanged(int newLevel)
            {
                batteryLevel.set(newLevel);
                latch.countDown();
            }
            
            @Override
            public void onBatteryLevelBelowThreshold(int currentLevel, int threshold)
            {
                // Not used in this test
            }
            
            @Override
            public void onBatteryLevelAboveThreshold(int currentLevel, int threshold)
            {
                // Not used in this test
            }
        };
        
        // Start monitoring first
        batteryMonitor.startMonitoring();
        
        // Register listener - should receive current battery level
        batteryMonitor.register(listener);
        
        // Wait for initial notification
        try
        {
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(batteryLevel.get()).isAtLeast(0);
            assertThat(batteryLevel.get()).isAtMost(100);
        } catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        
        // Unregister listener
        batteryMonitor.unregister(listener);
    }
    
    @Test
    public void testBatteryLevelBelowThreshold() throws InterruptedException
    {
        CountDownLatch belowThresholdLatch = new CountDownLatch(1);
        AtomicBoolean belowThresholdCalled = new AtomicBoolean(false);
        
        BatteryMonitor.IBatteryLevelListener listener = new BatteryMonitor.IBatteryLevelListener()
        {
            @Override
            public void onBatteryLevelChanged(int newLevel)
            {
                // Track level changes
            }
            
            @Override
            public void onBatteryLevelBelowThreshold(int currentLevel, int threshold)
            {
                assertThat(currentLevel).isEqualTo(BATTERY_LOW);
                assertThat(threshold).isEqualTo(TEST_THRESHOLD);
                belowThresholdCalled.set(true);
                belowThresholdLatch.countDown();
            }
            
            @Override
            public void onBatteryLevelAboveThreshold(int currentLevel, int threshold)
            {
                // Not expected in this test
            }
        };
        
        batteryMonitor.register(listener);
        batteryMonitor.startMonitoring();
        
        // Simulate battery level change to below threshold
        simulateBatteryLevelChange(BATTERY_HIGH); // Start above threshold
        SystemClock.sleep(100); // Small delay to ensure processing
        simulateBatteryLevelChange(BATTERY_LOW); // Drop below threshold
        
        // Wait for debounced notification (5 seconds + buffer)
        assertThat(belowThresholdLatch.await(DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(belowThresholdCalled.get()).isTrue();
        assertThat(batteryMonitor.isPausedDueToBattery()).isTrue();
    }
    
    @Test
    public void testBatteryLevelAboveThreshold() throws InterruptedException
    {
        CountDownLatch aboveThresholdLatch = new CountDownLatch(1);
        AtomicBoolean aboveThresholdCalled = new AtomicBoolean(false);
        
        BatteryMonitor.IBatteryLevelListener listener = new BatteryMonitor.IBatteryLevelListener()
        {
            @Override
            public void onBatteryLevelChanged(int newLevel)
            {
                // Track level changes
            }
            
            @Override
            public void onBatteryLevelBelowThreshold(int currentLevel, int threshold)
            {
                // Expected first when going below threshold
            }
            
            @Override
            public void onBatteryLevelAboveThreshold(int currentLevel, int threshold)
            {
                assertThat(currentLevel).isEqualTo(BATTERY_HIGH);
                assertThat(threshold).isEqualTo(TEST_THRESHOLD);
                aboveThresholdCalled.set(true);
                aboveThresholdLatch.countDown();
            }
        };
        
        batteryMonitor.register(listener);
        batteryMonitor.startMonitoring();
        
        // First go below threshold
        simulateBatteryLevelChange(BATTERY_LOW);
        SystemClock.sleep(DEBOUNCE_DELAY_MS); // Wait for debounce
        
        // Then go above threshold
        simulateBatteryLevelChange(BATTERY_HIGH);
        
        // Wait for debounced notification
        assertThat(aboveThresholdLatch.await(DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(aboveThresholdCalled.get()).isTrue();
        assertThat(batteryMonitor.isPausedDueToBattery()).isFalse();
    }
    
    @Test
    public void testDebouncing() throws InterruptedException
    {
        CountDownLatch levelChangeLatch = new CountDownLatch(3);
        CountDownLatch thresholdLatch = new CountDownLatch(1);
        AtomicInteger levelChangeCount = new AtomicInteger(0);
        AtomicInteger thresholdCrossCount = new AtomicInteger(0);
        
        BatteryMonitor.IBatteryLevelListener listener = new BatteryMonitor.IBatteryLevelListener()
        {
            @Override
            public void onBatteryLevelChanged(int newLevel)
            {
                levelChangeCount.incrementAndGet();
                levelChangeLatch.countDown();
            }
            
            @Override
            public void onBatteryLevelBelowThreshold(int currentLevel, int threshold)
            {
                thresholdCrossCount.incrementAndGet();
                thresholdLatch.countDown();
            }
            
            @Override
            public void onBatteryLevelAboveThreshold(int currentLevel, int threshold)
            {
                // Not expected in this test
            }
        };
        
        batteryMonitor.register(listener);
        batteryMonitor.startMonitoring();
        
        // Rapidly fluctuate battery level around threshold
        simulateBatteryLevelChange(BATTERY_HIGH); // Above threshold
        SystemClock.sleep(100);
        simulateBatteryLevelChange(BATTERY_LOW);  // Below threshold
        SystemClock.sleep(100);
        simulateBatteryLevelChange(BATTERY_HIGH); // Above threshold again (cancels debounce)
        SystemClock.sleep(100);
        simulateBatteryLevelChange(BATTERY_LOW);  // Below threshold again
        
        // Wait for level changes
        assertThat(levelChangeLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(levelChangeCount.get()).isAtLeast(3);
        
        // Wait for debounced threshold notification
        assertThat(thresholdLatch.await(DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS)).isTrue();
        
        // Only one threshold notification should occur due to debouncing
        assertThat(thresholdCrossCount.get()).isEqualTo(1);
    }
    
    @Test
    public void testReevaluateThreshold() throws InterruptedException
    {
        CountDownLatch belowThresholdLatch = new CountDownLatch(1);
        AtomicBoolean belowThresholdCalled = new AtomicBoolean(false);
        
        BatteryMonitor.IBatteryLevelListener listener = new BatteryMonitor.IBatteryLevelListener()
        {
            @Override
            public void onBatteryLevelChanged(int newLevel)
            {
                // Track level changes
            }
            
            @Override
            public void onBatteryLevelBelowThreshold(int currentLevel, int threshold)
            {
                belowThresholdCalled.set(true);
                belowThresholdLatch.countDown();
            }
            
            @Override
            public void onBatteryLevelAboveThreshold(int currentLevel, int threshold)
            {
                // Not expected in this test
            }
        };
        
        batteryMonitor.register(listener);
        batteryMonitor.startMonitoring();
        
        // Set battery level between old and new threshold
        simulateBatteryLevelChange(12); // Above 10 but below 15
        SystemClock.sleep(100);
        
        // Lower threshold so current level is now below it
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 13)
                .apply();
        
        // Force reevaluation
        batteryMonitor.reevaluateThreshold();
        
        // Should trigger below threshold notification
        assertThat(belowThresholdLatch.await(DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(belowThresholdCalled.get()).isTrue();
        assertThat(batteryMonitor.isPausedDueToBattery()).isTrue();
    }
    
    @Test
    public void testDisabledBatteryManagement()
    {
        // Disable battery management by setting threshold to 0
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 0)
                .apply();
        
        CountDownLatch thresholdLatch = new CountDownLatch(1);
        AtomicBoolean thresholdCalled = new AtomicBoolean(false);
        
        BatteryMonitor.IBatteryLevelListener listener = new BatteryMonitor.IBatteryLevelListener()
        {
            @Override
            public void onBatteryLevelChanged(int newLevel)
            {
                // Expected
            }
            
            @Override
            public void onBatteryLevelBelowThreshold(int currentLevel, int threshold)
            {
                thresholdCalled.set(true);
                thresholdLatch.countDown();
            }
            
            @Override
            public void onBatteryLevelAboveThreshold(int currentLevel, int threshold)
            {
                thresholdCalled.set(true);
                thresholdLatch.countDown();
            }
        };
        
        batteryMonitor.register(listener);
        batteryMonitor.startMonitoring();
        
        // Simulate battery below threshold
        simulateBatteryLevelChange(BATTERY_LOW);
        
        // Wait to ensure no threshold notification occurs
        try
        {
            assertThat(thresholdLatch.await(2, TimeUnit.SECONDS)).isFalse();
            assertThat(thresholdCalled.get()).isFalse();
            assertThat(batteryMonitor.isPausedDueToBattery()).isFalse();
        } catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    public void testGetCurrentBatteryLevel()
    {
        // Initially should be -1
        assertThat(batteryMonitor.getCurrentBatteryLevel()).isEqualTo(-1);
        
        batteryMonitor.startMonitoring();
        
        // After starting, should have a valid battery level
        SystemClock.sleep(500); // Give time for initial broadcast
        int level = batteryMonitor.getCurrentBatteryLevel();
        assertThat(level).isAtLeast(0);
        assertThat(level).isAtMost(100);
    }
    
    /**
     * Helper method to simulate battery level changes by broadcasting a battery changed intent.
     */
    private void simulateBatteryLevelChange(int level)
    {
        Intent batteryIntent = new Intent(Intent.ACTION_BATTERY_CHANGED);
        batteryIntent.putExtra(BatteryManager.EXTRA_LEVEL, level);
        batteryIntent.putExtra(BatteryManager.EXTRA_SCALE, 100);
        batteryIntent.putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING);
        
        // Call onReceive directly since we can't actually broadcast system intents
        batteryMonitor.onReceive(context, batteryIntent);
    }
}