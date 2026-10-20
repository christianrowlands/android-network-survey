package com.craxiom.networksurvey.ui.logfiles

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import kotlinx.coroutines.launch

/** The Log files page with its view model; files go to the share sheet, the folder to a file manager. */
@Composable
fun LogFilesScreen(
    onBack: () -> Unit,
    onOpenOrganizationSettings: () -> Unit,
    viewModel: LogFilesViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareFailed = stringResource(R.string.log_files_share_failed)
    val noFileManager = stringResource(R.string.log_files_no_file_manager, LogFilesRepository.DISPLAY_PATH)

    // The list changes while a survey writes, so it is read again whenever the page comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val actions = remember(viewModel) {
        object : LogFilesActions {
            override fun onBack() = onBack()
            override fun onSetRollover(megabytes: Int) = viewModel.setRollover(megabytes)
            override fun onSeeOrganizationSettings() = onOpenOrganizationSettings()
            override fun onUseOwnSettings() = viewModel.useOwnSettings()
            override fun onShare(files: List<LogFile>) {
                if (!LogFileSharing.share(context, files)) {
                    scope.launch { snackbarHostState.showSnackbar(shareFailed) }
                }
            }

            override fun onOpenFolder() {
                if (!LogFileSharing.openFolder(context)) {
                    scope.launch { snackbarHostState.showSnackbar(noFileManager) }
                }
            }
        }
    }
    LogFilesContent(state, actions, snackbarHostState)
}
