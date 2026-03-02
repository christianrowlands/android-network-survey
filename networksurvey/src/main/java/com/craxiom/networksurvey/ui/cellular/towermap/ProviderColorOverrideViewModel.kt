package com.craxiom.networksurvey.ui.cellular.towermap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.craxiom.networksurvey.data.PlmnColorOverrideManager
import com.craxiom.networksurvey.util.PlmnColorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the provider color override management screen.
 */
class ProviderColorOverrideViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val overrideManager = PlmnColorOverrideManager(application)

    private val _uiState = MutableStateFlow(ProviderColorOverrideUiState())
    val uiState: StateFlow<ProviderColorOverrideUiState> = _uiState.asStateFlow()

    init {
        loadOverrides()
    }

    private fun loadOverrides() {
        val overrides = overrideManager.getOverrides()
        val entries = overrides.mapNotNull { (key, paletteIndex) ->
            val parts = key.split("-")
            if (parts.size < 2) return@mapNotNull null
            val mcc = parts[0].toIntOrNull() ?: return@mapNotNull null
            val mnc = parts[1].toIntOrNull() ?: return@mapNotNull null
            ProviderColorEntry(
                mcc = mcc,
                mnc = mnc,
                paletteIndex = paletteIndex,
                defaultPaletteIndex = PlmnColorMapper.getDefaultColorIndex(mcc, mnc)
            )
        }.sortedWith(compareBy({ it.mcc }, { it.mnc }))

        _uiState.value = _uiState.value.copy(
            overrides = entries,
            isAtMaxCapacity = overrideManager.isAtMaxCapacity()
        )
    }

    fun setOverride(mcc: Int, mnc: Int, paletteIndex: Int) {
        overrideManager.setOverride(mcc, mnc, paletteIndex)
        loadOverrides()
    }

    fun removeOverride(mcc: Int, mnc: Int) {
        overrideManager.removeOverride(mcc, mnc)
        loadOverrides()
    }

    fun clearAll() {
        overrideManager.clearAll()
        loadOverrides()
    }
}

/**
 * A single provider color override entry for display.
 */
data class ProviderColorEntry(
    val mcc: Int,
    val mnc: Int,
    val paletteIndex: Int,
    val defaultPaletteIndex: Int
)

/**
 * UI state for the provider color override screen.
 */
data class ProviderColorOverrideUiState(
    val overrides: List<ProviderColorEntry> = emptyList(),
    val isAtMaxCapacity: Boolean = false
)
