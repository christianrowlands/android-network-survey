package com.craxiom.networksurvey.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

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

public class UploadSettingsFragment extends PreferenceFragmentCompat
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
        setupOcidApiKeyPreference();
    }

    /**
     * Sets up the OCID API key preference to use encrypted storage instead of SharedPreferences.
     * The preference is made non-persistent so setText() never writes to SharedPreferences.
     * A change listener handles user edits by storing directly to encrypted storage.
     */
    private void setupOcidApiKeyPreference()
    {
        Context context = getContext();
        if (context == null) return;

        EditTextPreference apiKeyPreference = findPreference(NetworkSurveyConstants.PROPERTY_OCID_API_KEY);
        if (apiKeyPreference == null) return;

        // Prevent the preference from persisting to SharedPreferences. This ensures
        // setText() only updates the in-memory value for UI display, not plain-text storage.
        apiKeyPreference.setPersistent(false);

        // Populate the preference with the current value from encrypted storage
        String secureKey = CredentialSecureStorage.INSTANCE.getOcidApiKey(context);
        if (secureKey != null && !secureKey.trim().isEmpty())
        {
            apiKeyPreference.setText(secureKey.trim());
        }

        // Clean up any residual plain-text API key from SharedPreferences
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().remove(NetworkSurveyConstants.PROPERTY_OCID_API_KEY).apply();

        // Handle user edits by storing directly to encrypted storage
        apiKeyPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            String value = newValue != null ? ((String) newValue).trim() : "";
            boolean isEmpty = TextUtils.isEmpty(value);
            if (!isEmpty && !PreferenceUtils.isApiKeyValid(value))
            {
                Toast.makeText(getActivity(), "OpenCelliD API Key is invalid", Toast.LENGTH_LONG).show();
            }
            CredentialSecureStorage.INSTANCE.storeOcidApiKey(context, isEmpty ? null : value);
            return true;
        });
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
