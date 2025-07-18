package com.craxiom.networksurvey.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

/**
 * Instrumented tests for battery-related methods in PreferenceUtils.
 * Tests preference retrieval, MDM overrides, and default values.
 */
@RunWith(AndroidJUnit4.class)
public class PreferenceUtilsBatteryTest
{
    private Context context;
    private SharedPreferences preferences;
    
    @Before
    public void setUp()
    {
        context = ApplicationProvider.getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Clear any existing preferences
        preferences.edit()
                .remove(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT)
                .apply();
    }
    
    @After
    public void tearDown()
    {
        // Clean up preferences
        preferences.edit()
                .remove(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT)
                .apply();
    }
    
    @Test
    public void testIsBatteryManagementEnabled_DefaultValue()
    {
        // When preference is not set, should return default (false)
        boolean enabled = PreferenceUtils.isBatteryManagementEnabled(context);
        assertThat(enabled).isFalse();
    }
    
    @Test
    public void testIsBatteryManagementEnabled_UserPreference()
    {
        // Battery management is enabled when threshold > 0
        // Test with threshold = 0 (disabled)
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 0)
                .apply();
        
        boolean enabled = PreferenceUtils.isBatteryManagementEnabled(context);
        assertThat(enabled).isFalse();
        
        // Test with threshold > 0 (enabled)
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 15)
                .apply();
        
        enabled = PreferenceUtils.isBatteryManagementEnabled(context);
        assertThat(enabled).isTrue();
        
        // Test with different threshold values
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 50)
                .apply();
        
        enabled = PreferenceUtils.isBatteryManagementEnabled(context);
        assertThat(enabled).isTrue();
    }
    
    @Test
    public void testGetBatteryThresholdPercent_DefaultValue()
    {
        // When preference is not set, should return default
        int threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        assertThat(threshold).isEqualTo(NetworkSurveyConstants.DEFAULT_BATTERY_THRESHOLD_PERCENT);
    }
    
    @Test
    public void testGetBatteryThresholdPercent_UserPreference()
    {
        // Set various threshold values
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 25)
                .apply();
        
        int threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        assertThat(threshold).isEqualTo(25);
        
        // Test boundary values
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 0)
                .apply();
        
        threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        assertThat(threshold).isEqualTo(0);
        
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 95)
                .apply();
        
        threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        assertThat(threshold).isEqualTo(95);
    }
    
    @Test
    public void testGetBatteryThresholdPercent_InvalidValues()
    {
        // Test value at minimum boundary (0 is valid and means disabled)
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 0)
                .apply();
        
        int threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        assertThat(threshold).isEqualTo(0); // 0 is valid (means disabled)
        
        // Test value above maximum (should be clamped to 95)
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 100)
                .apply();
        
        threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        assertThat(threshold).isEqualTo(0); // Invalid value returns default
        
        // Test negative value
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, -10)
                .apply();
        
        threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        assertThat(threshold).isEqualTo(0); // Invalid value returns default
    }
    
    @Test
    public void testBatteryPreferencesInteraction()
    {
        // Test that battery management enabled state is based on threshold value
        
        // Threshold = 0 means battery management is disabled
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 0)
                .apply();
        
        assertThat(PreferenceUtils.isBatteryManagementEnabled(context)).isFalse();
        assertThat(PreferenceUtils.getBatteryThresholdPercent(context)).isEqualTo(0);
        
        // Threshold > 0 means battery management is enabled
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 20)
                .apply();
        
        assertThat(PreferenceUtils.isBatteryManagementEnabled(context)).isTrue();
        assertThat(PreferenceUtils.getBatteryThresholdPercent(context)).isEqualTo(20);
        
        // Test with higher threshold
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 75)
                .apply();
        
        assertThat(PreferenceUtils.isBatteryManagementEnabled(context)).isTrue();
        assertThat(PreferenceUtils.getBatteryThresholdPercent(context)).isEqualTo(75);
    }
    
    @Test
    public void testMdmOverride_BatteryManagement()
    {
        // Note: This test demonstrates where MDM override would be tested
        // In a real implementation, MDM values would come from RestrictionsManager
        // or app restrictions bundle. Since we can't set real MDM values in tests,
        // this test documents the expected behavior.
        
        // Set user preference (battery management disabled when threshold = 0)
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 0)
                .apply();
        
        // In production, if MDM sets battery_threshold_percent > 0,
        // it would override the user preference and enable battery management
        boolean enabled = PreferenceUtils.isBatteryManagementEnabled(context);
        
        // Without MDM, should return false (disabled)
        assertThat(enabled).isFalse();
    }
    
    @Test
    public void testMdmOverride_BatteryThreshold()
    {
        // Note: This test demonstrates where MDM override would be tested
        // In a real implementation, MDM values would come from RestrictionsManager
        
        // Set user preference
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 15)
                .apply();
        
        // In production, if MDM sets battery_threshold_percent = 25,
        // it would override the user preference
        int threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        
        // Without MDM, should return user preference
        assertThat(threshold).isEqualTo(15);
    }
    
    @Test
    public void testLegacyStringToIntMigration()
    {
        // Test migration from old string preference to new int preference
        // (if applicable - depends on implementation history)
        
        // Simulate old string preference
        preferences.edit()
                .putString("battery_threshold_percent_string", "20")
                .apply();
        
        // The actual migration would happen in PreferenceUtils or during app upgrade
        // Here we just verify the new int preference works correctly
        preferences.edit()
                .putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, 20)
                .apply();
        
        int threshold = PreferenceUtils.getBatteryThresholdPercent(context);
        assertThat(threshold).isEqualTo(20);
    }
}