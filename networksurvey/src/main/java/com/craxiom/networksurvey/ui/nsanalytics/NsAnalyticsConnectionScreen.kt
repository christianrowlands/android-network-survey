package com.craxiom.networksurvey.ui.nsanalytics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.ui.common.CameraPermissionNotice
import com.craxiom.networksurvey.ui.common.PermissionRequest
import com.craxiom.networksurvey.ui.common.NsCtaBar
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.rememberCameraPermissionRequest
import com.craxiom.networksurvey.ui.main.more.ExternalLinks
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlinx.coroutines.launch

/**
 * The NS Analytics page: the connect walkthrough while this phone is not connected, the connection
 * and its uploads once it is. A code scanned or opened from a link is confirmed here; once the
 * phone connects, [onConnected] returns to the Survey screen.
 */
@Composable
fun NsAnalyticsConnectionScreen(
    viewModel: NsAnalyticsConnectionViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    onConnected: (workspaceName: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val camera = rememberCameraPermissionRequest(onNavigateToQrScanner)
    // Every web address on this page opens through here, and says so when no app can open it.
    val openUrl: (String) -> Unit = remember(context, resources, scope, snackbarHostState) {
        { url ->
            if (!ExternalLinks.open(context, url)) {
                scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.more_link_failed)) }
            }
        }
    }
    val currentOnConnected by rememberUpdatedState(onConnected)
    var dialog by rememberSaveable { mutableStateOf(NsAnalyticsDialog.NONE) }

    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        // A code scanned while this page was in the background is confirmed when it comes back.
        viewModel.checkAndProcessQrData()
        onStopOrDispose { viewModel.onStop() }
    }
    ServiceConnectionHandler { viewModel.setNetworkSurveyService(it) }
    LaunchedEffect(viewModel) { viewModel.connectedWorkspace.collect { currentOnConnected(it) } }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    NsPageScaffold(
        title = stringResource(if (state.isRegistered) R.string.survey_dest_ns_analytics else R.string.ns_analytics_connect_title),
        onBack = onNavigateUp,
        snackbarHostState = snackbarHostState,
        bottomBar = { if (!state.isRegistered && !state.isLoading) ScanBar(state.registering, camera) },
    ) {
        NsAnalyticsHeader()
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().padding(NsTheme.spacing.xxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.isRegistered -> NsAnalyticsConnectedContent(state, connectedActions(viewModel, openUrl) { dialog = it })
            else -> NsAnalyticsConnectWalkthrough(state.uploadFrequencyMinutes, openUrl)
        }
    }

    NsAnalyticsDialogs(state, dialog, dialogActions(viewModel, camera, openUrl) { dialog = NsAnalyticsDialog.NONE })
}

/**
 * The pinned Scan button. A refused camera is explained right above it, so tapping Scan again never
 * looks like it did nothing, however far down the page that notice would otherwise sit.
 */
@Composable
private fun ScanBar(registering: Boolean, camera: PermissionRequest) {
    NsCtaBar {
        CameraPermissionNotice(camera)
        if (registering) {
            Text(
                text = stringResource(R.string.ns_analytics_registering),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NsPrimaryCta(
            text = stringResource(R.string.ns_analytics_scan_cta),
            onClick = camera::request,
            icon = R.drawable.ic_qr_code_scanner,
            enabled = !registering,
        )
    }
}

private fun connectedActions(
    viewModel: NsAnalyticsConnectionViewModel,
    openUrl: (String) -> Unit,
    showDialog: (NsAnalyticsDialog) -> Unit,
) = object : NsAnalyticsConnectedActions {
    override fun onToggleAutoUpload(enabled: Boolean) = viewModel.toggleAutoUpload(enabled)
    override fun onUploadNow() = viewModel.uploadNow()
    override fun onOpenUrl(url: String) = openUrl(url)
    override fun onClearQueue() = showDialog(NsAnalyticsDialog.CLEAR_QUEUE)

    override fun onOpenDashboard() {
        val workspace = viewModel.uiState.value.workspace
        val url = if (workspace.isNullOrBlank()) {
            NsAnalyticsConstants.NS_ANALYTICS_WEB_URL
        } else {
            NsAnalyticsConstants.NS_ANALYTICS_WEB_URL.toUri().buildUpon()
                .appendQueryParameter(NsAnalyticsConstants.NS_ANALYTICS_WEB_WORKSPACE_PARAM, workspace)
                .build().toString()
        }
        openUrl(url)
    }

    override fun onDisconnect() {
        // Disconnecting waits for the survey to stop; the view model says so.
        if (viewModel.uiState.value.isSurveyActive) viewModel.unregisterDevice() else showDialog(NsAnalyticsDialog.DISCONNECT)
    }
}

private fun dialogActions(
    viewModel: NsAnalyticsConnectionViewModel,
    camera: PermissionRequest,
    openUrl: (String) -> Unit,
    closeDialog: () -> Unit,
) = object : NsAnalyticsDialogActions {
    override fun onConfirmRegistration() = viewModel.confirmRegistration()
    override fun onCancelRegistration() = viewModel.cancelRegistration()
    override fun onRetryRegistration() = viewModel.retryRegistration()
    override fun onDismissRegistrationError() = viewModel.dismissRegistrationError()
    override fun onDismissAlreadyRegistered() = viewModel.dismissAlreadyRegisteredDialog()
    override fun onDismissDeregistered() = viewModel.clearDeregistrationInfo()
    override fun onDismissQuota() = viewModel.dismissQuotaDialog()
    override fun onOpenUrl(url: String) = openUrl(url)
    override fun onDismissDialog() = closeDialog()

    override fun onScanAgain() {
        viewModel.clearDeregistrationInfo()
        camera.request()
    }

    override fun onConfirmDisconnect() {
        closeDialog()
        viewModel.unregisterDevice()
    }

    override fun onConfirmClearQueue() {
        closeDialog()
        viewModel.clearQueue()
    }
}
