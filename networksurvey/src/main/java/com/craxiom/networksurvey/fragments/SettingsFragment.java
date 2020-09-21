package com.craxiom.networksurvey.fragments;

import android.os.Bundle;
import android.text.InputType;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;

/**
 * A Settings Fragment to inflate the Preferences XML resource so the user can interact with the App's settings.
 *
 * @since 0.0.9
 */
public class SettingsFragment extends PreferenceFragmentCompat
{
    private static final String PASSWORD_NOT_SET_DISPLAY_TEXT = "not set";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        // Inflate the preferences XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey);

        final EditTextPreference gnssScanIntervalPreference = findPreference(NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS);
        if (gnssScanIntervalPreference != null)
        {
            gnssScanIntervalPreference.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
        }

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
}
