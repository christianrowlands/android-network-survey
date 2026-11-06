package com.craxiom.networksurvey.ui.scout.firmware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.AppPermissions
import com.craxiom.networksurvey.scout.firmware.FirmwareConsent
import com.craxiom.networksurvey.ui.bluetooth.rememberTurnOnBluetooth
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.rememberPermissionRequest
import com.craxiom.networksurvey.ui.common.requestOrOpenSettings

/**
 * The NS Scout firmware update screen with its view model: binds the survey service (the
 * recording lock), asks for Bluetooth and the Nearby devices permission and keeps the screen awake
 * while the update runs.
 *
 * @param onOpenScoutPage Opens the Scout page, where a lost pairing is fixed.
 */
@Composable
fun FirmwareUpdateScreen(
    onBack: () -> Unit,
    onOpenScoutPage: () -> Unit,
    viewModel: FirmwareUpdateViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ServiceConnectionHandler(viewModel::onServiceChanged)
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }
    val nearby = rememberPermissionRequest(AppPermissions.BLUETOOTH_PERMISSIONS, onAnswered = viewModel::refreshEnvironment) {
        viewModel.refreshEnvironment()
    }
    val turnOnBluetooth = rememberTurnOnBluetooth(onResult = viewModel::refreshEnvironment)

    val actions = remember(viewModel, nearby, turnOnBluetooth) {
        object : FirmwareUpdateActions {
            override fun onBack() = onBack()
            override fun onUpdate() = viewModel.update()
            override fun onAccept(consent: FirmwareConsent, accepted: Boolean) = viewModel.setAccepted(consent, accepted)
            override fun onCancel() = viewModel.cancel()
            override fun onResume() = viewModel.resume()
            override fun onStartOver() = viewModel.startOver()
            override fun onDone() {
                viewModel.done()
                onBack()
            }

            override fun onFixBlocker(blocker: FirmwareBlocker) = when (blocker) {
                FirmwareBlocker.PERMISSION -> nearby.requestOrOpenSettings(context)
                FirmwareBlocker.BLUETOOTH_OFF -> turnOnBluetooth()
                FirmwareBlocker.PAIRING_LOST -> onOpenScoutPage()
                FirmwareBlocker.RECORDING, FirmwareBlocker.WAITING -> Unit
            }
        }
    }
    FirmwareUpdateContent(
        state = state.copy(nearbyAccess = nearby.access),
        actions = actions,
        modifier = if (state.installing) Modifier.keepScreenOn() else Modifier,
    )
}
