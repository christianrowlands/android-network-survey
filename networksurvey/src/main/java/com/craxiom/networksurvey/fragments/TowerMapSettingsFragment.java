package com.craxiom.networksurvey.fragments;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import com.craxiom.networksurvey.R;

/**
 * Settings UI Fragment for the Tower Map specific settings.
 */
public class TowerMapSettingsFragment extends PreferenceFragmentCompat
{
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.tower_map_preferences, rootKey);
    }
}
