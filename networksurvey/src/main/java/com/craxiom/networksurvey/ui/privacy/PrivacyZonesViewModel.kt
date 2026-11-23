package com.craxiom.networksurvey.ui.privacy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZoneGate
import com.craxiom.networksurvey.privacy.PrivacyZonePolicy
import com.craxiom.networksurvey.privacy.PrivacyZoneState
import com.craxiom.networksurvey.privacy.PrivacyZoneStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * What the Privacy zones page shows.
 *
 * @property zones        Every saved zone, in the order it was added.
 * @property allowed      False when the organization turned privacy zones off (`allow_privacy_zones`).
 * @property insideZoneId The zone the survey is paused in right now, or null.
 */
data class PrivacyZonesUiState(
    val zones: List<PrivacyZone> = emptyList(),
    val allowed: Boolean = true,
    val insideZoneId: String? = null,
) {
    /** True while another zone can be added. */
    val canAdd: Boolean get() = allowed && zones.size < PrivacyZone.MAX_ZONES

    /** True when the list holds as many zones as it can. */
    val isFull: Boolean get() = zones.size >= PrivacyZone.MAX_ZONES

    /** The zone the survey is paused in right now, or null. */
    val insideZone: PrivacyZone? get() = insideZoneId?.let { id -> zones.firstOrNull { it.id == id } }
}

/**
 * The Privacy zones page: the saved zones ([PrivacyZoneStore]), the zone the survey is paused in
 * ([PrivacyZoneGate]), and whether the organization allows zones ([PrivacyZonePolicy], read again on
 * every return to the page because the managed configuration can change while it is away).
 */
class PrivacyZonesViewModel internal constructor(
    application: Application,
    private val store: PrivacyZoneStore,
    gateState: StateFlow<PrivacyZoneState>,
    private val isAllowed: () -> Boolean,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        PrivacyZoneStore.getInstance(application),
        PrivacyZoneGate.getInstance(application).state,
        { PrivacyZonePolicy.isAllowed(application) },
    )

    private val allowed = MutableStateFlow(isAllowed())

    /** The page's state. */
    val state: StateFlow<PrivacyZonesUiState> = combine(store.zones, gateState, allowed) { zones, gate, allowedNow ->
        PrivacyZonesUiState(
            zones = zones,
            allowed = allowedNow,
            insideZoneId = gate.zone?.id?.takeIf { allowedNow },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PrivacyZonesUiState(store.zones.value, allowed.value))

    /** Reads the organization's policy again (on every return to the page). */
    fun refresh() {
        allowed.value = isAllowed()
    }

    /** Deletes the zone with [id]; records made there are saved and sent again from now on. */
    fun remove(id: String) {
        store.remove(id)
    }
}
