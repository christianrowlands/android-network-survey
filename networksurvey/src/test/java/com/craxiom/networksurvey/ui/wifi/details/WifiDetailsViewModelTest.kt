package com.craxiom.networksurvey.ui.wifi.details

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.craxiom.networksurvey.data.SsidExclusionManager
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.FakeRssiChart
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.wifi.model.WifiRecords.wifi
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The Wi-Fi details page's view model: registration, plotting this network's signal, and the exclusion toggle. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WifiDetailsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val service = mock<NetworkSurveyService>()
    private val chart = FakeRssiChart()
    private val exclusions = SsidExclusionManager(RuntimeEnvironment.getApplication())
    private var scanState = WifiScanState.READY
    private val network = WifiNetwork("02:00:A4:11:F0:21", -52f, "Acme-Corp", 5180, 36, null, "WPA2", null, "", null)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RuntimeEnvironment.getApplication().getSharedPreferences("network_survey_ssid_exclusion", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showing_seedsTheChartWithTheTappedReading_once() {
        val viewModel = newViewModel()

        viewModel.show(network)
        viewModel.show(network.copy(bssid = "02:00:D8:4E:9A:0C"))

        assertEquals(-52f, chart.started)
        assertEquals("02:00:A4:11:F0:21", viewModel.network.value?.bssid)
    }

    @Test
    fun registersOnlyOnceTheScanCanRun_andAgainWhenItCan() {
        scanState = WifiScanState.LOCATION_OFF
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)
        verify(service, never()).registerWifiSurveyRecordListener(viewModel)

        scanState = WifiScanState.READY
        RuntimeEnvironment.getApplication().sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
        shadowOf(Looper.getMainLooper()).idle()

        verify(service, times(1)).registerWifiSurveyRecordListener(viewModel)
        assertEquals(WifiScanState.READY, viewModel.scanState.value)
    }

    @Test
    fun eachScan_plotsThisNetwork_orNotHeard() {
        val viewModel = registered()

        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:D8:4E:9A:0C"), wifi("02:00:A4:11:F0:21", rssi = -61f)))
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:D8:4E:9A:0C")))
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21", rssi = null)))

        assertEquals(listOf(-61f, UNKNOWN_RSSI, UNKNOWN_RSSI), chart.plotted)
    }

    @Test
    fun whileThePhoneCannotScan_theChartStops_andTheHeaderHasNoReading() {
        val viewModel = registered()
        viewModel.setChartPaused(false)
        assertFalse(chart.chartPaused)

        scanState = WifiScanState.LOCATION_OFF
        viewModel.refresh()

        assertTrue(chart.chartPaused)
        assertEquals(UNKNOWN_RSSI, viewModel.rssi.value)
        scanState = WifiScanState.READY
        viewModel.refresh()
        assertFalse(chart.chartPaused)
    }

    @Test
    fun theChartClockWaitsForThePage() {
        val viewModel = registered()
        assertTrue(chart.chartPaused)

        viewModel.setChartPaused(false)
        assertFalse(chart.chartPaused)
        viewModel.setChartPaused(true)
        assertTrue(chart.chartPaused)
    }

    @Test
    fun aScanAfterTheBindEnds_isNotPlotted() {
        val viewModel = registered()

        viewModel.onServiceChanged(null)
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21")))

        verify(service).unregisterWifiSurveyRecordListener(viewModel)
        assertEquals(emptyList<Float>(), chart.plotted)
    }

    @Test
    fun theExclusionToggle_addsAndRemovesTheSsid() {
        val viewModel = newViewModel()
        viewModel.show(network)
        assertFalse(viewModel.excluded.value)

        assertEquals(ExclusionChange.ADDED, viewModel.toggleExcluded())
        assertTrue(viewModel.excluded.value)
        assertTrue(exclusions.isExcluded("Acme-Corp"))

        assertEquals(ExclusionChange.REMOVED, viewModel.toggleExcluded())
        assertFalse(viewModel.excluded.value)
    }

    @Test
    fun aFullExclusionList_saysSo() {
        repeat(SsidExclusionManager.MAX_EXCLUSION_LIST_SIZE) { exclusions.addExcludedSsid("ssid-$it") }
        val viewModel = newViewModel()
        viewModel.show(network)

        assertEquals(ExclusionChange.LIST_FULL, viewModel.toggleExcluded())
        assertFalse(viewModel.excluded.value)
    }

    @Test
    fun aNetworkAlreadyExcluded_showsAsExcluded() {
        exclusions.addExcludedSsid("Acme-Corp")
        val viewModel = newViewModel()

        viewModel.show(network)

        assertTrue(viewModel.excluded.value)
    }

    @Test
    fun clearing_releasesTheChartAndTheListener() {
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, viewModelFactory { initializer { newViewModel() } })[WifiDetailsViewModel::class.java]
        viewModel.show(network)
        viewModel.onServiceChanged(service)

        store.clear()

        verify(service).unregisterWifiSurveyRecordListener(viewModel)
        assertTrue(chart.cleared)
    }

    private fun registered(): WifiDetailsViewModel {
        val viewModel = newViewModel()
        viewModel.show(network)
        viewModel.onServiceChanged(service)
        return viewModel
    }

    private fun newViewModel() = WifiDetailsViewModel(
        application = RuntimeEnvironment.getApplication(),
        environment = { scanState },
        exclusions = exclusions,
        watchlistMatches = { flowOf(false) },
        chartSink = chart,
        mainDispatcher = dispatcher,
    )
}
