package com.craxiom.networksurvey.ui.wifi.clients

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.clients.ScoutClient
import com.craxiom.networksurvey.scout.clients.ScoutClientSnapshot
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.wifi.model.WifiScoutSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Clients mode of Signals > Wi-Fi. Activity scoped like the Wi-Fi list, so the choice of mode
 * and view survives sub-tab switches (not app restarts: the saved mode stays an access point mode).
 *
 * Clients are offered only while an NS Scout is paired and is the Wi-Fi source ([available]); the
 * live listener is registered only while the Clients list shows, since registering makes the Scout
 * capture clients.
 */
class WifiClientsViewModel internal constructor(
    application: Application,
    scoutStates: Flow<Pair<ScoutState, WifiSource>>,
    private val mainDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        scoutStates = ScoutManager.getInstance(application).let { combine(it.state, it.wifiSource, ::Pair) },
        mainDispatcher = Dispatchers.Main,
    )

    private var opened: ScoutClient? = null
    private var paused = false
    private val feed = WifiClientFeed(viewModelScope, mainDispatcher, ::onSnapshot)

    private val _uiState = MutableStateFlow(WifiClientsUiState())
    val uiState: StateFlow<WifiClientsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(mainDispatcher) {
            scoutStates.collect { (state, source) ->
                val scout = WifiScoutSource.of(state, source)
                _uiState.update { it.copy(available = scout != null, scoutName = scout?.name.orEmpty()) }
                syncRegistration()
            }
        }
    }

    /** Called with the bound service, and with null before the Wi-Fi list unbinds. */
    fun onServiceChanged(service: NetworkSurveyService?) = feed.onServiceChanged(service, _uiState.value.shown)

    /** Shows the Clients list (true) or the access points again (false). */
    fun setSelected(selected: Boolean) {
        if (_uiState.value.selected == selected) return
        _uiState.update { it.copy(selected = selected) }
        syncRegistration()
    }

    fun setView(view: WifiClientsView) = _uiState.update { it.copy(view = view) }

    /**
     * Freezes the rows on screen, or shows the latest again, as Pause does for the access points.
     * Only the rows stop: the Scout keeps capturing clients while the list is open.
     */
    fun setPaused(paused: Boolean) {
        if (this.paused == paused) return
        this.paused = paused
        if (!paused) applySnapshot(if (_uiState.value.shown) feed.snapshot.value else ScoutClientSnapshot.EMPTY)
    }

    /** Opens or closes a network row in the "By network looked for" view. */
    fun toggleNetwork(ssid: String) = _uiState.update {
        it.copy(expandedNetworks = if (ssid in it.expandedNetworks) it.expandedNetworks - ssid else it.expandedNetworks + ssid)
    }

    /** Remembers [client] as the one being opened, so Client details starts with what was on screen. */
    fun noteOpened(client: ScoutClient) {
        opened = client
    }

    /** @return The client last opened with [mac], or the one in the latest snapshot, or null. */
    fun lastOpened(mac: String): ScoutClient? =
        opened?.takeIf { it.mac == mac } ?: _uiState.value.clients.firstOrNull { it.mac == mac }

    override fun onCleared() = feed.release()

    private fun syncRegistration() {
        val shown = _uiState.value.shown
        feed.update(shown)
        // A stale list never shows when the Clients mode comes back; paused rows stay frozen
        if (!shown && !paused) applySnapshot(ScoutClientSnapshot.EMPTY)
    }

    private fun onSnapshot(snapshot: ScoutClientSnapshot) {
        if (!paused) applySnapshot(snapshot)
    }

    private fun applySnapshot(snapshot: ScoutClientSnapshot) = _uiState.update {
        it.copy(clients = snapshot.clients, networks = WifiClientsMapper.networks(snapshot.clients), nowMs = snapshot.atMs)
    }
}
