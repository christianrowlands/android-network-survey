package com.craxiom.networksurvey.ui.main.more

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.NsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The More tab: routes each row to its page, opens links in the browser, and copies the version.
 *
 * @param onOpen Opens the page at a route of the outer graph; Back returns to More.
 */
@Composable
fun MoreScreen(onOpen: (String) -> Unit, viewModel: MoreViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val actions = remember(context, resources, onOpen) {
        object : MoreActions {
            override fun onOpen(route: String) = onOpen(route)

            override fun onOpenExternal(url: String) {
                if (!ExternalLinks.open(context, url)) showMessage(scope, snackbarHostState, resources.getString(R.string.more_link_failed))
            }

            override fun onCopyVersion() {
                val text = "${resources.getString(R.string.app_name)} ${NsUtils.getAppVersionName(context)}"
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.more_version), text))
                // Android 13 and later confirm a copy themselves.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    showMessage(scope, snackbarHostState, resources.getString(R.string.more_version_copied))
                }
            }
        }
    }
    MoreContent(state, actions, snackbarHostState)
}

private fun showMessage(scope: CoroutineScope, snackbarHostState: SnackbarHostState, message: String) {
    scope.launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message)
    }
}

