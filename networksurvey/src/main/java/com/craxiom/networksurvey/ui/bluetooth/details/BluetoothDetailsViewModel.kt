package com.craxiom.networksurvey.ui.bluetooth.details

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.RssiChartClock
import com.craxiom.networksurvey.ui.RssiChartSink
import com.craxiom.networksurvey.ui.StoredRssiChart
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.bluetooth.list.model.AndroidBluetoothEnvironment
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothEnvironment
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothScanState
import com.craxiom.networksurvey.ui.signals.ScanConditions
import com.craxiom.networksurvey.ui.signals.ScanListenerRegistration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The navigation saved state key the Bluetooth details page reads its device from. */
const val BLUETOOTH_DATA_KEY = "bluetoothData"

/**
 * The Bluetooth details page: the one device it shows and its signal over time. Scoped to the
 * page's navigation entry.
 *
 * The record listener is registered only while the page is bound to the service and the phone can
 * scan (registering starts the service's Bluetooth scanning, which toasts without the permission
 * and gives up while Bluetooth is off); each scan plots this device's signal, or "not heard" when a
 * scan missed it. Records arrive on the survey executor and are plotted on the main thread.
 */
class BluetoothDetailsViewModel internal constructor(
    application: Application,
    environment: BluetoothEnvironment,
    private val chartSink: RssiChartSink,
    private val mainDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application), IBluetoothSurveyRecordListener {

    constructor(application: Application) : this(
        application = application,
        environment = AndroidBluetoothEnvironment(application),
        chartSink = StoredRssiChart(),
        mainDispatcher = Dispatchers.Main,
    )

    /** The device this page shows, once the page has said which. */
    private val mutableDevice = MutableStateFlow<BluetoothRecordData?>(null)

    private val conditions = ScanConditions(
        context = application,
        actions = listOf(BluetoothAdapter.ACTION_STATE_CHANGED, LocationManager.PROVIDERS_CHANGED_ACTION),
        read = environment::scanState,
        onBroadcast = ::applyScanState,
    )

    /** What stops the phone from scanning, if anything. */
    val scanState: StateFlow<BluetoothScanState> = conditions.state

    private val registration = ScanListenerRegistration(
        register = { it.registerBluetoothSurveyRecordListener(this) },
        unregister = { it.unregisterBluetoothSurveyRecordListener(this) },
    )

    /** The chart to draw (null in tests). */
    val chart = chartSink.chart

    private val chartClock = RssiChartClock(chartSink, canScan = conditions.state.value == BluetoothScanState.READY)

    /**
     * The latest reading for the header: [UNKNOWN_RSSI] when the last scans missed it, or while the
     * phone cannot scan (the last reading is no longer live then).
     */
    val rssi: StateFlow<Float> = combine(chartSink.chart?.rssiFlow ?: MutableStateFlow(UNKNOWN_RSSI), conditions.state) { rssi, state ->
        if (state == BluetoothScanState.READY) rssi else UNKNOWN_RSSI
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UNKNOWN_RSSI)

    /** Shows [device]; only the first call counts, so a recomposition or rotation keeps the history. */
    fun show(device: BluetoothRecordData) {
        if (mutableDevice.value != null) return
        mutableDevice.value = device
        chartSink.start(if (device.hasSignalStrength()) device.signalStrength.value else UNKNOWN_RSSI)
    }

    /** Called with the bound service, and with null before the page unbinds. */
    fun onServiceChanged(service: NetworkSurveyService?) =
        registration.onServiceChanged(service, conditions.state.value == BluetoothScanState.READY)

    /** Re-reads whether the phone can scan (on resume and after a permission answer). */
    fun refresh() {
        conditions.refresh()
        applyScanState()
    }

    /** Stops the chart's clock while the page is away. */
    fun setChartPaused(paused: Boolean) = chartClock.setPageShowing(!paused)

    /** A device reported on its own (an LE advertisement): plotted only if it is this device. */
    override fun onBluetoothSurveyRecord(bluetoothRecord: BluetoothRecord) {
        val address = mutableDevice.value?.sourceAddress ?: return
        if (bluetoothRecord.data.sourceAddress == address) plot(signalOf(bluetoothRecord))
    }

    /** A scan's batch: this device's signal, or "not heard" when the batch missed it. */
    override fun onBluetoothSurveyRecords(bluetoothRecords: List<BluetoothRecord>) {
        val address = mutableDevice.value?.sourceAddress ?: return
        plot(bluetoothRecords.firstOrNull { it.data.sourceAddress == address }?.let(::signalOf) ?: UNKNOWN_RSSI)
    }

    override fun onCleared() {
        registration.release()
        conditions.close()
        chartSink.clear()
    }

    private fun plot(rssi: Float) {
        viewModelScope.launch(mainDispatcher) {
            // A record in flight when the page unregistered is not news.
            if (registration.isRegistered) chartSink.plot(rssi)
        }
    }

    private fun applyScanState() {
        val ready = conditions.state.value == BluetoothScanState.READY
        registration.update(ready)
        chartClock.setCanScan(ready)
    }

    private fun signalOf(record: BluetoothRecord): Float =
        if (record.data.hasSignalStrength()) record.data.signalStrength.value else UNKNOWN_RSSI
}
