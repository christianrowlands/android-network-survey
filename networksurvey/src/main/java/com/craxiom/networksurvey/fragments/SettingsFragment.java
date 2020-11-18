package com.craxiom.networksurvey.fragments;

import android.content.Context;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;

import androidx.preference.DropDownPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.util.MdmUtils;

import timber.log.Timber;

/**
 * A Settings Fragment to inflate the Preferences XML resource so the user can interact with the App's settings.
 *
 * @since 0.0.9
 */
public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener
{
    private static final String PASSWORD_NOT_SET_DISPLAY_TEXT = "not set";

    /**
     * The list of preferences that can be set in both the MDM app restrictions, and this settings UI.
     */
    private static final String[] PROPERTY_KEYS = {NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING,
            NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING,
            NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING,
            NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB,
            NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS,
            NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
            NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS,
            NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT};

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        // Inflate the preferences XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS));
        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS));
        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS));

        /*final EditTextPreference mqttPasswordPreference = findPreference(NetworkSurveyConstants.PROPERTY_MQTT_PASSWORD);

        if (mqttPasswordPreference != null)
        {
            mqttPasswordPreference.setSummaryProvider(preference1 -> {
                final String currentPassword = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(NetworkSurveyConstants.PROPERTY_MQTT_PASSWORD, "");

                return getAsterisks(currentPassword.length());
            });

            mqttPasswordPreference.setOnBindEditTextListener(
                    editText -> {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        mqttPasswordPreference.setSummaryProvider(preference -> getAsterisks(editText.getText().toString().length()));
                    });
        }*/

        updateUiForMdmIfNecessary();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        int defaultValue = -1;

        switch (key)
        {
            case NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY:
                final boolean mdmOverride = sharedPreferences.getBoolean(key, false);

                Timber.d("mdmOverride Preference Changed to %s", mdmOverride);

                if (mdmOverride)
                {
                    final PreferenceScreen preferenceScreen = getPreferenceScreen();
                    for (String preferenceKey : PROPERTY_KEYS)
                    {
                        final Preference preference = preferenceScreen.findPreference(preferenceKey);
                        if (preference != null) preference.setEnabled(true);
                    }
                } else
                {
                    updateUiForMdmIfNecessary();
                }
                break;

            case NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS:
                defaultValue = NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS;
                break;

            case NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS:
                defaultValue = NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS;
                break;

            case NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS:
                defaultValue = NetworkSurveyConstants.DEFAULT_GNSS_SCAN_INTERVAL_SECONDS;
                break;
        }

        if (defaultValue != -1)
        {
            // If the new value is not valid, revert to the default value
            try
            {
                Integer.parseInt(sharedPreferences.getString(key, ""));
            } catch (Exception e)
            {
                final SharedPreferences.Editor edit = sharedPreferences.edit();
                edit.putString(key, String.valueOf(defaultValue));
                edit.apply();
            }
        }
    }

    @Override
    public void onDestroyView()
    {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

        super.onDestroyView();
    }

    /**
     * @param length The number of asterisks to include in the string.
     * @return A string of asterisks that can be used to represent a password.  If the length is 0, then not_set is returned
     * @since 0.1.1
     */
    private String getAsterisks(int length)
    {
        if (length == 0) return PASSWORD_NOT_SET_DISPLAY_TEXT;

        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < length; s++)
        {
            sb.append("*");
        }
        return sb.toString();
    }

    /**
     * Sets {@link InputType#TYPE_CLASS_NUMBER} flag on the provided {@link EditTextPreference}.
     *
     * @param preference The preference to update.
     * @since 0.3.0
     */
    private void setPreferenceAsIntegerOnly(EditTextPreference preference)
    {
        if (preference != null)
        {
            preference.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
        } else
        {
            Timber.e("Could not find the preference to set it as integer numbers only.");
        }
    }

    /**
     * If the app is under MDM control, update the user preferences UI to reflect those MDM provided values. If the app
     * is not under MDM control, then do nothing.
     * <p>
     * Also, we need to check if the user has turned on the MDM override option. If so, then the values can
     * still be changed. If not, then we should disable all settings but still update the values so that the UI reflects
     * the MDM provided values.
     *
     * @since 0.4.0
     */
    private void updateUiForMdmIfNecessary()
    {
        if (!MdmUtils.isUnderMdmControl(requireContext(), PROPERTY_KEYS)) return;

        final SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();

        // Update the UI so that the MDM override is visible, and that some of the settings can't be changed
        final Preference overridePreference = getPreferenceScreen().findPreference(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY);
        if (overridePreference != null) overridePreference.setVisible(true);

        final boolean mdmOverride = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);

        if (mdmOverride) return; // Nothing to do because all the preferences are enabled by default.

        final PreferenceScreen preferenceScreen = getPreferenceScreen();

        final RestrictionsManager restrictionsManager = (RestrictionsManager) requireContext().getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager == null) return;

        final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
        if (mdmProperties == null) return;

        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING);
        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING);
        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING);
        updateLogRolloverSizeForMdm(preferenceScreen, mdmProperties);
        updateIntPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS);
        updateIntPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS);
        updateIntPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS);
        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT);
    }

    /**
     * Updates a boolean preference with an MDM value, if it exists. The shared preferences are
     * also updated, so that values are retained when MDM control is off.
     *
     * @param preferenceScreen The preference screen
     * @param mdmProperties    The map of mdm provided properties.
     * @param preferenceKey    The preference key
     * @since 0.4.0
     */
    private void updateBooleanPreferenceForMdm(PreferenceScreen preferenceScreen, Bundle mdmProperties, String preferenceKey)
    {
        try
        {
            final SwitchPreferenceCompat preference = preferenceScreen.findPreference(preferenceKey);

            if (preference != null && mdmProperties.containsKey(preferenceKey))
            {
                final boolean mdmBooleanProperty = mdmProperties.getBoolean(preferenceKey);

                preference.setEnabled(false);
                preference.setChecked(mdmBooleanProperty);

                getPreferenceManager().getSharedPreferences()
                        .edit()
                        .putBoolean(preferenceKey, mdmBooleanProperty)
                        .apply();
            }
        } catch (Exception e)
        {
            Timber.wtf(e, "Could not find the bool preferences or update the UI component for %s", preferenceKey);
        }
    }

    /**
     * Updates an integer preference with an MDM value, if it exists. The shared preferences are
     * also updated, so that values are retained when MDM control is off.
     *
     * @param preferenceScreen The preference screen
     * @param mdmProperties    The map of mdm provided properties.
     * @param preferenceKey    The preference key
     * @since 0.4.0
     */
    private void updateIntPreferenceForMdm(PreferenceScreen preferenceScreen, Bundle mdmProperties, String preferenceKey)
    {
        try
        {
            final EditTextPreference preference = preferenceScreen.findPreference(preferenceKey);

            if (preference != null && mdmProperties.containsKey(preferenceKey))
            {
                final int mdmIntProperty = mdmProperties.getInt(preferenceKey, -1);

                if (mdmIntProperty != -1)
                {
                    preference.setEnabled(false);

                    final String mdmValue = String.valueOf(mdmIntProperty);

                    preference.setSummaryProvider(pref -> mdmValue);

                    getPreferenceManager().getSharedPreferences()
                            .edit()
                            .putString(preferenceKey, String.valueOf(mdmIntProperty))
                            .apply();
                }
            }
        } catch (Exception e)
        {
            Timber.wtf(e, "Could not find the int preference or update the UI component for %s", preferenceKey);
        }
    }

    /**
     * Updates the log rollover preference with an MDM value, if it exists. The shared preferences
     * is also updated, so that values are retained when MDM control is off.
     *
     * @param preferenceScreen The preference screen
     * @since 0.4.0
     */
    private void updateLogRolloverSizeForMdm(PreferenceScreen preferenceScreen, Bundle mdmProperties)
    {
        final String preferenceKey = NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB;
        try
        {
            final DropDownPreference preference = preferenceScreen.findPreference(preferenceKey);

            if (preference != null && mdmProperties.containsKey(preferenceKey))
            {
                final int mdmIntProperty = mdmProperties.getInt(preferenceKey, -1);

                if (mdmIntProperty != -1)
                {
                    preference.setEnabled(false);

                    final String mdmValue = mdmIntProperty == 0 ? "Never" : String.valueOf(mdmIntProperty);
                    preference.setSummaryProvider(pref -> mdmValue);

                    getPreferenceManager().getSharedPreferences()
                            .edit()
                            .putString(preferenceKey, String.valueOf(mdmIntProperty))
                            .apply();
                }
            }
        } catch (Exception e)
        {
            Timber.wtf(e, "Could not find the int preference or update the UI component for %s", preferenceKey);
        }
    }
}
