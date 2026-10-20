package com.craxiom.networksurvey.ui.bluetooth.list.model

import androidx.compose.runtime.Immutable

/** Whether the phone can scan for Bluetooth devices, and if not, the first thing in the way. */
enum class BluetoothScanState {
    /** The phone has no Bluetooth radio. */
    NOT_SUPPORTED,

    /** Nearby devices (Android 12 and later) or location has not been allowed. */
    PERMISSION_NEEDED,

    /** Location services are off, so Android returns no scan results. */
    LOCATION_OFF,

    /** Bluetooth is turned off. */
    BLUETOOTH_OFF,

    /** Nothing is in the way. */
    READY,
}

/**
 * What the Bluetooth list shows.
 *
 * @property rows The devices that pass the filter, in the chosen order.
 * @property totalCount How many devices the list holds before the filter.
 * @property paused True while the user has paused updates; the rows are then frozen.
 * @property sortIndex The index in `R.array.bluetooth_sort_options`.
 */
@Immutable
data class BluetoothListUiState(
    val rows: List<BluetoothDeviceDisplay> = emptyList(),
    val totalCount: Int = 0,
    val scanState: BluetoothScanState = BluetoothScanState.READY,
    val paused: Boolean = false,
    val sortIndex: Int = BluetoothSort.SIGNAL,
    val filter: BluetoothDisplayFilter = BluetoothDisplayFilter(),
) {
    /** How many devices show after the filter. */
    val visibleCount: Int get() = rows.size
}
