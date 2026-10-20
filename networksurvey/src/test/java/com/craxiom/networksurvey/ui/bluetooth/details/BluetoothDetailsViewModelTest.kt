package com.craxiom.networksurvey.ui.bluetooth.details

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.FakeRssiChart
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothScanState
import com.google.protobuf.FloatValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The Bluetooth details page's view model: registration and plotting this device's signal. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BluetoothDetailsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val service = mock<NetworkSurveyService>()
    private val chart = FakeRssiChart()
    private var scanState = BluetoothScanState.READY
    private val device = record(ADDRESS, -63f).data

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showing_seedsTheChartWithTheTappedReading() {
        newViewModel().show(device)

        assertEquals(-63f, chart.started)
    }

    @Test
    fun bluetoothOff_keepsTheListenerAway_untilItIsOn() {
        scanState = BluetoothScanState.BLUETOOTH_OFF
        val viewModel = newViewModel()
        viewModel.show(device)
        viewModel.onServiceChanged(service)
        verify(service, never()).registerBluetoothSurveyRecordListener(viewModel)

        scanState = BluetoothScanState.READY
        viewModel.refresh()

        verify(service).registerBluetoothSurveyRecordListener(viewModel)
    }

    @Test
    fun recordsAndBatches_plotThisDevice_orNotHeard() {
        val viewModel = newViewModel()
        viewModel.show(device)
        viewModel.onServiceChanged(service)

        viewModel.onBluetoothSurveyRecord(record(OTHER, -40f))
        viewModel.onBluetoothSurveyRecord(record(ADDRESS, -70f))
        viewModel.onBluetoothSurveyRecords(listOf(record(OTHER, -40f)))
        viewModel.onBluetoothSurveyRecords(listOf(record(OTHER, -40f), record(ADDRESS, null)))

        assertEquals(listOf(-70f, UNKNOWN_RSSI, UNKNOWN_RSSI), chart.plotted)
    }

    @Test
    fun aRecordAfterTheBindEnds_isNotPlotted() {
        val viewModel = newViewModel()
        viewModel.show(device)
        viewModel.onServiceChanged(service)
        viewModel.onServiceChanged(null)

        viewModel.onBluetoothSurveyRecord(record(ADDRESS, -70f))

        verify(service).unregisterBluetoothSurveyRecordListener(viewModel)
        assertEquals(emptyList<Float>(), chart.plotted)
    }

    private fun newViewModel() =
        BluetoothDetailsViewModel(RuntimeEnvironment.getApplication(), { scanState }, chart, dispatcher)

    private fun record(address: String, rssi: Float?): BluetoothRecord {
        val data = BluetoothRecordData.newBuilder().setSourceAddress(address)
        rssi?.let { data.setSignalStrength(FloatValue.of(it)) }
        return BluetoothRecord.newBuilder().setData(data).build()
    }

    private companion object {
        const val ADDRESS = "02:00:F4:12:00:9C"
        const val OTHER = "02:00:5C:8D:31:AA"
    }
}
