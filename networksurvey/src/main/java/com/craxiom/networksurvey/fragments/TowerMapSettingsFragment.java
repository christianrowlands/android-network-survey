package com.craxiom.networksurvey.fragments;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.logging.db.SurveyDatabase;
import com.craxiom.networksurvey.logging.db.SurveyedPointStore;
import com.craxiom.networksurvey.ui.main.SharedViewModel;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Settings UI Fragment for the Tower Map specific settings.
 */
@AndroidEntryPoint
public class TowerMapSettingsFragment extends PreferenceFragmentCompat
{
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.tower_map_preferences, rootKey);

        setupPreferenceDependencies();
        setupProviderColorOverrides();
        setupClearSurveyPoints();
    }

    private void setupPreferenceDependencies()
    {
        // Get the display coverage preference
        SwitchPreferenceCompat displayCoveragePreference = findPreference(NetworkSurveyConstants.PROPERTY_MAP_DISPLAY_SERVING_CELL_COVERAGE);

        // Get the color and opacity preferences
        Preference colorPreference = findPreference(NetworkSurveyConstants.PROPERTY_MAP_COVERAGE_CIRCLE_COLOR);
        Preference opacityPreference = findPreference(NetworkSurveyConstants.PROPERTY_MAP_COVERAGE_CIRCLE_OPACITY);

        if (displayCoveragePreference != null && colorPreference != null && opacityPreference != null)
        {
            // Set initial state
            boolean isEnabled = displayCoveragePreference.isChecked();
            colorPreference.setEnabled(isEnabled);
            opacityPreference.setEnabled(isEnabled);

            // Listen for changes
            displayCoveragePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (boolean) newValue;
                colorPreference.setEnabled(enabled);
                opacityPreference.setEnabled(enabled);
                return true;
            });
        }
    }

    /**
     * Wires the "Clear survey points" action to a confirmation dialog and the database clear.
     */
    private void setupClearSurveyPoints()
    {
        Preference clearPreference = findPreference(NetworkSurveyConstants.PROPERTY_CLEAR_SURVEY_POINTS);
        if (clearPreference == null) return;

        clearPreference.setOnPreferenceClickListener(preference -> {
            Context context = getContext();
            if (context == null) return true;
            FragmentDialogs.showClearSurveyPointsConfirmation(getParentFragmentManager(),
                    () -> clearSurveyPoints(context));
            return true;
        });
    }

    private void clearSurveyPoints(Context context)
    {
        new Thread(() -> {
            int toastText;
            try
            {
                SurveyedPointStore.clearAll(SurveyDatabase.getInstance(context).surveyedPointDao());
                toastText = R.string.clear_survey_points_success;
            } catch (Exception e)
            {
                toastText = R.string.clear_survey_points_failed;
            }
            final int message = toastText;
            if (isAdded())
            {
                requireActivity().runOnUiThread(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * Sets up the click listener for navigating to the Provider Color Overrides screen.
     */
    private void setupProviderColorOverrides()
    {
        Preference overridesPreference = findPreference(NetworkSurveyConstants.PROPERTY_MANAGE_PROVIDER_COLOR_OVERRIDES);
        if (overridesPreference != null)
        {
            overridesPreference.setOnPreferenceClickListener(preference -> {
                SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
                viewModel.triggerNavigationToProviderColorOverrides();
                return true;
            });
        }
    }
}
