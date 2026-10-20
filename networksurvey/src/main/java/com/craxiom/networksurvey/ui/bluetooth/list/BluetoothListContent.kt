package com.craxiom.networksurvey.ui.bluetooth.list

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothListUiState
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothScanState
import com.craxiom.networksurvey.ui.common.NsEmptyState
import com.craxiom.networksurvey.ui.common.NsLazyPanelItem
import com.craxiom.networksurvey.ui.common.NsSortSheet
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.signals.ListScanStrip
import com.craxiom.networksurvey.ui.signals.SignalsStateMessage

/** What the user can do on the Bluetooth list; the stateful screen wires these up. */
interface BluetoothListActions {
    fun onDeviceClick(address: String)
    fun onDeviceLongClick(address: String)
    fun onSortSelected(index: Int)
    fun onTogglePause()
    fun onClearFilter()
    fun onAllow()
    fun onOpenAppSettings()
    fun onTurnOnLocation()
    fun onTurnOnBluetooth()
}

/**
 * The Bluetooth list without its plumbing: the strip, then either what stops the scan (with the
 * one button that fixes it), "No Bluetooth devices found yet", "No devices match", or the devices.
 *
 * @param stateWords The Signals state word and its spoken form ("Live view", "Recording"), shown
 *                   while the list is live.
 * @param permissionAccess Whether the permissions can still be asked for or only granted in Settings.
 */
@Composable
fun BluetoothListContent(
    state: BluetoothListUiState,
    stateWords: Pair<String, String>,
    permissionAccess: PermissionAccess,
    actions: BluetoothListActions,
    modifier: Modifier = Modifier,
) {
    var sortSheetOpen by rememberSaveable { mutableStateOf(false) }
    val sortOptions = stringArrayResource(R.array.bluetooth_sort_options).toList()
    val (status, statusSpoken) = stripStatus(state, stateWords)

    Column(modifier.fillMaxSize()) {
        ListScanStrip(
            // While the scan cannot run the rows are hidden, so counting them would mislead.
            counts = if (state.scanState == BluetoothScanState.READY) listOf(deviceCount(state)) else emptyList(),
            status = status,
            statusSpoken = statusSpoken,
            live = state.scanState == BluetoothScanState.READY && !state.paused,
            sortLabel = sortOptions.getOrElse(state.sortIndex) { sortOptions.first() },
            onSortClick = { sortSheetOpen = true },
            paused = state.paused,
            onTogglePause = actions::onTogglePause,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        when {
            state.scanState != BluetoothScanState.READY -> ScanBlocked(state.scanState, permissionAccess, actions)
            state.totalCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NsEmptyState(text = stringResource(R.string.signals_bluetooth_empty), icon = R.drawable.ic_bluetooth)
            }

            state.rows.isEmpty() -> SignalsStateMessage(
                icon = R.drawable.ic_filter,
                text = stringResource(R.string.bluetooth_filter_empty_state),
                action = stringResource(R.string.wifi_filter_clear),
                onAction = actions::onClearFilter,
                prominent = false,
            )

            else -> DeviceList(state, actions)
        }
    }

    if (sortSheetOpen) {
        NsSortSheet(
            options = sortOptions,
            selectedIndex = state.sortIndex,
            onSelected = actions::onSortSelected,
            onDismiss = { sortSheetOpen = false },
        )
    }
}

@Composable
private fun DeviceList(state: BluetoothListUiState, actions: BluetoothListActions) {
    val listState = rememberLazyListState()
    val atTop by remember(listState) {
        derivedStateOf {
            !listState.isScrollInProgress && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    // A stronger device arriving above the first row would otherwise slide in out of view, since
    // the list keeps its first visible item; a user at the top stays at the top.
    if (atTop) {
        listState.requestScrollToItem(0)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        // No item animation: rows that re-sort every second would be sliding past each other all the
        // time, and the design keeps motion for the Ready to Recording change alone.
        itemsIndexed(items = state.rows, key = { _, device -> device.address }) { index, device ->
            NsLazyPanelItem(index = index, count = state.rows.size) {
                BluetoothDeviceRow(
                    device = device,
                    onClick = { actions.onDeviceClick(device.address) },
                    onLongClick = { actions.onDeviceLongClick(device.address) },
                )
            }
        }
    }
}

/** What stops the scan, with the one button that fixes it. */
@Composable
private fun ScanBlocked(scanState: BluetoothScanState, permissionAccess: PermissionAccess, actions: BluetoothListActions) {
    when (scanState) {
        BluetoothScanState.NOT_SUPPORTED -> SignalsStateMessage(
            icon = R.drawable.ic_bluetooth,
            text = stringResource(R.string.bluetooth_state_unsupported_body),
        )

        BluetoothScanState.PERMISSION_NEEDED -> {
            val blocked = permissionAccess == PermissionAccess.BLOCKED
            SignalsStateMessage(
                icon = R.drawable.ic_info,
                text = stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        R.string.bluetooth_state_permission_body
                    } else {
                        R.string.bluetooth_state_permission_body_location
                    }
                ),
                action = stringResource(if (blocked) R.string.open_settings else R.string.survey_location_allow),
                onAction = if (blocked) actions::onOpenAppSettings else actions::onAllow,
            )
        }

        BluetoothScanState.LOCATION_OFF -> SignalsStateMessage(
            icon = R.drawable.ic_location_off,
            text = stringResource(R.string.bluetooth_state_location_off_body),
            action = stringResource(R.string.survey_location_turn_on),
            onAction = actions::onTurnOnLocation,
        )

        BluetoothScanState.BLUETOOTH_OFF -> SignalsStateMessage(
            icon = R.drawable.ic_bluetooth,
            text = stringResource(R.string.bluetooth_state_off_body),
            action = stringResource(R.string.survey_location_turn_on),
            onAction = actions::onTurnOnBluetooth,
        )

        BluetoothScanState.READY -> Unit
    }
}

/** "12 devices", or "3 of 12 devices" while the filter hides some. */
@Composable
private fun deviceCount(state: BluetoothListUiState): String = if (state.filter.isActive) {
    pluralStringResource(R.plurals.bluetooth_list_device_count_filtered, state.totalCount, state.visibleCount, state.totalCount)
} else {
    pluralStringResource(R.plurals.bluetooth_list_device_count, state.totalCount, state.totalCount)
}

/**
 * The strip's status and its spoken form: what stops the scan wins over everything, then the
 * user's pause, then the Signals state word.
 */
@Composable
private fun stripStatus(state: BluetoothListUiState, stateWords: Pair<String, String>): Pair<String, String> {
    val resources = LocalResources.current
    val word = when (state.scanState) {
        BluetoothScanState.NOT_SUPPORTED -> resources.getString(R.string.bluetooth_state_unsupported)
        BluetoothScanState.PERMISSION_NEEDED -> resources.getString(R.string.bluetooth_state_permission)
        BluetoothScanState.LOCATION_OFF -> resources.getString(R.string.survey_location_off)
        BluetoothScanState.BLUETOOTH_OFF -> resources.getString(R.string.bluetooth_state_off)
        BluetoothScanState.READY -> if (state.paused) resources.getString(R.string.list_updates_paused) else return stateWords
    }
    return word to word
}
