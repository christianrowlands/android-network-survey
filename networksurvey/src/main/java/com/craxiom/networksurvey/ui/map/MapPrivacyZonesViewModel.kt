package com.craxiom.networksurvey.ui.map

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZonePolicy
import com.craxiom.networksurvey.privacy.PrivacyZoneStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The Map tab's privacy zones layer: the zones to draw (none while the organization turns zones
 * off, since they do nothing then), the Layers sheet's switch, which defaults on and is saved, and
 * the zone whose sheet a tap opened. The switch lets the user hide the circles before sharing a
 * screenshot, since a circle drawn around a home is itself a leak.
 */
class MapPrivacyZonesViewModel internal constructor(
    application: Application,
    store: PrivacyZoneStore,
    private val preferences: SharedPreferences,
    private val isAllowed: () -> Boolean,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        PrivacyZoneStore.getInstance(application),
        PreferenceManager.getDefaultSharedPreferences(application),
        { PrivacyZonePolicy.isAllowed(application) },
    )

    private val allowed = MutableStateFlow(isAllowed())

    /** The zones in force: every saved zone, or none while the organization turns zones off. */
    val zones: StateFlow<List<PrivacyZone>> = combine(store.zones, allowed) { zones, allowedNow ->
        if (allowedNow) zones else emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, if (allowed.value) store.zones.value else emptyList())

    private val _show = MutableStateFlow(
        preferences.getBoolean(NetworkSurveyConstants.PROPERTY_SHOW_PRIVACY_ZONES, NetworkSurveyConstants.DEFAULT_SHOW_PRIVACY_ZONES)
    )

    /** True while the layer is switched on in the Layers sheet. */
    val show: StateFlow<Boolean> = _show.asStateFlow()

    private val selectedId = MutableStateFlow<String?>(null)

    /**
     * The zone a tap on the map opened a sheet for, or null. It closes on its own when that zone is
     * deleted or the organization turns zones off.
     */
    val selected: StateFlow<PrivacyZone?> = combine(zones, selectedId) { zonesNow, id ->
        id?.let { zonesNow.firstOrNull { zone -> zone.id == it } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Opens the sheet for the zone with [zoneId] (a tap on its circle). */
    fun select(zoneId: String) {
        selectedId.value = zoneId
    }

    /** Closes the zone sheet. */
    fun dismiss() {
        selectedId.value = null
    }

    /** Switches the layer on or off and saves the choice. */
    fun setShow(show: Boolean) {
        _show.value = show
        preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_SHOW_PRIVACY_ZONES, show) }
    }

    /** Reads the organization's policy again (on every return to the Map tab). */
    fun refresh() {
        allowed.value = isAllowed()
    }
}
