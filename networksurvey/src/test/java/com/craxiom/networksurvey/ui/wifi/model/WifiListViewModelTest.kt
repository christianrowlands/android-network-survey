package com.craxiom.networksurvey.ui.wifi.model

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Looper
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.ui.wifi.model.WifiRecords.wifi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The Wi-Fi list's view model: registration, the scan state, scans from the survey thread, pause,
 * sort, throttling, and what a block or a survey pause does to the rows and the scan gap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WifiListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val service = mock<NetworkSurveyService>()
    private var scanState = WifiScanState.READY
    private var throttle = WifiThrottleInputs(throttleEnabled = false, developerOptionsOn = true, ignored = false)
    private var now = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun registersOnlyOnceTheScanCanRun() {
        scanState = WifiScanState.LOCATION_OFF
        val viewModel = newViewModel()

        viewModel.onServiceChanged(service)
        verify(service, never()).registerWifiSurveyRecordListener(viewModel)

        scanState = WifiScanState.READY
        viewModel.refresh()
        viewModel.refresh()
        verify(service, times(1)).registerWifiSurveyRecordListener(viewModel)
    }

    @Test
    fun wifiAndScanningTurnedOff_unregisters_andShowsWhy() {
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)

        scanState = WifiScanState.WIFI_OFF
        RuntimeEnvironment.getApplication().sendBroadcast(Intent(WifiManager.WIFI_STATE_CHANGED_ACTION))
        shadowOf(Looper.getMainLooper()).idle()

        verify(service).unregisterWifiSurveyRecordListener(viewModel)
        assertEquals(WifiScanState.WIFI_OFF, viewModel.uiState.value.scanState)
    }

    @Test
    fun aNullServiceUnregisters() {
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)

        viewModel.onServiceChanged(null)

        verify(service).unregisterWifiSurveyRecordListener(viewModel)
    }

    @Test
    fun scansFromTheSurveyThread_areAppliedOnTheMainDispatcher() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)

        val thread = Thread { viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21"), wifi("02:00:A4:11:F0:22"))) }
        thread.start()
        thread.join()
        // Nothing changed on the survey thread; the scan waits for the main dispatcher.
        assertEquals(0, viewModel.uiState.value.scanNumber)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.scanNumber)
        assertEquals(2, state.apCount)
    }

    @Test
    fun aScanInFlightWhenTheScreenUnbinds_isDropped() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)

        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21")))
        viewModel.onServiceChanged(null)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.scanNumber)
    }

    @Test
    fun pause_dropsScans_andKeepsTheSeedFromBeforeIt() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21")))
        advanceUntilIdle()

        viewModel.togglePaused()
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:D8:4E:9A:0C")))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.scanNumber)
        assertEquals(listOf("02:00:A4:11:F0:21"), viewModel.seedRecords().map { it.wifiBeaconRecord.data.bssid })
    }

    @Test
    fun theSortIsKept() {
        newViewModel().setSortIndex(3)

        val stored = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
            .getInt(NetworkSurveyConstants.PROPERTY_WIFI_NETWORKS_SORT_ORDER, 0)
        assertEquals(3, stored)
        assertEquals(3, newViewModel().uiState.value.sortIndex)
    }

    @Test
    fun android11AndLater_readTheThrottleSettingOnResumeOnly() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)
        throttle = throttle.copy(throttleEnabled = true)

        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21")))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.throttleWarning)

        viewModel.refresh()
        assertEquals(ThrottleWarning(android9 = false, developerOptionsOn = true), viewModel.uiState.value.throttleWarning)
    }

    @Test
    fun throttling_isShownOnRefresh_andStaysDismissed() {
        throttle = throttle.copy(throttleEnabled = true)
        val viewModel = newViewModel()

        viewModel.refresh()
        assertEquals(ThrottleWarning(android9 = false, developerOptionsOn = true), viewModel.uiState.value.throttleWarning)

        viewModel.dismissThrottleWarning()
        viewModel.refresh()
        assertNull(viewModel.uiState.value.throttleWarning)
    }

    @Test
    fun throttlingTurnedOff_clearsTheWarningOnTheNextRefresh() {
        throttle = throttle.copy(throttleEnabled = true)
        val viewModel = newViewModel()
        viewModel.refresh()

        throttle = throttle.copy(throttleEnabled = false)
        viewModel.refresh()

        assertNull(viewModel.uiState.value.throttleWarning)
    }

    @Test
    fun android10_warnsWhenScansArriveMuchSlowerThanTheInterval() = runTest(dispatcher) {
        val viewModel = newViewModel(sdk = Build.VERSION_CODES.Q)
        viewModel.onServiceChanged(service)

        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21")))
        advanceUntilIdle()
        now += RATE_MS * WifiThrottling.GAP_FACTOR + 1
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21")))
        advanceUntilIdle()

        assertEquals(ThrottleWarning(android9 = false, developerOptionsOn = true), viewModel.uiState.value.throttleWarning)
    }

    @Test
    fun android10_aBatteryPauseOfTheSurvey_isNotMistakenForThrottling() = runTest(dispatcher) {
        assertNoWarningAcrossASurveyPause { it.copy(pausedForBattery = true) }
    }

    @Test
    fun android10_aQueueBackpressurePause_isNotMistakenForThrottling() = runTest(dispatcher) {
        assertNoWarningAcrossASurveyPause { it.copy(pausedForBackpressure = true) }
    }

    /** One scan, the survey pauses for longer than the gap check allows, resumes, and scans again. */
    private fun TestScope.assertNoWarningAcrossASurveyPause(pause: (SurveyRuntimeState) -> SurveyRuntimeState) {
        val surveyState = MutableStateFlow(SurveyRuntimeState())
        whenever(service.surveyStateFlow).thenReturn(surveyState)
        val viewModel = newViewModel(sdk = Build.VERSION_CODES.Q)
        viewModel.onServiceChanged(service)
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21")))
        advanceUntilIdle()

        surveyState.value = pause(surveyState.value)
        advanceUntilIdle()
        now += RATE_MS * WifiThrottling.GAP_FACTOR * 4
        surveyState.value = SurveyRuntimeState()
        advanceUntilIdle()
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21")))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.throttleWarning)
        assertEquals(2, viewModel.uiState.value.scanNumber)
    }

    @Test
    fun android11AndLater_neverWatchTheSurveyPauses() {
        newViewModel().onServiceChanged(service)

        verify(service, never()).surveyStateFlow
    }

    @Test
    fun aBlockedList_dropsItsRowsAndCounts_andKeepsTheUsersChoices() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21", ssid = "Acme-Corp"), wifi("02:00:A4:11:F0:22", ssid = "Acme-Guest")))
        advanceUntilIdle()
        viewModel.setFilterQuery("Acme")
        viewModel.setSortIndex(1)
        viewModel.setMode(WifiListMode.FLAT)

        scanState = WifiScanState.LOCATION_OFF
        viewModel.refresh()

        val blocked = viewModel.uiState.value
        assertEquals(emptyList<WifiDisplayItem>(), blocked.items)
        assertEquals(0, blocked.apCount)
        assertEquals(0, blocked.visibleApCount)
        assertEquals(0, blocked.scanNumber)
        assertEquals(emptyList<String>(), viewModel.seedRecords().map { it.wifiBeaconRecord.data.bssid })
        assertEquals("Acme", blocked.filter.searchQuery)
        assertEquals(1, blocked.sortIndex)
        assertEquals(WifiListMode.FLAT, blocked.mode)

        // Back again: nothing stale until the next scan
        scanState = WifiScanState.READY
        viewModel.refresh()
        assertEquals(emptyList<WifiDisplayItem>(), viewModel.uiState.value.items)
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:D8:4E:9A:0C", ssid = "Acme-Lab")))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.apCount)
    }

    @Test
    fun rowsFrozenWithPause_surviveABlock_andClearWhenResumedWhileBlocked() = runTest(dispatcher) {
        val viewModel = newViewModel()
        viewModel.onServiceChanged(service)
        viewModel.onWifiBeaconSurveyRecords(listOf(wifi("02:00:A4:11:F0:21")))
        advanceUntilIdle()
        viewModel.togglePaused()

        scanState = WifiScanState.LOCATION_OFF
        viewModel.refresh()
        assertEquals(1, viewModel.uiState.value.apCount)

        scanState = WifiScanState.READY
        viewModel.refresh()
        assertEquals(1, viewModel.uiState.value.apCount)

        scanState = WifiScanState.LOCATION_OFF
        viewModel.refresh()
        viewModel.togglePaused()
        assertEquals(0, viewModel.uiState.value.apCount)
        assertEquals(emptyList<WifiDisplayItem>(), viewModel.uiState.value.items)
    }

    private fun newViewModel(sdk: Int = 36) = WifiListViewModel(
        application = RuntimeEnvironment.getApplication(),
        environment = { scanState },
        throttleProbe = { throttle },
        scanRateMs = { RATE_MS },
        clock = { now },
        mainDispatcher = dispatcher,
        sdk = sdk,
    )

    private companion object {
        const val RATE_MS = 15_000L
    }
}
