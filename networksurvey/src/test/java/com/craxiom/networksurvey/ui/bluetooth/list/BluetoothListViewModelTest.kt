package com.craxiom.networksurvey.ui.bluetooth.list

import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.services.controller.BluetoothController
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothListUiState
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothRecords.le
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothScanState
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

/** The Bluetooth list's view model: registration, records, pause, pruning, sort and filter. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BluetoothListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var scanState = BluetoothScanState.READY
    private var now = 0L
    private var companyLookups = 0
    private val service = mock<NetworkSurveyService>()
    private lateinit var viewModel: BluetoothListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit().clear().commit()
        viewModel = newViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun registersOnlyOnceTheScanCanRun() = runTest(dispatcher) {
        scanState = BluetoothScanState.PERMISSION_NEEDED
        viewModel.refresh()
        viewModel.onServiceChanged(service)
        verify(service, never()).registerBluetoothSurveyRecordListener(viewModel)

        scanState = BluetoothScanState.READY
        viewModel.refresh()
        viewModel.refresh()

        verify(service, times(1)).registerBluetoothSurveyRecordListener(viewModel)
        release()
    }

    @Test
    fun bluetoothTurnedOffAndOn_unregistersThenRegistersAgain() = runTest(dispatcher) {
        viewModel.onServiceChanged(service)

        scanState = BluetoothScanState.BLUETOOTH_OFF
        viewModel.refresh()
        verify(service).unregisterBluetoothSurveyRecordListener(viewModel)

        scanState = BluetoothScanState.READY
        viewModel.refresh()
        verify(service, times(2)).registerBluetoothSurveyRecordListener(viewModel)
        release()
    }

    @Test
    fun releasingTheService_unregisters() = runTest(dispatcher) {
        viewModel.onServiceChanged(service)

        viewModel.onServiceChanged(null)

        verify(service).unregisterBluetoothSurveyRecordListener(viewModel)
    }

    @Test
    fun recordsFromTheSurveyThread_showAsRows() = runTest(dispatcher) {
        val state = collect()
        val thread = Thread { viewModel.onBluetoothSurveyRecords(listOf(le("AA", rssi = -50f), le("BB", rssi = -70f))) }
        thread.start()
        thread.join()
        settle()

        assertEquals(listOf("AA", "BB"), state().rows.map { it.address })
        assertEquals(2, state().totalCount)
    }

    @Test
    fun pause_freezesTheRowsButKeepsListening() = runTest(dispatcher) {
        val state = collect()
        viewModel.onBluetoothSurveyRecord(le("AA"))
        settle()

        viewModel.togglePaused()
        viewModel.onBluetoothSurveyRecord(le("BB"))
        settle()
        assertTrue(state().paused)
        assertEquals(listOf("AA"), state().rows.map { it.address })

        viewModel.togglePaused()
        settle()
        assertFalse(state().paused)
        assertEquals(setOf("AA", "BB"), state().rows.map { it.address }.toSet())
    }

    @Test
    fun pausing_showsAtOnce_evenRightAfterNewRows() = runTest(dispatcher) {
        val state = collect()
        viewModel.onBluetoothSurveyRecord(le("AA"))
        runCurrent()

        viewModel.togglePaused()
        runCurrent()

        assertTrue(state().paused)
        assertEquals(listOf("AA"), state().rows.map { it.address })
    }

    @Test
    fun aRowFrozenByPause_stillOpensAfterLeavingTheLiveList() = runTest(dispatcher) {
        viewModel.onBluetoothSurveyRecord(le("AA", name = "Keys"))
        viewModel.togglePaused()

        now = BluetoothController.displayRetentionMs(NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS * 1000L) + 1
        viewModel.refresh()

        assertEquals("Keys", viewModel.recordFor("AA")?.otaDeviceName)
    }

    @Test
    fun devicesNoLongerHeard_areDroppedByTheTicker() = runTest(dispatcher) {
        val state = collect()
        viewModel.onServiceChanged(service)
        viewModel.onBluetoothSurveyRecord(le("AA"))
        settle()
        assertEquals(1, state().totalCount)

        now = BluetoothController.displayRetentionMs(NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS * 1000L) + 1
        advanceTimeBy(6_000)
        settle()

        assertEquals(0, state().totalCount)
        release()
    }

    @Test
    fun theSortIsSavedAndReadBack() = runTest(dispatcher) {
        viewModel.setSortIndex(BluetoothSort.NAME)

        assertEquals(BluetoothSort.NAME, newViewModel().uiState.value.sortIndex)
    }

    @Test
    fun theFilterNarrowsTheRowsButNotTheTotal() = runTest(dispatcher) {
        val state = collect()
        viewModel.onBluetoothSurveyRecords(listOf(le("AA", name = "Keys"), le("BB")))
        viewModel.setNamedOnly(true)
        settle()

        assertEquals(listOf("AA"), state().rows.map { it.address })
        assertEquals(2, state().totalCount)

        viewModel.clearFilter()
        settle()
        assertEquals(2, state().visibleCount)
    }

    @Test
    fun companyNames_areLookedUpOncePerCompany() = runTest(dispatcher) {
        val state = collect()
        viewModel.onBluetoothSurveyRecords(listOf(le("AA", companyId = "4c"), le("BB", companyId = "4c")))
        settle()

        assertEquals("company 4c", state().rows.first().companyName)
        assertEquals(1, companyLookups)
    }

    @Test
    fun theScanStateFollowsTheEnvironment() = runTest(dispatcher) {
        val state = collect()
        scanState = BluetoothScanState.LOCATION_OFF
        viewModel.refresh()
        settle()

        assertEquals(BluetoothScanState.LOCATION_OFF, state().scanState)
    }

    @Test
    fun detailsOpenWithTheDevicesRecord() {
        viewModel.onBluetoothSurveyRecord(le("AA", name = "Keys"))

        assertEquals("Keys", viewModel.recordFor("AA")?.otaDeviceName)
        assertEquals(null, viewModel.recordFor("ZZ"))
    }

    private fun newViewModel() = BluetoothListViewModel(
        application = RuntimeEnvironment.getApplication(),
        environment = { scanState },
        companyNames = { _, companyId ->
            companyLookups++
            if (companyId.isEmpty()) "" else "company $companyId"
        },
        scanRateMs = { NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS * 1000L },
        clock = { now },
        dispatcher = dispatcher,
    )

    /**
     * Unbinds, which also stops the prune ticker; a ticker left running would keep the test
     * scheduler busy forever and runTest would never finish.
     */
    private fun release() = viewModel.onServiceChanged(null)

    /** Keeps the state flow subscribed for the test, and returns a reader of its latest value. */
    private fun TestScope.collect(): () -> BluetoothListUiState {
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return { viewModel.uiState.value }
    }

    /** Lets the throttled rows catch up. */
    private fun TestScope.settle() {
        advanceTimeBy(ROW_REFRESH_MS + 1)
        runCurrent()
    }
}
