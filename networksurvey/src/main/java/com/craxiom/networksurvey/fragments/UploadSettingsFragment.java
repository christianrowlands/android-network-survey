package com.craxiom.networksurvey.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.util.PreferenceUtils;

import timber.log.Timber;

public class UploadSettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener
{
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.upload_preferences, rootKey);
    }

    @Override
    public void onResume()
    {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this);

        super.onResume();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        Timber.d("onSharedPreferenceChanged(): Preference value changed: %s", key);
        if (NetworkSurveyConstants.PROPERTY_OCID_API_KEY.equals(key))
        {
            EditTextPreference apiKeyPreference = findPreference(NetworkSurveyConstants.PROPERTY_OCID_API_KEY);
            //noinspection DataFlowIssue
            String apiKeyValue = apiKeyPreference.getText();

            if (apiKeyValue == null) return;

            apiKeyValue = apiKeyValue.trim();
            Timber.d("onSharedPreferenceChanged(): User set API key = \"%s\"", apiKeyValue);
            boolean isApiKeyEmpty = TextUtils.isEmpty(apiKeyValue);
            if (!isApiKeyEmpty && !PreferenceUtils.isApiKeyValid(apiKeyValue))
            {
                Timber.d("onSharedPreferenceChanged(): User defined invalid API key = \"%s\"", apiKeyValue);
                Toast.makeText(getActivity(), "OpenCelliD API Key is invalid", Toast.LENGTH_LONG).show();
            }
        }
    }
}
