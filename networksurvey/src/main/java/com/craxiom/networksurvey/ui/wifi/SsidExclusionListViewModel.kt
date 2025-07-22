package com.craxiom.networksurvey.ui.wifi

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.SsidExclusionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the SSID exclusion list screen.
 */
class SsidExclusionListViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val ssidExclusionManager = SsidExclusionManager(application)

    private val _uiState = MutableStateFlow(SsidExclusionListUiState())
    val uiState: StateFlow<SsidExclusionListUiState> = _uiState.asStateFlow()

    init {
        loadExcludedSsids()
    }

    private fun loadExcludedSsids() {
        viewModelScope.launch {
            val excludedSsids = ssidExclusionManager.getExcludedSsidsList()
            _uiState.value = _uiState.value.copy(
                excludedSsids = excludedSsids,
                isAtMaxCapacity = ssidExclusionManager.isAtMaxCapacity()
            )
        }
    }

    fun addSsid(ssid: String) {
        if (ssid.isBlank()) return

        viewModelScope.launch {
            val success = ssidExclusionManager.addExcludedSsid(ssid.trim())

            if (success) {
                showToast(
                    getApplication<Application>().getString(
                        R.string.ssid_added_to_exclusion,
                        ssid.trim()
                    )
                )
                loadExcludedSsids()
            } else {
                if (ssidExclusionManager.isAtMaxCapacity()) {
                    showToast(getApplication<Application>().getString(R.string.exclusion_list_full))
                } else {
                    showToast(
                        getApplication<Application>().getString(
                            R.string.ssid_already_excluded,
                            ssid.trim()
                        )
                    )
                }
            }
        }
    }

    fun removeSsid(ssid: String) {
        viewModelScope.launch {
            val success = ssidExclusionManager.removeExcludedSsid(ssid)

            if (success) {
                showToast(
                    getApplication<Application>().getString(
                        R.string.ssid_removed_from_exclusion,
                        ssid
                    )
                )
                loadExcludedSsids()
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            ssidExclusionManager.clearExcludedSsids()
            loadExcludedSsids()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * UI state for the SSID exclusion list screen.
 */
data class SsidExclusionListUiState(
    val excludedSsids: List<String> = emptyList(),
    val isAtMaxCapacity: Boolean = false
)