package com.craxiom.networksurvey.fragments;

import android.content.Context;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;

import androidx.preference.DropDownPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;

import timber.log.Timber;

/**
 * A Settings Fragment to inflate the Preferences XML resource so the user can interact with the App's settings.
 *
 * @since 0.0.9
 */
public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener
{
    private static final String PASSWORD_NOT_SET_DISPLAY_TEXT = "not set";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        // Inflate the preferences XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS));
        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS));
        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS));

        final EditTextPreference mqttPasswordPreference = findPreference(NetworkSurveyConstants.PROPERTY_MQTT_PASSWORD);

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
        }

        updateUiForMdmIfNecessary();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        int defaultValue = -1;

        switch (key)
        {
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
            // If the new value is not a valid revert to the default value
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
     * Updates the UI if MDM values are available
     *
     * @since 0.3.0
     */
    private void updateUiForMdmIfNecessary()
    {
        if (!isUnderMdmControl()) return;

        final RestrictionsManager restrictionsManager = (RestrictionsManager) requireContext().getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
            final PreferenceScreen preferenceScreen = getPreferenceScreen();

            updateIntPreferenceForMdm(preferenceScreen, getString(R.string.log_rollover_dropdown_key), mdmProperties);
        }
    }

    /**
     * Updates an int preference value with an MDM value, if it exists.
     *
     * @param preferenceScreen The preference screen
     * @param preferenceKey    The key of the preference
     * @param mdmProperties    The MDM properties
     * @since 0.3.0
     */
    private void updateIntPreferenceForMdm(PreferenceScreen preferenceScreen, String preferenceKey, Bundle mdmProperties)
    {
        try
        {
            final DropDownPreference preference = preferenceScreen.findPreference(preferenceKey);

            if (preference != null)
            {
                final int mdmIntProperty = mdmProperties.getInt(preferenceKey, 0);

                preference.setEnabled(false);
                preference.setSummaryProvider(pref -> mdmIntProperty == 0 ? "Never" : String.valueOf(mdmIntProperty));
            }
        } catch (Exception e)
        {
            Timber.wtf(e, "Could not find the int preference or update the UI component for %s", preferenceKey);
        }
    }

    /**
     * @return True, if the restrictions manager is non-null, and if MDM properties exist for certain
     * preferences.
     * @since 0.3.0
     */
    private boolean isUnderMdmControl()
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) requireContext().getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            if (mdmProperties.getInt(getString(R.string.log_rollover_dropdown_key), 0) != 0)
            {
                Timber.i("Network Survey is under MDM control");
                return true;
            }
        }

        return false;
    }
}
