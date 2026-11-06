package com.craxiom.networksurvey.ui.scout

import android.Manifest
import android.content.res.Resources
import android.os.Build
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.AppPermissions
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.ScoutPairingState
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.ui.bluetooth.rememberTurnOnBluetooth
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.rememberPermissionRequest
import com.craxiom.networksurvey.ui.common.requestOrOpenSettings
import kotlinx.coroutines.launch

/**
 * The NS Scout page with its view model: binds the survey service (for the Wi-Fi source, Stop and
 * the recording lock), asks for the Nearby devices permission and Bluetooth, shares the installer
 * link and shows what happened in a snackbar. [onOpenFirmware] opens the firmware update.
 */
@Composable
fun ScoutScreen(onBack: () -> Unit, onOpenFirmware: () -> Unit = {}, viewModel: ScoutPageViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pairing by viewModel.pairingState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    ServiceConnectionHandler(viewModel::onServiceChanged)
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }

    val nearby = rememberPermissionRequest(SCAN_PERMISSIONS, onAnswered = viewModel::refreshScanBlock) { viewModel.startScan() }
    val turnOnBluetooth = rememberTurnOnBluetooth(onResult = viewModel::startScan)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val bluetoothAction = event is ScoutPageEvent.Removed || event is ScoutPageEvent.Erased
            // Longer for what has an action or a second sentence to read
            val long = bluetoothAction || (event is ScoutPageEvent.Paired && event.duringSurvey)
            launch {
                val result = snackbarHostState.showSnackbar(
                    message = message(resources, event),
                    actionLabel = if (bluetoothAction) resources.getString(R.string.scout_action_bluetooth_settings) else null,
                    duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) ScoutLinks.openBluetoothSettings(context)
            }
        }
    }

    val actions = remember(viewModel, nearby, turnOnBluetooth) {
        object : ScoutPageActions {
            override fun onBack() = onBack()
            override fun onShareInstallLink() {
                ScoutLinks.shareInstaller(context)
            }

            override fun onOpenScoutSite() {
                ScoutLinks.openScoutSite(context)
            }

            override fun onSetSource(source: WifiSource) = viewModel.setSource(source)
            override fun onStopSurvey() = viewModel.stopSurvey()
            override fun onRemove() = viewModel.remove()
            override fun onErase() = viewModel.erasePairings()
            override fun onTurnOnBluetooth() = turnOnBluetooth()
            override fun onAllowNearby() = nearby.requestOrOpenSettings(context)
            override fun onOpenBluetoothSettings() {
                ScoutLinks.openBluetoothSettings(context)
            }

            override fun onUpdateFirmware() = onOpenFirmware()
            override fun onStartScan() = viewModel.startScan()
            override fun onPair(nearby: NearbyScout) = viewModel.pair(nearby)
            override fun onResultShown(result: ScoutPairingState) = viewModel.onPairingShown(result)
            override fun onDismissPairing() = viewModel.stopScan()
        }
    }
    ScoutPageContent(state.copy(nearbyAccess = nearby.access), pairing.copy(nearbyAccess = nearby.access), actions, snackbarHostState)
}

/** The permissions the pairing scan needs: Nearby devices from Android 12, location before it. */
private val SCAN_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    AppPermissions.BLUETOOTH_PERMISSIONS
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun message(resources: Resources, event: ScoutPageEvent): String = when (event) {
    is ScoutPageEvent.Removed -> resources.getString(R.string.scout_removed, event.scoutName)
    is ScoutPageEvent.Erased -> resources.getString(R.string.scout_erased, event.scoutName)
    is ScoutPageEvent.EraseFailed -> resources.getString(R.string.scout_erase_failed, event.scoutName)
    is ScoutPageEvent.Paired ->
        resources.getString(if (event.duringSurvey) R.string.scout_paired_recording else R.string.scout_paired, event.scoutName)
    is ScoutPageEvent.SourceChanged -> if (event.source == WifiSource.PHONE) {
        resources.getString(R.string.wifi_scout_now_phone)
    } else {
        resources.getString(R.string.scout_snackbar_scout, event.scoutName)
    }

    ScoutPageEvent.LockedWhileRecording -> resources.getString(R.string.wifi_scout_stop_to_switch)
    ScoutPageEvent.RemoveLockedWhileRecording -> resources.getString(R.string.scout_remove_blocked)
    ScoutPageEvent.EraseLockedWhileRecording -> resources.getString(R.string.scout_erase_blocked)
    ScoutPageEvent.ReplaceLockedWhileRecording -> resources.getString(R.string.scout_replace_blocked)
    ScoutPageEvent.NeedsLink -> resources.getString(R.string.scout_erase_needs_link)
}
