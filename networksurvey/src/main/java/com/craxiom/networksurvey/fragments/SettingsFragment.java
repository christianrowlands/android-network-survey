package com.craxiom.networksurvey.fragments;

import static com.craxiom.networksurvey.constants.CdrPermissions.CDR_OPTIONAL_PERMISSIONS;
import static com.craxiom.networksurvey.constants.CdrPermissions.CDR_REQUIRED_PERMISSIONS;
import static com.craxiom.networksurvey.fragments.DashboardFragment.ACCESS_OPTIONAL_PERMISSION_REQUEST_ID;
import static com.craxiom.networksurvey.fragments.DashboardFragment.ACCESS_REQUIRED_PERMISSION_REQUEST_ID;

import android.content.Context;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.DropDownPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.craxiom.networksurvey.util.MdmUtils;
import com.craxiom.networksurvey.util.SettingsUtils;

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
    public static final String[] MDM_OVERLAP_PROPERTY_KEYS = {NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING,
            NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING,
            NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING,
            NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING,
            NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING,
            NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB,
            NetworkSurveyConstants.PROPERTY_LOG_FILE_TYPE,
            NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS,
            NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
            NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS,
            NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS,
            NetworkSurveyConstants.PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS,
            NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT,
            NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER,
            NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL};

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        // Inflate the preferences XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS));
        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS));
        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS));
        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS));
        setPreferenceAsIntegerOnly(findPreference(NetworkSurveyConstants.PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS));

        setAppVersion();
        setAppInstanceId();

        updateUiForMdmIfNecessary();

        final Preference uploadSettings = findPreference(NetworkSurveyConstants.UPLOAD_PREFERENCES_GROUP);
        if (uploadSettings != null)
        {
            uploadSettings.setOnPreferenceClickListener(preference -> {
                SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
                viewModel.triggerNavigationToUploadSettings();
                return true;
            });
        }

        final Preference towerMapSettings = findPreference(NetworkSurveyConstants.TOWER_MAP_PREFERENCES_GROUP);
        if (towerMapSettings != null)
        {
            towerMapSettings.setOnPreferenceClickListener(preference -> {
                SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
                viewModel.triggerNavigationToTowerMapSettings();
                return true;
            });
        }

        final Preference privacyPolicy = findPreference(NetworkSurveyConstants.PROPERTY_PRIVACY_POLICY);
        if (privacyPolicy != null)
        {
            privacyPolicy.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://networksurvey.app/privacy-policy"));
                Context context = getContext();
                if (context != null) context.startActivity(browserIntent);
                return true;
            });
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        int defaultValue = -1;
        if (key == null) return;

        switch (key)
        {
            case NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY:
                final boolean mdmOverride = sharedPreferences.getBoolean(key, false);

                Timber.d("mdmOverride Preference Changed to %s", mdmOverride);

                if (mdmOverride)
                {
                    final PreferenceScreen preferenceScreen = getPreferenceScreen();
                    for (String preferenceKey : MDM_OVERLAP_PROPERTY_KEYS)
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

            case NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS:
                defaultValue = NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS;
                break;

            case NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS:
                defaultValue = NetworkSurveyConstants.DEFAULT_GNSS_SCAN_INTERVAL_SECONDS;
                break;

            case NetworkSurveyConstants.PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS:
                defaultValue = NetworkSurveyConstants.DEFAULT_DEVICE_STATUS_SCAN_INTERVAL_SECONDS;
                break;

            case NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING:
                final boolean autostartCdr = sharedPreferences.getBoolean(key, false);
                if (autostartCdr)
                {
                    // Verify the app has the necessary permissions to start CDR logging
                    showCdrPermissionRationaleAndRequestPermissions();
                }
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
                Timber.e(e, "The new value for %s is not a valid integer. Reverting to the default value of %d", key, defaultValue);
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
        Context context = requireContext();
        if (!MdmUtils.isUnderMdmControl(context, MDM_OVERLAP_PROPERTY_KEYS)) return;

        final SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();

        // Update the UI so that the MDM override is visible, and that some of the settings can't be changed
        final Preference overridePreference = getPreferenceScreen().findPreference(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY);
        if (overridePreference != null) overridePreference.setVisible(true);

        // Regardless of MDM override, upload to 3rd party DBs is disabled if MDM disables it
        if (!MdmUtils.isExternalDataUploadAllowed(context))
        {
            Preference preference = findPreference(NetworkSurveyConstants.UPLOAD_PREFERENCES_GROUP);
            if (preference != null)
            {
                preference.setEnabled(false);
                preference.setSummary(R.string.upload_disabled_via_mdm);
            }
        }

        final boolean mdmOverride = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);

        if (mdmOverride)
        {
            return; // Nothing to do because all the preferences are enabled by default.
        }

        final PreferenceScreen preferenceScreen = getPreferenceScreen();

        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager == null) return;

        final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
        if (mdmProperties == null) return;

        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING);
        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING);
        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING);
        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING);
        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING);
        updateLogRolloverSizeForMdm(preferenceScreen, mdmProperties);
        updateListProviderPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_LOG_FILE_TYPE);
        updateIntPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS);
        updateIntPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS);
        updateIntPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS);
        updateIntPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS);
        updateIntPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS);
        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT);
        updateListProviderPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER);
        updateBooleanPreferenceForMdm(preferenceScreen, mdmProperties, NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL);
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
     * Updates a list preference with an MDM value, if it exists. The shared preferences are
     * also updated, so that values are retained when MDM control is off.
     *
     * @param preferenceScreen The preference screen
     * @param mdmProperties    The map of mdm provided properties.
     * @param preferenceKey    The preference key
     * @since 0.4.0
     */
    private void updateListProviderPreferenceForMdm(PreferenceScreen preferenceScreen, Bundle mdmProperties, String preferenceKey)
    {
        try
        {
            final ListPreference preference = preferenceScreen.findPreference(preferenceKey);

            if (preference != null && mdmProperties.containsKey(preferenceKey))
            {
                final int mdmIntProperty = mdmProperties.getInt(preferenceKey, -1);

                if (mdmIntProperty != -1)
                {
                    preference.setEnabled(false);
                    preference.setValueIndex(mdmIntProperty);
                    Timber.i("Setting the location provider to %d", mdmIntProperty);

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

    /**
     * Sets the App Version in the preferences UI if it is available.
     */
    private void setAppVersion()
    {
        try
        {
            Context context = getContext();
            if (context == null) return;

            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            final Preference appVersionPreference = findPreference(NetworkSurveyConstants.PROPERTY_APP_VERSION);
            if (appVersionPreference != null)
            {
                appVersionPreference.setSummary(info.versionName);
            }
        } catch (Exception e)
        {
            Timber.wtf(e, "Could not set the app version number");
        }
    }

    /**
     * Sets the App Instance ID in the preferences UI if it is available.
     */
    private void setAppInstanceId()
    {
        Context context = getContext();
        if (context == null) return;

        final Preference appInstanceIdPreference = findPreference(NetworkSurveyConstants.PROPERTY_APP_INSTANCE_ID);

        SettingsUtils.setAppInstanceId(context, appInstanceIdPreference);
    }

    /**
     * Check to see if we should show the rationale for any of the CDR permissions. If so, then display a dialog that
     * explains what permissions we need for this app to work properly.
     * <p>
     * If we should not show the rationale, then just request the permissions.
     */
    private void showCdrPermissionRationaleAndRequestPermissions()
    {
        final FragmentActivity activity = getActivity();
        if (activity == null) return;

        final Context context = getContext();
        if (context == null) return;

        if (missingAnyPermissions(CDR_REQUIRED_PERMISSIONS))
        {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle(getString(R.string.cdr_required_permissions_rationale_title));
            alertBuilder.setMessage(getText(R.string.cdr_required_permissions_rationale));
            alertBuilder.setPositiveButton(R.string.request, (dialog, which) -> requestRequiredCdrPermissions());

            AlertDialog permissionsExplanationDialog = alertBuilder.create();
            permissionsExplanationDialog.show();

            // Revert the cdr autostart preference if the permissions have not been granted
            final SwitchPreferenceCompat preference = getPreferenceScreen().findPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING);
            if (preference != null) preference.setChecked(false);
            getPreferenceManager().getSharedPreferences()
                    .edit()
                    .putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING, false)
                    .apply();

            return;
        }

        if (missingAnyPermissions(CDR_OPTIONAL_PERMISSIONS))
        {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle(getString(R.string.cdr_optional_permissions_rationale_title));
            alertBuilder.setMessage(getText(R.string.cdr_optional_permissions_rationale));
            alertBuilder.setPositiveButton(R.string.request, (dialog, which) -> requestOptionalCdrPermissions());
            alertBuilder.setNegativeButton(R.string.ignore, (dialog, which) -> {

            });

            AlertDialog permissionsExplanationDialog = alertBuilder.create();
            permissionsExplanationDialog.show();
        }
    }

    /**
     * @return True if any of the permissions have been denied. False if all the permissions
     * have been granted.
     */
    private boolean missingAnyPermissions(String[] permissions)
    {
        final Context context = getContext();
        if (context == null) return true;
        for (String permission : permissions)
        {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
            {
                Timber.i("Missing the permission: %s", permission);
                return true;
            }
        }

        return false;
    }

    /**
     * Request the permissions needed for this app if any of them have not yet been granted.  If all of the permissions
     * are already granted then don't request anything.
     */
    private void requestRequiredCdrPermissions()
    {
        if (missingAnyPermissions(CDR_REQUIRED_PERMISSIONS))
        {
            FragmentActivity activity = getActivity();
            if (activity != null)
            {
                ActivityCompat.requestPermissions(activity, CDR_REQUIRED_PERMISSIONS, ACCESS_REQUIRED_PERMISSION_REQUEST_ID);
            }
        }
    }

    /**
     * Request the optional permissions for this app if any of them have not yet been granted. If all of the permissions
     * are already granted then don't request anything.
     */
    private void requestOptionalCdrPermissions()
    {
        if (missingAnyPermissions(CDR_OPTIONAL_PERMISSIONS))
        {
            FragmentActivity activity = getActivity();
            if (activity != null)
            {
                ActivityCompat.requestPermissions(activity, CDR_OPTIONAL_PERMISSIONS, ACCESS_OPTIONAL_PERMISSION_REQUEST_ID);
            }
        }
    }
}
