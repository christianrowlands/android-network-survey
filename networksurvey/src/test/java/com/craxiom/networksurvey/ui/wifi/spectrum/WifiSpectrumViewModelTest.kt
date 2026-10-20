package com.craxiom.networksurvey.ui.wifi.spectrum

import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.wifi.model.WifiRecords.wifi
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The spectrum page's view model: the seed from the Wi-Fi list, scans and registration. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WifiSpectrumViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val service = mock<NetworkSurveyService>()
    private var scanState = WifiScanState.READY

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theListsLastScan_isDrawnUntilAScanArrives() {
        val viewModel = newViewModel()
        assertNull(viewModel.networks.value)

        viewModel.seed(listOf(wifi("02:00:A4:11:F0:21", ssid = "Seeded")))
        assertEquals(listOf("Seeded"), viewModel.networks.value?.map { it.ssid })

        viewModel.onServiceChanged(service)
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:D8:4E:9A:0C", ssid = "Live")))
        viewModel.seed(listOf(wifi("02:00:A4:11:F0:21", ssid = "Seeded again")))

        assertEquals(listOf("Live"), viewModel.networks.value?.map { it.ssid })
    }

    @Test
    fun registersOnlyOnceTheScanCanRun() {
        scanState = WifiScanState.PERMISSION_NEEDED
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)
        verify(service, never()).registerWifiSurveyRecordListener(viewModel)

        scanState = WifiScanState.READY
        viewModel.refresh()

        verify(service).registerWifiSurveyRecordListener(viewModel)
    }

    @Test
    fun aScanAfterTheBindEnds_isIgnored() {
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)
        viewModel.onServiceChanged(null)

        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:D8:4E:9A:0C")))

        assertNull(viewModel.networks.value)
    }

    private fun newViewModel() = WifiSpectrumViewModel(RuntimeEnvironment.getApplication(), { scanState }, dispatcher)
}
