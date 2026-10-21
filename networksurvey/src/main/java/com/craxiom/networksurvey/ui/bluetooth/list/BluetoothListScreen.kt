package com.craxiom.networksurvey.ui.bluetooth.list

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothEnvironment
import com.craxiom.networksurvey.ui.bluetooth.rememberTurnOnBluetooth
import com.craxiom.networksurvey.ui.common.PermissionRequest
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.common.openAppSettings
import com.craxiom.networksurvey.ui.common.openLocationSettings
import com.craxiom.networksurvey.ui.common.rememberPermissionRequest
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.signals.SignalsSubTab
import com.craxiom.networksurvey.ui.signals.signalsStripWords
import com.craxiom.networksurvey.ui.util.copyWithSystemConfirmation

/**
 * The Bluetooth sub-tab of Signals: binds to the survey service while it shows, re-checks the
 * permissions and radios on every resume, and asks for nothing until the user taps the one button
 * that fixes what is missing.
 */
@Composable
fun BluetoothListScreen() {
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val viewModel: BluetoothListViewModel = viewModel(viewModelStoreOwner = activity)
    val sharedViewModel: SharedViewModel = viewModel(viewModelStoreOwner = activity)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ServiceConnectionHandler(viewModel::onServiceChanged)
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val permission = rememberPermissionRequest(BluetoothEnvironment.PERMISSIONS, onAnswered = viewModel::refresh) {
        viewModel.refresh()
    }
    val turnOnBluetooth = rememberTurnOnBluetooth(onResult = viewModel::refresh)

    BluetoothListContent(
        state = state,
        stripWords = signalsStripWords(SignalsSubTab.BLUETOOTH),
        permissionAccess = permission.access,
        actions = remember(viewModel, permission, turnOnBluetooth) {
            ScreenActions(context, viewModel, sharedViewModel, permission, turnOnBluetooth)
        },
    )
}

/** Wires [BluetoothListActions] to the view models, the permission request and the system screens. */
private class ScreenActions(
    private val context: Context,
    private val viewModel: BluetoothListViewModel,
    private val sharedViewModel: SharedViewModel,
    private val permission: PermissionRequest,
    private val turnOnBluetooth: () -> Unit,
) : BluetoothListActions {

    override fun onDeviceClick(address: String) {
        val record = viewModel.recordFor(address) ?: return
        sharedViewModel.triggerNavigationToBluetooth(record)
    }

    override fun onDeviceLongClick(address: String) = copyWithSystemConfirmation(
        context,
        context.getString(R.string.bluetooth_address_clip_label),
        address,
        context.getString(R.string.bluetooth_address_copied),
    )

    override fun onSortSelected(index: Int) = viewModel.setSortIndex(index)

    override fun onTogglePause() = viewModel.togglePaused()

    override fun onClearFilter() = viewModel.clearFilter()

    override fun onFilterQueryChanged(query: String) = viewModel.setFilterQuery(query)

    override fun onNamedOnlyChanged(namedOnly: Boolean) = viewModel.setNamedOnly(namedOnly)

    override fun onTrackersOnlyChanged(trackersOnly: Boolean) = viewModel.setTrackersOnly(trackersOnly)

    override fun onAllow() = permission.request()

    override fun onOpenAppSettings() = openAppSettings(context)

    override fun onTurnOnLocation() = openLocationSettings(context)

    override fun onTurnOnBluetooth() = turnOnBluetooth()
}
