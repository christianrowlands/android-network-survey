package com.craxiom.networksurvey.ui.bluetooth.list

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.location.LocationManager
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.data.BluetoothCompanyNameProvider
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.services.controller.BluetoothController
import com.craxiom.networksurvey.ui.bluetooth.list.model.AndroidBluetoothEnvironment
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothDeviceDisplay
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothDeviceList
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothDeviceState
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothDisplayFilter
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothEnvironment
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothListUiState
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothScanState
import com.craxiom.networksurvey.ui.signals.ScanConditions
import com.craxiom.networksurvey.ui.signals.ScanListenerRegistration
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** How often devices no longer heard are dropped. */
private const val PRUNE_INTERVAL_MS = 5_000L

/** The most often the rows are rebuilt from new records, so a signal sort does not reshuffle constantly. */
internal const val ROW_REFRESH_MS = 1_000L

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The Bluetooth list: the devices heard, merged by address and dropped once they stop being heard,
 * plus the user's sort, filter and pause. Activity-scoped, so the Signals app bar's filter sheet and
 * the list share it and the list survives sub-tab switches.
 *
 * The record listener is registered only while the screen is bound to the service
 * ([onServiceChanged]) and the phone can scan; registering starts the service's Bluetooth scanning,
 * which would otherwise toast about a missing permission, or give up while Bluetooth is off and not
 * start again. Registering once the obstacle clears is what restarts it.
 */
class BluetoothListViewModel internal constructor(
    application: Application,
    private val environment: BluetoothEnvironment,
    private val companyNames: (List<String>, String) -> String,
    private val scanRateMs: () -> Long,
    private val clock: () -> Long,
    private val dispatcher: CoroutineDispatcher,
) : AndroidViewModel(application), IBluetoothSurveyRecordListener {

    constructor(application: Application) : this(
        application = application,
        environment = AndroidBluetoothEnvironment(application),
        companyNames = { uuids, companyId ->
            BluetoothCompanyNameProvider.getInstance(application).resolveCompanyName(uuids, companyId)
        },
        scanRateMs = {
            PreferenceUtils.getScanRatePreferenceMs(
                NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS,
                application,
            ).toLong()
        },
        clock = System::currentTimeMillis,
        dispatcher = Dispatchers.Default,
    ) {
        // The first lookup parses the company list, so do it before any record needs it.
        viewModelScope.launch(Dispatchers.IO) { BluetoothCompanyNameProvider.getInstance(application) }
    }

    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)

    private var ticker: Job? = null

    @Volatile
    private var retentionMs = currentRetentionMs()

    private val devices = MutableStateFlow<Map<String, BluetoothDeviceState>>(emptyMap())

    /** The devices shown while paused; null while updates show live. */
    private val frozen = MutableStateFlow<Map<String, BluetoothDeviceState>?>(null)
    private val filter = MutableStateFlow(BluetoothDisplayFilter())
    private val sortIndex = MutableStateFlow(preferences.getInt(NetworkSurveyConstants.PROPERTY_BLUETOOTH_DEVICES_SORT_ORDER, 0))
    private val conditions = ScanConditions(
        context = application,
        actions = listOf(BluetoothAdapter.ACTION_STATE_CHANGED, LocationManager.PROVIDERS_CHANGED_ACTION),
        read = environment::scanState,
        onBroadcast = ::refresh,
    )
    private val scanState = conditions.state
    private val registration = ScanListenerRegistration(
        register = { it.registerBluetoothSurveyRecordListener(this) },
        unregister = { it.unregisterBluetoothSurveyRecordListener(this) },
        onRegistered = {
            prune()
            startTicker()
        },
        onUnregistered = {
            ticker?.cancel()
            ticker = null
        },
    )

    /** Company names by first UUID and company ID; resolving reads a large table. */
    private val companyNameCache = ConcurrentHashMap<String, String>()

    val uiState: StateFlow<BluetoothListUiState> = combine(
        // New records are throttled; a pause or resume shows at once, with the rows it froze.
        combine(devices.throttleLatest(ROW_REFRESH_MS), frozen) { live, paused -> Shown(paused ?: live, paused != null) },
        filter,
        sortIndex,
        scanState,
    ) { shown, filter, sort, scan ->
        val all = shown.devices.values.map(::display)
        BluetoothListUiState(
            rows = BluetoothDeviceList.sorted(all.filter(filter::matches), sort),
            totalCount = all.size,
            scanState = scan,
            paused = shown.paused,
            sortIndex = sort,
            filter = filter,
        )
    }.flowOn(dispatcher).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        BluetoothListUiState(sortIndex = sortIndex.value, scanState = scanState.value),
    )

    /** Called with the bound service, and with null before the screen unbinds. */
    fun onServiceChanged(newService: NetworkSurveyService?) =
        registration.onServiceChanged(newService, scanState.value == BluetoothScanState.READY)

    /**
     * Re-reads the scan interval and whether the phone can scan (on resume, after a permission
     * answer, and when Bluetooth or location is switched), and registers or unregisters to match.
     */
    fun refresh() {
        retentionMs = currentRetentionMs()
        // Nothing prunes while the list is away, so drop what went stale before it shows again.
        prune()
        registration.update(conditions.refresh() == BluetoothScanState.READY)
    }

    fun setSortIndex(index: Int) {
        sortIndex.value = index
        preferences.edit { putInt(NetworkSurveyConstants.PROPERTY_BLUETOOTH_DEVICES_SORT_ORDER, index) }
    }

    fun setFilterQuery(query: String) = filter.update { it.copy(query = query) }

    fun setNamedOnly(namedOnly: Boolean) = filter.update { it.copy(namedOnly = namedOnly) }

    fun setTrackersOnly(trackersOnly: Boolean) = filter.update { it.copy(trackersOnly = trackersOnly) }

    fun clearFilter() {
        filter.value = BluetoothDisplayFilter()
    }

    /** Freezes the rows on screen, or shows them live again. Records keep arriving either way. */
    fun togglePaused() = frozen.update { if (it == null) devices.value else null }

    /**
     * @return The record to open the details screen with, or null if the device is gone. While
     * paused, a row on screen may already have left the live list, so the frozen one is checked too.
     */
    fun recordFor(address: String): BluetoothRecordData? =
        (devices.value[address] ?: frozen.value?.get(address))?.detailsRecord

    override fun onBluetoothSurveyRecord(bluetoothRecord: BluetoothRecord) = onBluetoothSurveyRecords(listOf(bluetoothRecord))

    override fun onBluetoothSurveyRecords(bluetoothRecords: List<BluetoothRecord>) {
        val now = clock()
        devices.update { BluetoothDeviceList.merge(it, bluetoothRecords, now) }
    }

    override fun onCleared() {
        registration.release()
        conditions.close()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch(dispatcher) {
            while (isActive) {
                delay(PRUNE_INTERVAL_MS)
                prune()
            }
        }
    }

    private fun prune() {
        val now = clock()
        devices.update { BluetoothDeviceList.pruneStale(it, now, retentionMs) }
    }

    private fun currentRetentionMs(): Long = BluetoothController.displayRetentionMs(scanRateMs())

    private fun display(device: BluetoothDeviceState): BluetoothDeviceDisplay {
        val key = "${device.serviceUuids.firstOrNull().orEmpty()}|${device.companyId}"
        val company = companyNameCache.getOrPut(key) { companyNames(device.serviceUuids, device.companyId) }
        return BluetoothDeviceDisplay(
            address = device.address,
            name = device.name,
            rssi = device.rssi,
            companyName = company,
            tech = device.tech,
            tracker = device.findMy?.takeIf { it.isTracker },
        )
    }
}

/** The devices the rows are built from, and whether they are a paused snapshot. */
private data class Shown(val devices: Map<String, BluetoothDeviceState>, val paused: Boolean)

/** Emits the first value at once, then at most one value (the latest) per [periodMillis]. */
internal fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMillis)
}
