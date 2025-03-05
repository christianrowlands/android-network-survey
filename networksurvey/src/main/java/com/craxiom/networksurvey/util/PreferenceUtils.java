/*
 * Copyright (C) 2012-2018 Paul Watts (paulcwatts@gmail.com), Sean J. Barbeau (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.craxiom.networksurvey.util;

import static com.craxiom.mqttlibrary.MqttConstants.PROPERTY_MQTT_CLIENT_ID;
import static com.craxiom.mqttlibrary.MqttConstants.PROPERTY_MQTT_CONNECTION_HOST;
import static com.craxiom.mqttlibrary.MqttConstants.PROPERTY_MQTT_CONNECTION_PORT;
import static com.craxiom.mqttlibrary.MqttConstants.PROPERTY_MQTT_CONNECTION_TLS_ENABLED;
import static com.craxiom.mqttlibrary.MqttConstants.PROPERTY_MQTT_PASSWORD;
import static com.craxiom.mqttlibrary.MqttConstants.PROPERTY_MQTT_USERNAME;
import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.DEFAULT_LOCATION_PROVIDER;
import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.PROPERTY_KEY_ACCEPT_MAP_PRIVACY;
import static java.util.Collections.emptySet;

import android.content.Context;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import com.craxiom.mqttlibrary.MqttConstants;
import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings;
import com.craxiom.networksurvey.model.GnssType;
import com.craxiom.networksurvey.model.LogTypeState;
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo;
import com.craxiom.networksurvey.ui.cellular.model.TowerSource;

import org.osmdroid.util.BoundingBox;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import timber.log.Timber;

/**
 * A class containing utility methods related to preferences.
 * <p>
 * Originally from the GPS Test open source Android app.  https://github.com/barbeau/gpstest
 */
public class PreferenceUtils
{
    public static final int CAPABILITY_UNKNOWN = -1;
    public static final int CAPABILITY_NOT_SUPPORTED = 0;
    public static final int CAPABILITY_SUPPORTED = 1;
    public static final int CAPABILITY_LOCATION_DISABLED = 2;

    /**
     * A list of words to use when generating a random MQTT client ID.
     */
    private static final List<String> WORDS = Arrays.asList(
            "Sun", "Moon", "Star", "Sky", "River", "Ocean",
            "Mountain", "Valley", "Forest", "Desert", "Island",
            "Grass", "Dawn", "Twilight", "Morning", "Sunset",
            "Lake", "Stream", "Hill", "Field", "Meadow",
            "Cloud", "Rain", "Snow", "Wind", "Storm", "Zinc",
            "Fire", "Ice", "Earth", "Metal", "Wood", "Ulysses",
            "Apple", "Berry", "Cherry", "Date", "Elderberry",
            "Fig", "Grape", "Honeydew", "Ivy", "Jackfruit",
            "Kiwi", "Lemon", "Mango", "Nectarine", "Olive",
            "Peach", "Quince", "Raspberry", "Strawberry", "Tomato",
            "Ugli", "Vanilla", "Walnut", "Xigua", "Yam", "Zucchini",
            "Amber", "Blue", "Crimson", "Diamond", "Emerald",
            "Fuchsia", "Gold", "Heliotrope", "Ivory", "Jade",
            "Khaki", "Lavender", "Magenta", "Green", "Ochre",
            "Periwinkle", "Orange", "Purple", "Red", "Silver",
            "Pink", "Quartz", "Ruby", "Sapphire", "Turquoise",
            "Ultramarine", "Violet", "Wheat", "Xanadu", "Yellow",
            "Falcon", "Tiger", "Dolphin", "Elephant", "Giraffe",
            "Lynx", "Octopus", "Panther", "Quail", "Rhino",
            "Shark", "Tucan", "Unicorn", "Viper", "Wolf",
            "Yak", "Zebra", "Yeats", "Zoroaster", "Voltaire",
            "Austin", "Berlin", "Cairo", "Denver", "Edinburgh",
            "Florence", "Geneva", "Havana", "Istanbul", "Jakarta",
            "Kyoto", "Lisbon", "Madrid", "Nairobi", "Oslo",
            "Paris", "Quebec", "Rome", "Sydney", "Tokyo",
            "Utrecht", "Vienna", "Warsaw", "Xian", "York", "Zurich",
            "Browser", "Digital", "Ethernet", "Xenophon",
            "Keyboard", "Football", "Lamp", "Bat", "Oracle",
            "Python", "Quantum", "Router", "Silicon", "Tablet",
            "Uranium", "Virus", "Worm", "Xerox", "Yagi", "Zombie",
            "Angel", "Banshee", "Cyclops", "Demon", "Elf",
            "Griffin", "Hydra", "Mermaid", "Nymph", "Ogre",
            "Phoenix", "Quetzalcoatl", "Sasquatch", "Titan",
            "Valkyrie", "Werewolf", "Yeti", "Zeus", "Washington",
            "Aristotle", "Beethoven", "Cleopatra", "Darwin", "Einstein",
            "Freud", "Galileo", "Hippocrates", "Imhotep", "Joan",
            "Kafka", "Leonardo", "Mozart", "Newton", "Orwell",
            "Plato", "QueenVictoria", "Rousseau", "Shakespeare"
    );

    private static final Random RANDOM = new Random();

    /**
     * Gets the scan rate preference associated with the provide preference key.
     * <p>
     * First, this method tries to pull the MDM provided scan rate. If it is not set (either because the device is not
     * under MDM control, or if that specific value is not set by the MDM administrator) then the value is pulled from
     * the Android Shared Preferences (aka from the user settings). If it is not set there then the provided default
     * value is used.
     * <p>
     * The only exception to this sequence is that if the user has toggled the MDM override switch in user settings,
     * then the user preference value will be used instead of the MDM value.
     *
     * @param scanRatePreferenceKey  The preference key to use when pulling the scan rate from MDM and Shared Preferences.
     * @param defaultScanRateSeconds The default scan rate to fall back on if the scan rate could not be found.
     * @param context                The context to use when getting the Shared Preferences and Restriction Manager.
     * @return The scan rate to use.
     * @since 0.3.0
     */
    public static int getScanRatePreferenceMs(String scanRatePreferenceKey, int defaultScanRateSeconds, Context context)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);

        final boolean mdmOverride = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);

        // First try to use the MDM provided value.
        if (restrictionsManager != null && !mdmOverride)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            final int scanRateSeconds = mdmProperties.getInt(scanRatePreferenceKey);
            if (scanRateSeconds > 0)
            {
                return scanRateSeconds * 1_000;
            }
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Next, try to use the value from user preferences.
        final String scanInterval = preferences.getString(scanRatePreferenceKey,
                String.valueOf(defaultScanRateSeconds));
        try
        {
            final int scanRateSeconds = Integer.parseInt(scanInterval);
            return scanRateSeconds > 0 ? scanRateSeconds * 1_000 : defaultScanRateSeconds * 1_000;
        } catch (Exception e)
        {
            Timber.e(e, "Could not convert the GNSS scan interval user preference (%s) to an int", scanInterval);
            return defaultScanRateSeconds * 1_000;
        }
    }

    /**
     * Gets the auto start preference associated with the provide preference key.
     * <p>
     * First, this method tries to pull the MDM provided auto start value. If it is not set (either because the device
     * is not under MDM control, or if that specific value is not set by the MDM administrator) then the value is pulled
     * from the Android Shared Preferences (aka from the user settings). If it is not set there then the provided default
     * value is used.
     * <p>
     * The only exception to this sequence is that if the user has toggled the MDM override switch in user settings,
     * then the user preference value will be used instead of the MDM value.
     *
     * @param autoStartPreferenceKey The preference key to use when pulling the value from MDM and Shared Preferences.
     * @param defaultAutoStart       The default auto start value to fall back on if it could not be found.
     * @param context                The context to use when getting the Shared Preferences and Restriction Manager.
     * @return The auto start preference to use.
     * @since 0.4.0
     */
    public static boolean getAutoStartPreference(String autoStartPreferenceKey, boolean defaultAutoStart, Context context)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);

        final boolean mdmOverride = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);

        // First try to use the MDM provided value.
        if (restrictionsManager != null && !mdmOverride)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            if (mdmProperties.containsKey(autoStartPreferenceKey))
            {
                return mdmProperties.getBoolean(autoStartPreferenceKey);
            }
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Next, try to use the value from user preferences, with a default fallback
        return preferences.getBoolean(autoStartPreferenceKey, defaultAutoStart);
    }

    /**
     * Gets the maximum file size preference. Once this file size is reached, the log file should be closed and a new
     * one started.
     * <p>
     * First, this method tries to pull the MDM provided rollover size. If it is not set (either because the device is
     * not under MDM control, or if that specific value is not set by the MDM administrator) then the value is pulled
     * from the Android Shared Preferences (aka from the user settings). If it is not set there then the default value
     * is used.
     * <p>
     * The only exception to this sequence is that if the user has toggled the MDM override switch in user settings,
     * then the user preference value will be used instead of the MDM value.
     *
     * @param context The context to use when getting the Shared Preferences and Restriction Manager.
     * @return The maximum file size to use.
     * @since 0.4.0
     */
    public static int getRolloverSizePreference(Context context)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);

        final boolean mdmOverride = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);

        // First try to use the MDM provided value.
        if (restrictionsManager != null && !mdmOverride)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            if (mdmProperties.containsKey(NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB))
            {
                final int logRolloverSizeMb = mdmProperties.getInt(NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB);
                if (logRolloverSizeMb >= 0) return logRolloverSizeMb;
            }
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Next, try to use the value from user preferences.
        final String rolloverPreferenceString = preferences.getString(NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB, NetworkSurveyConstants.DEFAULT_ROLLOVER_SIZE_MB);
        try
        {
            final int logRolloverSizeMb = Integer.parseInt(rolloverPreferenceString);
            if (logRolloverSizeMb >= 0) return logRolloverSizeMb;
        } catch (Exception e)
        {
            Timber.e(e, "Could not convert the log rollover size user preference (%s) to an int", rolloverPreferenceString);
        }

        return Integer.parseInt(NetworkSurveyConstants.DEFAULT_ROLLOVER_SIZE_MB);
    }

    /**
     * Gets the log file type preference.
     * <p>
     * First, this method tries to pull the MDM provided log file type. If it is not set (either because the device
     * is not under MDM control, or if that specific value is not set by the MDM administrator) then the value is pulled
     * from the Android Shared Preferences (aka from the user settings). If it is not set there then the default
     * value is used.
     * <p>
     * The only exception to this sequence is that if the user has toggled the MDM override switch in user settings,
     * then the user preference value will be used instead of the MDM value.
     *
     * @param context The context to use when getting the Shared Preferences and Restriction Manager.
     * @return A wrapper object that contains flags indicating which file types are enabled.
     */
    public static LogTypeState getLogTypePreference(Context context)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);

        final boolean mdmOverride = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);

        // First try to use the MDM provided value.
        if (restrictionsManager != null && !mdmOverride)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            if (mdmProperties.containsKey(NetworkSurveyConstants.PROPERTY_LOG_FILE_TYPE))
            {
                return convertIndexToLogTypeState(String.valueOf(mdmProperties.getInt(NetworkSurveyConstants.PROPERTY_LOG_FILE_TYPE)));
            }
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Next, try to use the value from user preferences, with a default fallback
        return convertIndexToLogTypeState(preferences.getString(NetworkSurveyConstants.PROPERTY_LOG_FILE_TYPE, "2"));
    }

    /**
     * Gets the location provider preference
     * <p>
     * First, this method tries to pull the MDM provided location provider. If it is not set (either because the device
     * is not under MDM control, or if that specific value is not set by the MDM administrator) then the value is pulled
     * from the Android Shared Preferences (aka from the user settings). If it is not set there then the default
     * value is used.
     * <p>
     * The only exception to this sequence is that if the user has toggled the MDM override switch in user settings,
     * then the user preference value will be used instead of the MDM value.
     *
     * @param context The context to use when getting the Shared Preferences and Restriction Manager.
     * @return An int representing the location provider. See {@link values/arrays.xml:location_provider_option_labels}
     * for a mapping of the values.
     */
    public static int getLocationProviderPreference(Context context)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);

        final boolean mdmOverride = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);

        // First try to use the MDM provided value.
        if (restrictionsManager != null && !mdmOverride)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            if (mdmProperties.containsKey(NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER))
            {
                return mdmProperties.getInt(NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER, DEFAULT_LOCATION_PROVIDER);
            }
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Next, try to use the value from user preferences, with a default fallback
        String locationProviderString = preferences.getString(NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER, String.valueOf(DEFAULT_LOCATION_PROVIDER));
        try
        {
            return Integer.parseInt(locationProviderString);
        } catch (NumberFormatException e)
        {
            Timber.e(e, "Could not parse the location provider string: %s", locationProviderString);
            return DEFAULT_LOCATION_PROVIDER;
        }
    }

    /**
     * Gets the preference for allowing intent control.
     * <p>
     * First, this method tries to pull the MDM provided allow intent control value. If it is not set (either because the device
     * is not under MDM control, or if that specific value is not set by the MDM administrator) then the value is pulled
     * from the Android Shared Preferences (aka from the user settings). If it is not set there then the default
     * value is used.
     * <p>
     * The only exception to this sequence is that if the user has toggled the MDM override switch in user settings,
     * then the user preference value will be used instead of the MDM value.
     *
     * @param context The context to use when getting the Shared Preferences and Restriction Manager.
     * @return True if the app should allow intent control, false otherwise.
     */
    public static boolean getAllowIntentControlPreference(Context context)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);

        final boolean mdmOverride = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);

        // First try to use the MDM provided value.
        if (restrictionsManager != null && !mdmOverride)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            if (mdmProperties.containsKey(NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL))
            {
                return mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL);
            }
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Next, try to use the value from user preferences, with a default fallback
        return preferences.getBoolean(NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL, true);
    }

    /**
     * Gets the preference for ignoring the WiFi scan throttling warning.
     * <p>
     * First, this method tries to pull the MDM provided auto start value. If it is not set (either because the device
     * is not under MDM control, or if that specific value is not set by the MDM administrator) then the value is pulled
     * from the Android Shared Preferences (aka from the user settings). If it is not set there then the provided default
     * value is used.
     * <p>
     * Note: The MDM Override does not apply to this preference because it should be up to the MDM admin to decide
     * if developer options have been disabled. If they have, then the user toggling the MDM override switch does not
     * change anything.
     *
     * @param defaultValue The default value to fall back on if it could not be found.
     * @param context      The context to use when getting the Shared Preferences and Restriction Manager.
     * @return True if the Wi-Fi scan throttling warning should not be displayed, false if it should be displayed if
     * throttling is enabled.
     */
    public static boolean getIgnoreWifiThrottlingWarningPreference(boolean defaultValue, Context context)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        String propertyKey = NetworkSurveyConstants.PROPERTY_IGNORE_WIFI_SCAN_THROTTLING_WARNING;

        // First try to use the MDM provided value.
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            if (mdmProperties.containsKey(propertyKey))
            {
                return mdmProperties.getBoolean(propertyKey);
            }
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Next, try to use the value from user preferences, with a default fallback
        return preferences.getBoolean(propertyKey, defaultValue);
    }

    /**
     * Converts the user preference index to a wrapper object that contains the flags indicating
     * which file types are enabled.
     */
    private static LogTypeState convertIndexToLogTypeState(String index)
    {
        boolean csv = false;
        boolean geoPackage = false;
        switch (index)
        {
            case "0" -> // CSV Only
                    csv = true;
            case "1" -> // GeoPackage Only
                    geoPackage = true;
            case "2" ->
            { // Both
                csv = true;
                geoPackage = true;
            }
            default ->
            {
                Timber.wtf("Unhandled log type setting=%s", index);
                csv = true;
                geoPackage = true;
            }
        }

        return new LogTypeState(csv, geoPackage);
    }

    /**
     * Gets the auto start MQTT connection preference.
     * <p>
     * First, this method tries to pull the MDM provided auto start MQTT value. If it is not set (either because the device
     * is not under MDM control, or if that specific value is not set by the MDM administrator) then the value is pulled
     * from the Android Shared Preferences (aka from the user settings). If it is not set there then the provided default
     * value is used.
     * <p>
     * The only exception to this sequence is that if the user has toggled the MDM override switch in user settings,
     * then the user preference value will be used instead of the MDM value.
     *
     * @param context The context to use when getting the Shared Preferences and Restriction Manager.
     * @return True if the MQTT connection should be started when the phone is booted, false otherwise.
     * @since 0.4.0
     */
    public static boolean getMqttStartOnBootPreference(Context context)
    {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean mdmOverride = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);

        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        Bundle mdmProperties = null;
        if (restrictionsManager != null)
        {
            mdmProperties = restrictionsManager.getApplicationRestrictions();
        }

        if (!mdmOverride
                && mdmProperties != null
                && mdmProperties.containsKey(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT))
        {
            Timber.i("Using the MDM MQTT auto start preference");
            return mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT);
        } else
        {
            Timber.i("Using the user MQTT auto start preference");

            return sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT, false);
        }
    }

    public static void saveString(SharedPreferences prefs, String key, String value)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(key, value);

        edit.apply();
    }

    public static void saveString(String key, String value)
    {
        saveString(Application.getPrefs(), key, value);
    }

    public static void saveInt(SharedPreferences prefs, String key, int value)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt(key, value);

        edit.apply();
    }

    public static void saveInt(String key, int value)
    {
        saveInt(Application.getPrefs(), key, value);
    }

    public static void saveLong(SharedPreferences prefs, String key, long value)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong(key, value);

        edit.apply();
    }

    public static void saveLong(String key, long value)
    {
        saveLong(Application.getPrefs(), key, value);
    }

    public static void saveBoolean(SharedPreferences prefs, String key, boolean value)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(key, value);

        edit.apply();
    }

    public static void saveBoolean(String key, boolean value)
    {
        saveBoolean(Application.getPrefs(), key, value);
    }

    public static void saveFloat(SharedPreferences prefs, String key, float value)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putFloat(key, value);

        edit.apply();
    }

    public static void saveFloat(String key, float value)
    {
        saveFloat(Application.getPrefs(), key, value);
    }

    public static void saveDouble(SharedPreferences prefs, String key, double value)
    {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong(key, Double.doubleToRawLongBits(value));

        edit.apply();
    }

    public static void saveDouble(String key, double value)
    {
        saveDouble(Application.getPrefs(), key, value);
    }

    /**
     * Gets a double for the provided key from preferences, or the default value if the preference
     * doesn't currently have a value
     *
     * @param key          key for the preference
     * @param defaultValue the default value to return if the key doesn't have a value
     * @return a double from preferences, or the default value if it doesn't exist
     */
    public static Double getDouble(String key, double defaultValue)
    {
        if (!Application.getPrefs().contains(key))
        {
            return defaultValue;
        }
        return Double.longBitsToDouble(Application.getPrefs().getLong(key, 0));
    }

    /**
     * Gets a boolean for the provided key from preferences, or the default value if the preference
     * doesn't currently have a value
     *
     * @param key          key for the preference
     * @param defaultValue the default value to return if the key doesn't have a value
     * @return a boolean from preferences, or the default value if it doesn't exist
     */
    public static boolean getBoolean(String key, boolean defaultValue)
    {
        return Application.getPrefs().getBoolean(key, defaultValue);
    }

    public static String getString(String key, SharedPreferences prefs)
    {
        return prefs.getString(key, null);
    }

    public static long getLong(SharedPreferences prefs, String key, long defaultValue)
    {
        return prefs.getLong(key, defaultValue);
    }

    public static float getFloat(SharedPreferences prefs, String key, float defaultValue)
    {
        return prefs.getFloat(key, defaultValue);
    }

    /**
     * Returns the currently selected satellite sort order as the index in R.array.sort_sats
     *
     * @return the currently selected satellite sort order as the index in R.array.sort_sats
     */
    public static int getSatSortOrderFromPreferences(Context context, SharedPreferences prefs)
    {
        Resources r = context.getResources();
        String[] sortOptions = r.getStringArray(R.array.sort_sats);
        String sortPref = prefs.getString(r.getString(
                R.string.pref_key_default_sat_sort), sortOptions[0]);
        for (int i = 0; i < sortOptions.length; i++)
        {
            if (sortPref.equalsIgnoreCase(sortOptions[i]))
            {
                return i;
            }
        }
        return 0;  // Default to the first option
    }

    /**
     * Gets a set of GnssTypes that should have their satellites displayed that has been saved to preferences. (All are shown if empty or null)
     *
     * @return a set of GnssTypes that should have their satellites displayed that has been saved to preferences. (All are shown if empty or null)
     */
    public static Set<GnssType> gnssFilter(Context context, SharedPreferences prefs)
    {
        Set<GnssType> filter = new LinkedHashSet<>();
        Resources r = context.getResources();
        String filterString = getString(r.getString(R.string.pref_key_default_sat_filter), prefs);
        if (filterString == null)
        {
            return filter;
        }
        String[] parsedFilter = filterString.split(",");
        for (String s : parsedFilter)
        {
            GnssType gnssType = GnssType.fromString(s);
            if (gnssType != null)
            {
                filter.add(gnssType);
            }
        }
        return filter;
    }

    /**
     * Saves a set of GnssTypes that should have their satellites displayed to preferences. (All are shown if empty or null)
     * Values are persisted as string of comma-separated values, with each of the enum values .toString() called
     *
     * @param filter a set of GnssTypes that should have their satellites displayed. (All are shown if empty or null)
     */
    public static void saveGnssFilter(Context context, Set<GnssType> filter, SharedPreferences prefs)
    {
        Resources r = context.getResources();
        StringBuilder filterString = new StringBuilder();
        for (GnssType gnssType : filter)
        {
            filterString.append(gnssType.toString()).append(",");
        }
        // Remove the last comma (if there was at least one entry)
        if (!filter.isEmpty())
        {
            filterString.deleteCharAt(filterString.length() - 1);
        }
        saveString(prefs, r.getString(R.string.pref_key_default_sat_filter), filterString.toString());
    }

    /**
     * Clears any active GNSS filter so all satellites are displayed
     */
    public static void clearGnssFilter(Context context, SharedPreferences prefs)
    {
        saveGnssFilter(context, emptySet(), prefs);
    }

    /**
     * Removes the specified preference by deleting it
     */
    public static void remove(String key)
    {
        SharedPreferences.Editor edit = Application.getPrefs().edit();
        edit.remove(key).apply();
    }

    public static void populatePrefsFromMqttConnectionSettings(MqttConnectionSettings mqttConnectionSettings, Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = preferences.edit();
        if (mqttConnectionSettings.host() != null)
        {
            edit.putString(PROPERTY_MQTT_CONNECTION_HOST, mqttConnectionSettings.host());
        }
        if (mqttConnectionSettings.port() != 0)
        {
            edit.putInt(PROPERTY_MQTT_CONNECTION_PORT, mqttConnectionSettings.port());
        }
        if (mqttConnectionSettings.tlsEnabled() != null)
        {
            edit.putBoolean(PROPERTY_MQTT_CONNECTION_TLS_ENABLED, mqttConnectionSettings.tlsEnabled());
        }
        if (mqttConnectionSettings.deviceName() != null)
        {
            edit.putString(PROPERTY_MQTT_CLIENT_ID, mqttConnectionSettings.deviceName());
        }
        if (mqttConnectionSettings.mqttUsername() != null)
        {
            edit.putString(PROPERTY_MQTT_USERNAME, mqttConnectionSettings.mqttUsername());
        }
        if (mqttConnectionSettings.mqttPassword() != null)
        {
            edit.putString(PROPERTY_MQTT_PASSWORD, mqttConnectionSettings.mqttPassword());
        }
        if (mqttConnectionSettings.mqttTopicPrefix() != null)
        {
            edit.putString(MqttConstants.PROPERTY_MQTT_TOPIC_PREFIX, mqttConnectionSettings.mqttTopicPrefix());
        }
        if (mqttConnectionSettings.cellularStreamEnabled() != null)
        {
            edit.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, mqttConnectionSettings.cellularStreamEnabled());
        }
        if (mqttConnectionSettings.wifiStreamEnabled() != null)
        {
            edit.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, mqttConnectionSettings.wifiStreamEnabled());
        }
        if (mqttConnectionSettings.bluetoothStreamEnabled() != null)
        {
            edit.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, mqttConnectionSettings.bluetoothStreamEnabled());
        }
        if (mqttConnectionSettings.gnssStreamEnabled() != null)
        {
            edit.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, mqttConnectionSettings.gnssStreamEnabled());
        }
        if (mqttConnectionSettings.deviceStatusStreamEnabled() != null)
        {
            edit.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, mqttConnectionSettings.deviceStatusStreamEnabled());
        }

        edit.apply();
    }

    /**
     * Saves the provided MQTT Protocol Streaming flags to the shared preferences. Not all the
     * {@link MqttConnectionInfo} parameters are saved in this method; instead, just the streaming
     * flags are saved.
     */
    public static void saveMqttStreamFlags(MqttConnectionInfo info, Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, info.isCellularStreamEnabled());
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, info.isWifiStreamEnabled());
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, info.isBluetoothStreamEnabled());
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, info.isGnssStreamEnabled());
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, info.isDeviceStatusStreamEnabled());

        editor.apply();
    }

    /**
     * Checks to see if the MQTT client ID is set in the shared preferences. If it is not, then a
     * random client ID is generated and saved to the shared preferences. This is to improve the
     * UX for the user, to make it easier to fill out the MQTT connection UI.
     */
    public static void populateRandomMqttClientIdIfMissing(Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String mqttClientId = preferences.getString(PROPERTY_MQTT_CLIENT_ID, "");
        if (mqttClientId.isEmpty())
        {
            final SharedPreferences.Editor edit = preferences.edit();
            edit.putString(PROPERTY_MQTT_CLIENT_ID, generateClientID());
            edit.apply();
        }
    }

    /**
     * @return A random MQTT client ID that is a combination of two words and a number.
     */
    private static String generateClientID()
    {
        final int wordsSize = WORDS.size();
        final int index1 = RANDOM.nextInt(wordsSize);
        int index2 = RANDOM.nextInt(wordsSize);
        while (index1 == index2)
        {
            index2 = RANDOM.nextInt(wordsSize);
        }

        String word1 = WORDS.get(index1);
        String word2 = WORDS.get(index2);
        final int number = RANDOM.nextInt(100); // Generates a number from 0 to 99

        return word1 + word2 + number;
    }

    public static boolean hasAcceptedMapPrivacy(Context context)
    {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PROPERTY_KEY_ACCEPT_MAP_PRIVACY, false);
    }

    public static void setAcceptMapPrivacy(Context context, boolean hasAccepted)
    {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PROPERTY_KEY_ACCEPT_MAP_PRIVACY, hasAccepted).apply();
    }

    public static boolean hasDeniedBackgroundLocationPermission(Context context)
    {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(NetworkSurveyConstants.PROPERTY_KEY_DENIED_BACKGROUND_LOCATION_PERMISSION, false);
    }

    public static void denyBackgroundLocationPermission(Context context)
    {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(NetworkSurveyConstants.PROPERTY_KEY_DENIED_BACKGROUND_LOCATION_PERMISSION, true).apply();
    }

    public static boolean isApiKeyValid(String apiKey)
    {
        // old 8 motions - e.g. "9743a66f914cc249efca164485a19c5c"
        // new ENAiKOON - guid, e.g. "9743a66f-914c-c249-efca-164485a19c5c"
        // admin ENAiKOON - there are some custom keys defined by administrators
        // old Unwired Labs - e.g. "9743a66f914cc2"
        // new Unwired Labs - e.g. "pk.9743a66f914cc249efca164485a19c5c"
        return (apiKey.matches("pk\\.[a-fA-F0-9]{32}") || apiKey.matches("[a-fA-F0-9]{14}") || apiKey.matches("[a-fA-F0-9]{32}") || apiKey.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"));
    }

    public static String getUserOcidApiKey(Context context)
    {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(NetworkSurveyConstants.PROPERTY_OCID_API_KEY, "").trim();
    }

    public static String getSharedApiKey(Context context)
    {
        return BuildConfig.OCID_API_KEY;
    }

    public static boolean isApiKeyShared(String apiKey)
    {
        return BuildConfig.OCID_API_KEY.equalsIgnoreCase(apiKey);
    }

    public static String getOpenCelliDApiKey(Context context, boolean anonymousUploadToOcid)
    {
        if (anonymousUploadToOcid)
        {
            return getSharedApiKey(context);
        }

        // Retrieve the user-set API key from preferences
        String userApiKey = getUserOcidApiKey(context);

        // Check if the user-provided API key is valid
        if (isApiKeyValid(userApiKey))
        {
            return userApiKey;
        }

        // If not valid or not set, fallback to the shared API key
        return getSharedApiKey(context);
    }

    public static boolean displayServingCellCoverageOnMap(Context context)
    {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(NetworkSurveyConstants.PROPERTY_MAP_DISPLAY_SERVING_CELL_COVERAGE, true);
    }

    public static void setLastSelectedTowerSource(Context context, TowerSource towerSource)
    {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(NetworkSurveyConstants.PROPERTY_LAST_SELECTED_TOWER_SOURCE, towerSource.toString()).apply();
    }

    public static TowerSource getLastSelectedTowerSource(Context context)
    {
        String savedTowerSourceString = PreferenceManager.getDefaultSharedPreferences(context).getString(NetworkSurveyConstants.PROPERTY_LAST_SELECTED_TOWER_SOURCE, TowerSource.OpenCelliD.toString());

        return TowerSource.valueOf(savedTowerSourceString);
    }

    public static void saveTowerMapViewBoundingBox(Context context, BoundingBox boundingBox)
    {
        if (boundingBox == null) return;

        String boundingBoxString = String.format(Locale.US, "%f,%f,%f,%f",
                boundingBox.getLatNorth(),
                boundingBox.getLonEast(),
                boundingBox.getLatSouth(),
                boundingBox.getLonWest());

        SharedPreferences prefs = context.getSharedPreferences(NetworkSurveyConstants.TOWER_MAP_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        prefs.edit().putString(NetworkSurveyConstants.PROPERTY_LAST_TOWER_MAP_VIEW_LOCATION, boundingBoxString).apply();
    }

    public static BoundingBox getBoundingBoxFromPreferences(Context context)
    {
        SharedPreferences prefs = context.getSharedPreferences(NetworkSurveyConstants.TOWER_MAP_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        String boundingBoxString = prefs.getString(NetworkSurveyConstants.PROPERTY_LAST_TOWER_MAP_VIEW_LOCATION, null);

        if (boundingBoxString == null) return null;

        String[] parts = boundingBoxString.split(",");
        if (parts.length != 4) return null;

        try
        {
            double latNorth = Double.parseDouble(parts[0]);
            double lonEast = Double.parseDouble(parts[1]);
            double latSouth = Double.parseDouble(parts[2]);
            double lonWest = Double.parseDouble(parts[3]);
            return new BoundingBox(latNorth, lonEast, latSouth, lonWest);
        } catch (NumberFormatException e)
        {
            Timber.e(e, "Error parsing the save tower map view bounding box from preferences");
            return null;
        }
    }
}
