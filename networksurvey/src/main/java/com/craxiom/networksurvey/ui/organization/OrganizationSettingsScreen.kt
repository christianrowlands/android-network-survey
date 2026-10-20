package com.craxiom.networksurvey.ui.organization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler

/** The Organization settings page with its view model. */
@Composable
fun OrganizationSettingsScreen(onBack: () -> Unit, viewModel: OrganizationSettingsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var recording by remember { mutableStateOf(false) }
    ServiceConnectionHandler { service -> recording = service?.surveyStateFlow?.value?.isRecording == true }

    val actions = remember(viewModel) {
        object : OrganizationSettingsActions {
            override fun onBack() = onBack()
            override fun onUseOwnSettings() = viewModel.setOverride(true)
            override fun onRestore() = viewModel.setOverride(false)
        }
    }
    OrganizationSettingsContent(state, recording, actions)
}
