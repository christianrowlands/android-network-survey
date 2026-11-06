package com.craxiom.networksurvey.ui.wifi.clients

import com.craxiom.networksurvey.listeners.IWifiClientLiveListener
import com.craxiom.networksurvey.scout.clients.ScoutClient
import com.craxiom.networksurvey.scout.clients.ScoutClientSnapshot
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.services.NetworkSurveyService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * When the Clients mode is offered: only while a paired NS Scout is the Wi-Fi source. A Clients
 * choice falls back to the access points otherwise, and comes back with the Scout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WifiClientsViewModelTest {
    private val scout = PairedScout("NS-02000000A1B2", "02:00:00:00:A1:B2", "A1B2", 0, "", "0.1.0", 1, emptyList(), 0L)
    private val connected = ScoutState.Connected(scout, batteryPercent = null, channel = 36, framesPerSecond = 40, capturing = true, dataMissed = false)
    private val states = MutableStateFlow<Pair<ScoutState, WifiSource>>(ScoutState.NotPaired to WifiSource.PHONE)
    private val viewModel = WifiClientsViewModel(RuntimeEnvironment.getApplication(), states, UnconfinedTestDispatcher())

    @Test
    fun neverPaired_offersNoClients() {
        assertFalse(viewModel.uiState.value.available)
        viewModel.setSelected(true)
        assertFalse(viewModel.uiState.value.shown)
    }

    @Test
    fun theScoutAsTheSource_offersClients_namedAfterIt() {
        states.value = connected to WifiSource.SCOUT

        assertTrue(viewModel.uiState.value.available)
        assertEquals(scout.name, viewModel.uiState.value.scoutName)
        viewModel.setSelected(true)
        assertTrue(viewModel.uiState.value.shown)
    }

    @Test
    fun thePhoneAsTheSource_fallsBack_andTheScoutBringsClientsBack() {
        states.value = connected to WifiSource.SCOUT
        viewModel.setSelected(true)

        states.value = ScoutState.NotInUse(scout) to WifiSource.PHONE
        assertFalse(viewModel.uiState.value.available)
        assertFalse(viewModel.uiState.value.shown)

        states.value = connected to WifiSource.SCOUT
        assertTrue(viewModel.uiState.value.shown)
    }

    @Test
    fun pause_freezesTheRows_andResumeShowsTheLatest() {
        val listener = clientsShown()
        listener.onWifiClientSnapshot(snapshot(FIRST))
        assertEquals(listOf(FIRST), macs())

        viewModel.setPaused(true)
        listener.onWifiClientSnapshot(snapshot(SECOND))
        assertEquals(listOf(FIRST), macs())

        viewModel.setPaused(false)
        assertEquals(listOf(SECOND), macs())
        listener.onWifiClientSnapshot(snapshot(FIRST))
        assertEquals(listOf(FIRST), macs())
    }

    @Test
    fun pausedRows_surviveLeavingTheClientsList_andClearWhenResumedElsewhere() {
        clientsShown().onWifiClientSnapshot(snapshot(FIRST))

        viewModel.setPaused(true)
        viewModel.setSelected(false)
        assertEquals(listOf(FIRST), macs())

        viewModel.setPaused(false)
        assertEquals(emptyList<String>(), macs())
    }

    @Test
    fun toggleNetwork_opensAndClosesTheRow() {
        viewModel.toggleNetwork("eduroam")
        assertEquals(setOf("eduroam"), viewModel.uiState.value.expandedNetworks)
        viewModel.toggleNetwork("eduroam")
        assertEquals(emptySet<String>(), viewModel.uiState.value.expandedNetworks)
    }

    /** Binds a service with the Clients list showing. @return The listener the view model registered. */
    private fun clientsShown(): IWifiClientLiveListener {
        states.value = connected to WifiSource.SCOUT
        viewModel.setSelected(true)
        val service = mock<NetworkSurveyService>()
        viewModel.onServiceChanged(service)
        val listener = argumentCaptor<IWifiClientLiveListener>()
        verify(service).registerWifiClientLiveListener(listener.capture())
        return listener.firstValue
    }

    private fun snapshot(mac: String) = ScoutClientSnapshot(
        scout.name,
        listOf(ScoutClient(mac, locallyAdministered = false, rssi = -50, lastHeardMs = 0L, channel = null, bssid = null, frameTypes = emptyList(), probedSsids = emptyList())),
        atMs = 0L,
    )

    private fun macs() = viewModel.uiState.value.clients.map { it.mac }

    private companion object {
        const val FIRST = "aa:00:00:00:00:01"
        const val SECOND = "aa:00:00:00:00:02"
    }
}
