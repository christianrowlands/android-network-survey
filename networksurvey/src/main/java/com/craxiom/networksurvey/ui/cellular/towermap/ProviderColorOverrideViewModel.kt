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

    private val _uiState = MutableStateFlow(ProviderColorOverrideUiState())
    val uiState: StateFlow<ProviderColorOverrideUiState> = _uiState.asStateFlow()

    init {
        loadOverrides()
    }

    /**
     * Re-reads the overrides from the backing preferences. Called on resume so changes made
     * elsewhere (for example from the tower details color picker) are reflected when returning
     * to this screen via the back stack.
     */
    fun refresh() = loadOverrides()

    /**
     * Creates a fresh manager so every operation reads the latest persisted overrides. The manager
     * caches the preferences in memory at construction time, and other parts of the app (for
     * example the tower details color picker) write through their own instances, so a long-lived
     * instance here would serve stale data and its whole-map persistence could overwrite external
     * changes.
     */
    private fun newOverrideManager() = PlmnColorOverrideManager(getApplication())

    private fun loadOverrides() {
        val overrideManager = newOverrideManager()
        val overrides = overrideManager.getOverrides()
        val entries = overrides.mapNotNull { (key, paletteIndex) ->
            val parts = key.split("-")
            if (parts.size < 2) return@mapNotNull null
            val mccInt = parts[0].toIntOrNull() ?: return@mapNotNull null
            val mncInt = parts[1].toIntOrNull() ?: return@mapNotNull null
            ProviderColorEntry(
                mcc = parts[0],
                mnc = parts[1],
                paletteIndex = paletteIndex,
                defaultPaletteIndex = PlmnColorMapper.getDefaultColorIndex(mccInt, mncInt)
            )
        }.sortedWith(compareBy({ it.mcc.toIntOrNull() ?: 0 }, { it.mnc.toIntOrNull() ?: 0 }))

        _uiState.value = _uiState.value.copy(
            overrides = entries,
            isAtMaxCapacity = overrideManager.isAtMaxCapacity()
        )
    }

    fun setOverride(mcc: String, mnc: String, paletteIndex: Int) {
        newOverrideManager().setOverride(mcc, mnc, paletteIndex)
        loadOverrides()
    }

    fun removeOverride(mcc: String, mnc: String) {
        newOverrideManager().removeOverride(mcc, mnc)
        loadOverrides()
    }

    fun clearAll() {
        newOverrideManager().clearAll()
        loadOverrides()
    }
}

/**
 * A single provider color override entry for display.
 */
data class ProviderColorEntry(
    val mcc: String,
    val mnc: String,
    val paletteIndex: Int,
    val defaultPaletteIndex: Int
)

/**
 * UI state for the provider color override screen.
 */
data class ProviderColorOverrideUiState(
    val overrides: List<ProviderColorEntry> = emptyList(),
    val isAtMaxCapacity: Boolean = false,
    val maxCapacity: Int = PlmnColorOverrideManager.MAX_OVERRIDES
)
