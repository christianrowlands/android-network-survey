package com.craxiom.networksurvey.ui.bluetooth.details

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
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
import com.craxiom.networksurvey.ui.signals.SignalsViewModel
import com.craxiom.networksurvey.ui.util.copyWithSystemConfirmation

/**
 * The Bluetooth details route: binds to the survey service while the page shows, re-checks the
 * permissions, location and Bluetooth on every resume, and pauses the chart while away. Shows
 * nothing without a device (a route opened without one).
 */
@Composable
fun BluetoothDetailsRoute(device: BluetoothRecordData?, onBack: () -> Unit) {
    if (device == null) return
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val viewModel: BluetoothDetailsViewModel = viewModel()
    val sharedViewModel: SharedViewModel = viewModel(viewModelStoreOwner = activity)
    val signalsViewModel: SignalsViewModel = viewModel(viewModelStoreOwner = activity)
    // Only the first call counts, so a rotation keeps the chart's history.
    LaunchedEffect(viewModel, device) { viewModel.show(device) }

    ServiceConnectionHandler(viewModel::onServiceChanged)
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        viewModel.setChartPaused(false)
        onPauseOrDispose { viewModel.setChartPaused(true) }
    }
    val permission = rememberPermissionRequest(BluetoothEnvironment.PERMISSIONS, onAnswered = viewModel::refresh) {
        viewModel.refresh()
    }
    val turnOnBluetooth = rememberTurnOnBluetooth(onResult = viewModel::refresh)

    val rssi by viewModel.rssi.collectAsStateWithLifecycle()
    val intervals by signalsViewModel.intervals.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    BluetoothDetailsScreen(
        device = device,
        currentRssi = rssi.takeIf { it != UNKNOWN_RSSI }?.toInt(),
        scanRateSeconds = intervals.getValue(SignalsSubTab.BLUETOOTH),
        scanState = scanState,
        permissionAccess = permission.access,
        chart = viewModel.chart,
        actions = remember(permission, turnOnBluetooth) {
            RouteActions(context, device, sharedViewModel, permission, turnOnBluetooth, onBack)
        },
    )
}

/** Wires [BluetoothDetailsActions] to the settings page, the permission request and the system screens. */
private class RouteActions(
    private val context: Context,
    private val device: BluetoothRecordData,
    private val sharedViewModel: SharedViewModel,
    private val permission: PermissionRequest,
    private val turnOnBluetooth: () -> Unit,
    private val onBack: () -> Unit,
) : BluetoothDetailsActions {
    override fun onBack() = onBack.invoke()

    override fun onCopyAddress() = copyWithSystemConfirmation(
        context,
        context.getString(R.string.bluetooth_address_clip_label),
        device.sourceAddress,
        context.getString(R.string.bluetooth_address_copied),
    )

    override fun onOpenSettings() = sharedViewModel.triggerNavigationToSettings()
    override fun onAllow() = permission.request()
    override fun onOpenAppSettings() = openAppSettings(context)
    override fun onTurnOnLocation() = openLocationSettings(context)
    override fun onTurnOnBluetooth() = turnOnBluetooth()
}
