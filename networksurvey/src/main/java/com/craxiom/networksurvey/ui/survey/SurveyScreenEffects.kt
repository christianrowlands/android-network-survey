package com.craxiom.networksurvey.ui.survey

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.main.SharedViewModel
import kotlinx.coroutines.flow.filterNotNull

/**
 * The Survey screen's side effects: the lifecycle-bound service binding, re-checking settings on
 * resume, and the snackbar messages. Keeping the screen on while recording is the activity's job,
 * so it holds on every tab.
 */
@Composable
fun SurveyScreenEffects(
    viewModel: SurveyViewModel,
    sharedViewModel: SharedViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current

    ServiceConnectionHandler { service -> viewModel.onServiceChanged(service?.let(::NetworkSurveyServiceGateway)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            SurveyEventMessages.message(resources, event)?.let {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            }
        }
    }

    // A message another page left for this screen ("Connected to ...", "gRPC server saved ...").
    // Collected rather than keyed on the value: consuming it sets null, and an effect keyed on the
    // message would restart on that and cancel the snackbar it just showed.
    LaunchedEffect(sharedViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            sharedViewModel.surveyMessage.filterNotNull().collect { message ->
                sharedViewModel.consumeSurveyMessage()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }
}
