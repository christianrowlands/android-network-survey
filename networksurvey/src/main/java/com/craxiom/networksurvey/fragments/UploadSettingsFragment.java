package com.craxiom.networksurvey.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.logging.db.SurveyDatabase;
import com.craxiom.networksurvey.util.CredentialSecureStorage;
import com.craxiom.networksurvey.util.MdmUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;

import timber.log.Timber;

public class UploadSettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener
{
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.upload_preferences, rootKey);

        final Preference deletePreference = findPreference(NetworkSurveyConstants.PROPERTY_DELETE_ALL_DATA_IN_UPLOAD_DATABASE);
        if (deletePreference != null)
        {
            deletePreference.setOnPreferenceClickListener(preference -> {
                showDeleteConfirmationDialog(requireContext());
                return true;
            });
        }

        updateAutoUploadPreferencesForMdm();
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
            boolean isApiKeyEmpty = TextUtils.isEmpty(apiKeyValue);
            if (!isApiKeyEmpty && !PreferenceUtils.isApiKeyValid(apiKeyValue))
            {
                Toast.makeText(getActivity(), "OpenCelliD API Key is invalid", Toast.LENGTH_LONG).show();
            }

            // Move the API key from plain-text SharedPreferences to encrypted secure storage.
            // Unregister listener before removing to prevent re-entry from the remove callback.
            if (!isApiKeyEmpty)
            {
                Context context = getContext();
                if (context != null)
                {
                    CredentialSecureStorage.INSTANCE.storeOcidApiKey(context, apiKeyValue);
                    sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
                    sharedPreferences.edit()
                            .remove(NetworkSurveyConstants.PROPERTY_OCID_API_KEY)
                            .commit();
                    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
                }
            }
        }
    }

    private void showDeleteConfirmationDialog(Context context)
    {
        new AlertDialog.Builder(context)
                .setTitle(R.string.delete_upload_data_confirm_title)
                .setMessage(R.string.delete_upload_data_confirm_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteUploadData(context))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * If the app is under MDM control, update the auto-upload preferences to reflect
     * MDM provided values and disable the UI controls.
     */
    private void updateAutoUploadPreferencesForMdm()
    {
        Context context = getContext();
        if (context == null) return;

        if (!MdmUtils.isUnderMdmControl(context, SettingsFragment.MDM_OVERLAP_PROPERTY_KEYS))
        {
            return;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean mdmOverride = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);
        if (mdmOverride) return;

        RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager == null) return;

        Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
        if (mdmProperties == null) return;

        updateBooleanPreferenceForMdm(mdmProperties, NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED);
        updateBooleanPreferenceForMdm(mdmProperties, NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_WIFI_ONLY);
    }

    /**
     * Updates a boolean preference with an MDM value if it exists.
     */
    private void updateBooleanPreferenceForMdm(Bundle mdmProperties, String preferenceKey)
    {
        try
        {
            SwitchPreferenceCompat preference = findPreference(preferenceKey);
            if (preference != null && mdmProperties.containsKey(preferenceKey))
            {
                boolean mdmValue = mdmProperties.getBoolean(preferenceKey);
                preference.setEnabled(false);
                preference.setChecked(mdmValue);

                PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit()
                        .putBoolean(preferenceKey, mdmValue)
                        .apply();
            }
        } catch (Exception e)
        {
            Timber.e(e, "Could not update MDM preference for %s", preferenceKey);
        }
    }

    private void deleteUploadData(Context context)
    {
        new Thread(() -> {
            try
            {
                SurveyDatabase.getInstance(context).surveyRecordDao().deleteAllRecords();

                requireActivity().runOnUiThread(() ->
                        Toast.makeText(context, R.string.delete_upload_data_success, Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e)
            {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(context, R.string.delete_upload_data_failed, Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
}
