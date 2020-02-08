package com.craxiom.networksurvey.fragments;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import com.craxiom.networksurvey.R;

/**
 * A Settings Fragment to inflate the Preferences XML resource so the user can interact with the App's settings.
 *
 * @since 0.0.9
 */
public class SettingsFragment extends PreferenceFragmentCompat
{
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        // Inflate the preferences XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }
}
