package com.craxiom.networksurvey.ui.mqtt

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.survey.rememberSurveyPermissionFlow
import kotlinx.coroutines.launch

/**
 * The broker editor with its view model. It closes after a save or a remove and hands the list a
 * message to show.
 *
 * @param profileId The broker to edit, or null to add one.
 */
@Composable
fun MqttBrokerEditorScreen(
    profileId: String?,
    onBack: () -> Unit,
    onDone: (MqttMessage) -> Unit,
    onShare: (MqttConnectionSettings) -> Unit,
    viewModel: MqttBrokerEditorViewModel = viewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentOnDone by rememberUpdatedState(onDone)
    val permissions = rememberSurveyPermissionFlow()

    LaunchedEffect(profileId) { viewModel.load(profileId) }
    LaunchedEffect(viewModel) { viewModel.done.collect { currentOnDone(it.message) } }
    ServiceConnectionHandler { viewModel.setService(it) }

    val actions = remember(viewModel, permissions) {
        object : MqttBrokerEditorActions {
            override fun onBack() = onBack()
            override fun onEdit(change: (MqttBrokerForm) -> MqttBrokerForm) = viewModel.edit(change)
            override fun onSave() = permissions.ensureLocalNetwork(MQTT_ONLY) { viewModel.save() }
            override fun onRemove() = viewModel.remove()
            override fun onShare() {
                scope.launch { viewModel.shareSettings()?.let(onShare) }
            }
        }
    }
    MqttBrokerEditorContent(form, actions, snackbarHostState)
}
