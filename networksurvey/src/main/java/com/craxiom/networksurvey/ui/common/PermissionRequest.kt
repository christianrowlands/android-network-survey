package com.craxiom.networksurvey.ui.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Whether a feature's runtime permissions can be used, as far as the last request showed. */
enum class PermissionAccess {
    /** Granted, or not asked yet. */
    AVAILABLE,

    /** Denied, and Android can still ask again. */
    DENIED,

    /** Denied for good: only the app's system settings can grant it. */
    BLOCKED,
}

/**
 * Asks for a set of runtime permissions when the user acts. [request] goes straight to the granted
 * callback when everything is already allowed; otherwise it asks, and [access] says what to show
 * if the answer was no.
 *
 * [access] lives in memory: after the process is recreated a permission denied for good reads as
 * [PermissionAccess.AVAILABLE] again, so the first request does nothing visible and then flips to
 * [PermissionAccess.BLOCKED].
 */
@Stable
class PermissionRequest internal constructor(private val launch: () -> Unit, private val grantedNow: () -> Boolean, private val onGranted: () -> Unit) {
    var access by mutableStateOf(PermissionAccess.AVAILABLE)
        internal set

    /** Runs the granted callback if everything is allowed, or asks for what is missing. */
    fun request() {
        if (grantedNow()) onGranted() else launch()
    }
}

/**
 * Remembers a [PermissionRequest] for [permissions] that calls [onGranted] once all of them are
 * granted, and [onAnswered] after every answer from the system dialog, granted or not.
 */
@Composable
fun rememberPermissionRequest(
    permissions: Array<String>,
    onAnswered: () -> Unit = {},
    onGranted: () -> Unit,
): PermissionRequest {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    val currentOnAnswered by rememberUpdatedState(onAnswered)
    var request: PermissionRequest? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val target = request ?: return@rememberLauncherForActivityResult
        currentOnAnswered()
        // An interrupted request comes back empty; that is not a grant.
        if (results.isNotEmpty() && results.values.all { it }) {
            target.access = PermissionAccess.AVAILABLE
            currentOnGranted()
        } else if (results.isNotEmpty()) {
            val denied = results.filterValues { !it }.keys
            target.access = if (denied.all { canAskAgain(context, it) }) PermissionAccess.DENIED else PermissionAccess.BLOCKED
        }
    }
    request = remember(launcher) {
        PermissionRequest(
            launch = { launcher.launch(permissions) },
            grantedNow = { permissions.all { granted(context, it) } },
            onGranted = { currentOnGranted() },
        )
    }
    return request
}

/** Opens this app's page in the system settings, where a permission denied for good can be granted. */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun canAskAgain(context: Context, permission: String): Boolean {
    val activity = context.findComponentActivity() ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}
