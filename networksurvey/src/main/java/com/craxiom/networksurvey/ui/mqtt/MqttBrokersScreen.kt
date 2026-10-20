package com.craxiom.networksurvey.ui.mqtt

import android.content.res.Resources
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.common.CameraPermissionNotice
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.rememberCameraPermissionRequest
import com.craxiom.networksurvey.ui.survey.rememberSurveyPermissionFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The MQTT page with its view model, the camera permission for scanning, and the snackbars.
 *
 * @param scannedSettings A broker scanned by the QR scanner, waiting to be added.
 * @param editorMessage A message the broker editor sent back after a save or a remove.
 */
@Composable
fun MqttBrokersScreen(
    scannedSettings: StateFlow<MqttConnectionSettings?>,
    onScannedSettingsConsumed: () -> Unit,
    editorMessage: StateFlow<MqttMessage?>,
    onEditorMessageConsumed: () -> Unit,
    onBack: () -> Unit,
    onOpenEditor: (profileId: String?) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenOrganizationSettings: () -> Unit,
    viewModel: MqttBrokersViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val camera = rememberCameraPermissionRequest(onOpenScanner)
    val permissions = rememberSurveyPermissionFlow()

    ServiceConnectionHandler { viewModel.setService(it) }

    val scanned by scannedSettings.collectAsStateWithLifecycle()
    LaunchedEffect(scanned) {
        scanned?.let { settings ->
            permissions.ensureLocalNetwork(MQTT_ONLY) { viewModel.importFromQr(settings) }
            onScannedSettingsConsumed()
        }
    }
    val fromEditor by editorMessage.collectAsStateWithLifecycle()
    LaunchedEffect(fromEditor) {
        fromEditor?.let {
            viewModel.show(it)
            onEditorMessageConsumed()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> showMessage(snackbarHostState, resources, message, viewModel::undoSelect) }
    }

    val actions = remember(viewModel, camera, permissions) {
        object : MqttBrokersActions {
            override fun onBack() = onBack()
            override fun onSelect(id: String) = permissions.ensureLocalNetwork(MQTT_ONLY) { viewModel.select(id) }
            override fun onEdit(id: String) = onOpenEditor(id)
            override fun onAdd() = onOpenEditor(null)
            override fun onScan() = camera.request()
            override fun onSetClientId(clientId: String) = viewModel.setClientId(clientId)
            override fun onSetDeviceStatus(enabled: Boolean) = viewModel.setDeviceStatusEnabled(enabled)
            override fun onSeeOrganizationSettings() = onOpenOrganizationSettings()
            override fun onUseOwnSettings() = viewModel.useOwnSettings()
        }
    }
    MqttBrokersContent(state, actions, snackbarHostState) { CameraPermissionNotice(camera) }
}

/** The destination the MQTT pages connect to, for the local network request. */
internal val MQTT_ONLY = listOf(Destination.MQTT)

/** Shows [message]; a switch while streaming offers Undo, which calls [undo] with the old broker. */
private suspend fun showMessage(
    host: SnackbarHostState,
    resources: Resources,
    message: MqttMessage,
    undo: (String?) -> Unit,
) {
    if (message is MqttMessage.StreamingTo) {
        val result = host.showSnackbar(
            message = resources.getString(R.string.mqtt_streaming_to, message.name),
            actionLabel = resources.getString(R.string.mqtt_undo),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) undo(message.previousId)
        return
    }
    host.showSnackbar(mqttMessageText(resources, message))
}

/** The words for an MQTT page message. */
fun mqttMessageText(resources: Resources, message: MqttMessage): String = when (message) {
    is MqttMessage.Using -> resources.getString(R.string.mqtt_using, message.name)
    is MqttMessage.StreamingTo -> resources.getString(R.string.mqtt_streaming_to, message.name)
    is MqttMessage.Saved -> savedText(resources, message.name, message.outcome)
    is MqttMessage.Removed -> resources.getString(R.string.mqtt_removed, message.name)
    is MqttMessage.QrImported -> when {
        message.outcome != SaveOutcome.NONE -> savedText(resources, message.name, message.outcome)
        message.updatedExisting -> resources.getString(R.string.mqtt_qr_updated, message.name)
        else -> resources.getString(R.string.mqtt_qr_added)
    }

    MqttMessage.NotABrokerCode -> resources.getString(R.string.mqtt_qr_not_broker)
    MqttMessage.OwnSettingsInUse -> resources.getString(R.string.mqtt_override_used)
}

private fun savedText(resources: Resources, name: String, outcome: SaveOutcome): String = when (outcome) {
    SaveOutcome.NONE -> resources.getString(R.string.mqtt_saved, name)
    SaveOutcome.MQTT_TURNED_ON -> resources.getString(R.string.mqtt_saved_mqtt_on, name)
    SaveOutcome.TURN_ON_TO_STREAM -> resources.getString(R.string.mqtt_saved_turn_on, name)
    SaveOutcome.RECONNECTING -> resources.getString(R.string.mqtt_saved_reconnecting, name)
    SaveOutcome.STARTING -> resources.getString(R.string.mqtt_saved_starting, name)
}
