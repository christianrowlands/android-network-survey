package com.craxiom.networksurvey.ui.grpc

import android.content.res.Resources
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.survey.rememberSurveyPermissionFlow

/**
 * The gRPC page with its view model. Saving the first server returns to the Survey screen with a
 * message, as the prototype does; saving changes stays here with a snackbar, so the user can watch
 * the stream reconnect (or fail to).
 *
 * @param onReturnToSurvey Shows the message on the Survey screen and goes there.
 */
@Composable
fun GrpcServerScreen(
    onBack: () -> Unit,
    onReturnToSurvey: (message: String) -> Unit,
    onOpenOrganizationSettings: () -> Unit,
    viewModel: GrpcServerViewModel = viewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val permissions = rememberSurveyPermissionFlow()
    val currentOnReturnToSurvey by rememberUpdatedState(onReturnToSurvey)

    ServiceConnectionHandler { viewModel.setService(it) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { saved ->
            val text = savedText(resources, saved.result)
            if (saved.firstSetup) currentOnReturnToSurvey(text) else snackbarHostState.showSnackbar(text)
        }
    }

    val actions = remember(viewModel, permissions) {
        object : GrpcServerActions {
            override fun onBack() = onBack()
            override fun onEdit(change: (GrpcServerForm) -> GrpcServerForm) = viewModel.edit(change)

            // A server on the local network needs Android 17's local network permission to connect.
            override fun onSave() = permissions.ensureLocalNetwork(GRPC_ONLY) { viewModel.save() }
            override fun onSeeOrganizationSettings() = onOpenOrganizationSettings()
            override fun onUseOwnSettings() = viewModel.useOwnSettings()
        }
    }
    GrpcServerContent(form, actions, snackbarHostState)
}

/** The words for what a save did. */
private fun savedText(resources: Resources, result: GrpcSaveResult): String = resources.getString(
    when (result) {
        GrpcSaveResult.SAVED -> R.string.grpc_saved
        GrpcSaveResult.TURNED_ON -> R.string.grpc_saved_turned_on
        GrpcSaveResult.TURN_ON_TO_STREAM -> R.string.grpc_saved_turn_on
        GrpcSaveResult.STARTING -> R.string.grpc_saved_starting
        GrpcSaveResult.RECONNECTING -> R.string.grpc_saved_reconnecting
    }
)

private val GRPC_ONLY = listOf(Destination.GRPC)
