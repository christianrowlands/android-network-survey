package com.craxiom.networksurvey.ui.cellular.details

import android.content.Intent
import android.os.Looper
import com.craxiom.networksurvey.SimChanges
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.services.controller.CellularController
import com.craxiom.networksurvey.ui.cellular.CellularChartViewModel
import com.craxiom.networksurvey.ui.cellular.details.model.CellularDetailsUiState
import com.craxiom.networksurvey.ui.cellular.details.model.CellularScanState
import com.craxiom.networksurvey.ui.cellular.details.model.ChartSample
import com.craxiom.networksurvey.ui.cellular.details.model.DetailsRecords
import com.craxiom.networksurvey.ui.cellular.details.model.SimInfo
import com.craxiom.networksurvey.util.TelephonyStateUtils.NrMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import kotlin.concurrent.thread

/** The Cellular view's state: registration, per-SIM routing, SIM changes and the NR card's clock. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CellularDetailsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private var scanState = CellularScanState.READY
    private var sims = listOf(SimInfo(1, 7, "Mint"))
    private var now = 0L
    private val charts = FakeCharts()
    private lateinit var viewModel: CellularDetailsViewModel
    private val service = mock<NetworkSurveyService>()
    private val default = CellularController.DEFAULT_SUBSCRIPTION_ID

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = CellularDetailsViewModel(
            application = RuntimeEnvironment.getApplication(),
            environment = { scanState },
            simsOf = { sims },
            charts = charts,
            clock = { now },
            mainDispatcher = dispatcher,
        )
        // uiState is shared while collected, as the screen does.
        viewModel.uiState.launchIn(CoroutineScope(dispatcher))
    }

    @After
    fun tearDown() {
        viewModel.onServiceChanged(null)
        Dispatchers.resetMain()
    }

    private val state: CellularDetailsUiState get() = viewModel.uiState.value

    @Test
    fun binding_registers_andAsksForAScan() {
        viewModel.onServiceChanged(service)

        verify(service).registerCellularSurveyRecordListener(viewModel)
        verify(service).runSingleCellularScan()
        assertTrue(state.bound)
        assertTrue(state.pagesKnown)
    }

    @Test
    fun withAPermissionMissing_itDoesNotRegister_untilARefreshFindsItGranted() {
        scanState = CellularScanState.PERMISSION_NEEDED
        viewModel.refresh()
        viewModel.onServiceChanged(service)

        verify(service, never()).registerCellularSurveyRecordListener(viewModel)
        assertEquals(CellularScanState.PERMISSION_NEEDED, state.scanState)

        scanState = CellularScanState.READY
        viewModel.refresh()
        verify(service).registerCellularSurveyRecordListener(viewModel)
    }

    @Test
    fun airplaneMode_unregisters_clears_andRegistersAgainWhenOff() {
        viewModel.onServiceChanged(service)
        viewModel.onCellularBatch(listOf(DetailsRecords.lte()), default)
        assertNotNull(state.sim.serving)

        scanState = CellularScanState.AIRPLANE_MODE
        RuntimeEnvironment.getApplication().sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()

        verify(service).unregisterCellularSurveyRecordListener(viewModel)
        assertEquals(CellularScanState.AIRPLANE_MODE, state.scanState)
        assertNull(state.sim.serving)
        assertFalse(state.bound)

        scanState = CellularScanState.READY
        viewModel.refresh()
        verify(service, times(2)).registerCellularSurveyRecordListener(viewModel)
    }

    @Test
    fun scansArrivingWhileNotListening_areDropped() {
        viewModel.onNetworkType(DetailsRecords.technology(), default)
        viewModel.onCellularBatch(listOf(DetailsRecords.lte()), default)

        assertFalse(state.sim.scanned)
        assertNull(state.sim.hero)
        assertEquals(emptyList<Pair<Int, ChartSample?>>(), charts.plots)
        assertNull(viewModel.latestServingCell.value)
    }

    @Test
    fun unbinding_unregisters() {
        viewModel.onServiceChanged(service)
        viewModel.onServiceChanged(null)

        verify(service).unregisterCellularSurveyRecordListener(viewModel)
        assertFalse(state.bound)
    }

    @Test
    fun oneSim_showsTheDefaultSubscriptionsScans() {
        viewModel.onServiceChanged(service)
        viewModel.onCellularBatch(listOf(DetailsRecords.lte()), 0)
        assertNull(state.sim.serving)

        viewModel.onCellularBatch(listOf(DetailsRecords.lte()), default)
        assertEquals(CellularProtocol.LTE, state.sim.serving!!.protocol)
        assertEquals(7, state.pages.single().sim.subscriptionId)
    }

    @Test
    fun twoSims_routeScansToTheirTabs() {
        sims = listOf(SimInfo(1, 7, "Mint"), SimInfo(2, 9, "Other"))
        viewModel.onServiceChanged(service)
        viewModel.onCellularBatch(listOf(DetailsRecords.lte()), 7)
        viewModel.onCellularBatch(listOf(DetailsRecords.nr()), 9)

        assertEquals(2, state.pages.size)
        assertEquals(CellularProtocol.LTE, state.sim.serving!!.protocol)
        viewModel.selectPage(1)
        assertEquals(CellularProtocol.NR, state.sim.serving!!.protocol)
    }

    @Test
    fun scansFromABackgroundThread_arrive() {
        viewModel.onServiceChanged(service)
        thread {
            viewModel.onNetworkType(DetailsRecords.technology(), default)
            viewModel.onCellularBatch(listOf(DetailsRecords.lte()), default)
        }.join()

        assertNotNull(state.sim.hero)
        assertNotNull(state.sim.serving)
    }

    @Test
    fun aSimChange_forgetsTheCells_andReadsTheSimsAgain() {
        viewModel.onServiceChanged(service)
        viewModel.onCellularBatch(listOf(DetailsRecords.lte()), default)

        sims = listOf(SimInfo(1, 7, "Mint"), SimInfo(2, 9, "Other"))
        SimChanges.notifyChanged()

        assertEquals(2, state.pages.size)
        assertFalse(state.sim.scanned)
    }

    @Test
    fun theNrCard_goesIdleOnTheInjectedClock() {
        viewModel.onServiceChanged(service)
        val nsa = DetailsRecords.technology(nrMode = NrMode.NON_STANDALONE)
        viewModel.onNetworkType(nsa, default)
        viewModel.onCellularBatch(listOf(DetailsRecords.lte(), DetailsRecords.nrSecondary()), default)
        assertFalse(state.sim.nrCard!!.idle())

        now = 42_000
        viewModel.onNetworkType(nsa, default)
        viewModel.onCellularBatch(listOf(DetailsRecords.lte()), default)

        assertTrue(state.sim.nrCard!!.idle())
        assertEquals(42_000L, state.sim.nrLastSeenAgeMs)
    }

    @Test
    fun theServingCell_isOfferedToTheMap_andPlotted() {
        viewModel.onServiceChanged(service)
        viewModel.onCellularBatch(listOf(DetailsRecords.lte()), default)
        viewModel.onCellularBatch(listOf(DetailsRecords.lte(serving = false)), default)
        viewModel.onCellularBatch(emptyList(), default)

        assertEquals(default, viewModel.latestServingCell.value!!.subscriptionId)
        assertEquals(listOf(-79, null), charts.plots.map { it.second?.value })
    }

    /** Records what is plotted. */
    private class FakeCharts : CellularChartSink {
        val plots = mutableListOf<Pair<Int, ChartSample?>>()
        override fun chartFor(subscriptionId: Int): CellularChartViewModel? = null
        override fun plot(subscriptionId: Int, sample: ChartSample?) {
            plots += subscriptionId to sample
        }

        override fun setPaused(paused: Boolean) = Unit
        override fun clear() = Unit
    }
}
