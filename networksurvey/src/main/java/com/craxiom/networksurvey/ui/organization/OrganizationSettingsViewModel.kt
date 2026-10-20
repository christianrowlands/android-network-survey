package com.craxiom.networksurvey.ui.organization

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.mdm.AppRestrictionsDefinitions
import com.craxiom.networksurvey.survey.mdm.OrganizationOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Organization settings page: reads the managed configuration and the schema off the main
 * thread, and turns "Use my own settings" on or off. Refreshes when the override changes.
 */
class OrganizationSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val restrictions = AndroidRestrictionsSource(application)

    private val _uiState = MutableStateFlow<OrganizationSettingsUi?>(null)

    /** Null until the first read finishes. */
    val uiState: StateFlow<OrganizationSettingsUi?> = _uiState.asStateFlow()

    // Held as a field: SharedPreferences keeps its listeners weakly.
    private val overrideListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY) refresh()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(overrideListener)
        refresh()
    }

    /** Re-reads the managed configuration, for example after the organization pushed a change. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = OrganizationSettingsReader.read(
                restrictions.snapshot(),
                AppRestrictionsDefinitions.read(getApplication<Application>().resources),
                preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false),
            )
        }
    }

    /** Turns "Override organization settings" on (true) or restores the organization's (false). */
    fun setOverride(on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            OrganizationOverride.set(preferences, on)
        }
    }

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(overrideListener)
    }
}
