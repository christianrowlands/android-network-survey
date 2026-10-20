package com.craxiom.networksurvey.ui.survey

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.AppPermissions
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.CdrPermissions
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.main.BatteryOptimizationDialog
import com.craxiom.networksurvey.util.BatteryOptimizationHelper
import com.craxiom.networksurvey.util.LocalNetworkPermissionUtil

/**
 * Everything [SurveyPermissionFlow] waits on, kept in a view model so it survives a rotation: the
 * rationale or battery question on screen, the action a system permission dialog will run, and the
 * start the battery question holds. It holds no Activity (the flow checks permissions with the
 * application context), so the pending actions only reach view models and this state. Process
 * death still drops them, which is fine: the system dialog is gone then too.
 */
class SurveyPermissionState : ViewModel() {
    internal var rationale by mutableStateOf<SurveyPermissionFlow.Rationale?>(null)
    internal var pending: (() -> Unit)? = null

    /** Set when the user chose Ignore on the optional CDR permissions, for the next check only. */
    internal var skipOptionalOnce = false

    /** Set while the local network request is open; its action runs whatever the answer. */
    internal var runWhateverTheAnswer = false

    /** Set while the optional CDR permissions are asked for; they never block CDR. */
    internal var optionalRequest = false

    /** What runs when the user denies the requested permissions, for a request that goes on regardless. */
    internal var onDenied: (() -> Unit)? = null

    /** True while the battery optimization question is open. */
    internal var batteryQuestion by mutableStateOf(false)

    /** The start waiting for the battery question; its own slot, so a permission request cannot replace it. */
    internal var batteryPending: ((afterStart: () -> Unit) -> Unit)? = null

    /** The permissions to ask for next; the screen opens the system dialog for them and clears it. */
    internal var permissionRequest by mutableStateOf<Array<String>?>(null)
}

/**
 * Asks for the permissions a record type needs at the moment the user turns its tile on, with the
 * same rationale the old Dashboard switches showed. Once the permission is granted the tile turns
 * on by itself, so the user does not have to tap it a second time. It also asks for local network
 * access before anything connects to an MQTT broker or a gRPC server, and asks about battery
 * optimization right before a survey starts.
 *
 * What it waits on lives in [state], so a rotation while a dialog is open keeps the action the user
 * asked for. The flow keeps only the application context, so an action that captured a flow from
 * before the rotation still works and pins no Activity.
 */
class SurveyPermissionFlow internal constructor(
    context: Context,
    internal val state: SurveyPermissionState = SurveyPermissionState(),
    private val battery: BatteryPrompt = HelperBatteryPrompt(context.applicationContext),
) {
    private val context: Context = context.applicationContext

    internal enum class Rationale { BLUETOOTH, CDR_REQUIRED, CDR_OPTIONAL }

    internal val rationale: Rationale? get() = state.rationale

    /** True while the battery optimization question is open. */
    internal val batteryQuestion: Boolean get() = state.batteryQuestion

    /** True while a rationale, a system permission dialog or the battery question waits for an answer. */
    val isWaitingForAnswer: Boolean
        get() = state.rationale != null || state.permissionRequest != null || state.runWhateverTheAnswer || state.batteryQuestion

    /**
     * Runs [action] once [type] has the permissions it needs, asking for them first if not.
     *
     * @param includeOptional False to check only the permissions [type] can't record without (at a
     *                        survey start), so the optional CDR question isn't asked every time.
     * @param runIfDenied     True to run [action] even when the user denies the permissions (a
     *                        survey start, which then leaves [type] out and says so).
     */
    fun ensure(type: RecordType, includeOptional: Boolean = true, runIfDenied: Boolean = false, action: () -> Unit) {
        val needed = when {
            type == RecordType.BLUETOOTH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                missing(AppPermissions.BLUETOOTH_PERMISSIONS) -> Rationale.BLUETOOTH

            type == RecordType.CDR && missing(CdrPermissions.CDR_REQUIRED_PERMISSIONS) -> Rationale.CDR_REQUIRED
            type == RecordType.CDR && includeOptional && !state.skipOptionalOnce &&
                missing(CdrPermissions.CDR_OPTIONAL_PERMISSIONS) -> Rationale.CDR_OPTIONAL

            else -> null
        }
        state.skipOptionalOnce = false
        if (needed == null) {
            action()
        } else {
            state.pending = { ensure(type, includeOptional, runIfDenied, action) }
            state.onDenied = action.takeIf { runIfDenied }
            state.rationale = needed
        }
    }

    /**
     * Runs [action] after asking for local network access when [destinations] includes a stream to
     * a server (MQTT or gRPC) and Android 17 or later requires it. Granted or denied, the action
     * runs: a server on the internet works without it, and only one on the local network does not.
     */
    fun ensureLocalNetwork(destinations: Collection<Destination>, action: () -> Unit) {
        if (destinations.none { it in SERVER_DESTINATIONS } || !LocalNetworkPermissionUtil.isRequired(context)) {
            action()
            return
        }
        state.pending = action
        state.runWhateverTheAnswer = true
        request(arrayOf(LocalNetworkPermissionUtil.ACCESS_LOCAL_NETWORK))
    }

    /**
     * Runs [start] (a survey start) once the battery optimization question is answered, when one is
     * due. Later and Go to Settings both run it; Go to Settings opens the battery settings through
     * the continuation [start] is handed, which it calls once the survey has started, so the app is
     * not sent to the background first. Back or a tap outside closes the question and runs nothing.
     */
    fun ensureBatteryChoice(start: (afterStart: () -> Unit) -> Unit) {
        if (!battery.isDue()) {
            start {}
            return
        }
        state.batteryPending = start
        state.batteryQuestion = true
    }

    /** Later: runs the pending start. */
    internal fun onBatteryLater() {
        closeBatteryQuestion()?.invoke {}
    }

    /** Go to Settings: runs the pending start, then opens the battery settings once it is done. */
    internal fun onBatteryGoToSettings() {
        closeBatteryQuestion()?.invoke { battery.openSettings() }
    }

    /** Back or outside: nothing starts. */
    internal fun onBatteryDismissed() {
        closeBatteryQuestion()
    }

    private fun closeBatteryQuestion(): ((() -> Unit) -> Unit)? {
        val next = state.batteryPending
        state.batteryPending = null
        state.batteryQuestion = false
        return next
    }

    /** OK or Request on a rationale: asks the system for its permissions. */
    internal fun onRationaleConfirmed(rationale: Rationale) {
        state.optionalRequest = rationale == Rationale.CDR_OPTIONAL
        request(permissionsFor(rationale))
    }

    /** The rationale closed (after either button, Back or a tap outside). */
    internal fun onRationaleDismissed() {
        state.rationale = null
    }

    /** Ignore on the optional CDR permissions: CDR turns on without them. */
    internal fun onIgnoreOptional() {
        state.pending?.let(::runIgnoringOptional)
    }

    /**
     * Runs the pending action after the system permission dialog closes. Optional CDR permissions
     * never block CDR and local network access never blocks a connection; required permissions
     * must all be granted.
     */
    internal fun onRequestResult(allGranted: Boolean) {
        val next = state.pending
        state.pending = null
        val runAnyway = state.runWhateverTheAnswer
        state.runWhateverTheAnswer = false
        val optional = state.optionalRequest
        state.optionalRequest = false
        val denied = state.onDenied
        state.onDenied = null
        when {
            runAnyway -> next?.invoke()
            optional -> next?.let(::runIgnoringOptional)
            allGranted -> next?.invoke()
            else -> denied?.invoke()
        }
    }

    internal fun permissionsFor(rationale: Rationale): Array<String> = when (rationale) {
        Rationale.BLUETOOTH -> AppPermissions.BLUETOOTH_PERMISSIONS
        Rationale.CDR_REQUIRED -> CdrPermissions.CDR_REQUIRED_PERMISSIONS
        Rationale.CDR_OPTIONAL -> CdrPermissions.CDR_OPTIONAL_PERMISSIONS
    }

    /** Hands [permissions] to the screen, which opens the system dialog (it owns the launcher). */
    private fun request(permissions: Array<String>) {
        state.permissionRequest = permissions
    }

    /** Runs the pending action once, without asking for the optional CDR permissions again. */
    private fun runIgnoringOptional(action: () -> Unit) {
        state.pending = null
        state.skipOptionalOnce = true
        action()
    }

    private fun missing(permissions: Array<String>): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        /** The destinations that connect to a server the user may run on the local network. */
        val SERVER_DESTINATIONS = setOf(Destination.MQTT, Destination.GRPC)
    }
}

/**
 * What the battery optimization question needs from Android: whether it is due now, and the
 * settings that answer it.
 */
interface BatteryPrompt {
    fun isDue(): Boolean
    fun openSettings()
}

/** The question as [BatteryOptimizationHelper] decides it: not opted out, still optimized, not asked in the last day. */
private class HelperBatteryPrompt(context: Context) : BatteryPrompt {
    private val helper = BatteryOptimizationHelper(context)
    override fun isDue() = helper.shouldPromptForBatteryOptimization()
    override fun openSettings() = helper.openBatteryOptimizationSettings()
}

/**
 * Creates the permission flow and draws its rationale dialogs and the battery optimization question.
 * Its state is a [SurveyPermissionState] view model of the screen's own scope (the one the screen's
 * view models use), so it survives a rotation.
 *
 * @param holdBatteryQuestion True while another dialog (the background location explanation) must
 * not be covered; the question waits and shows after it.
 * @param battery Decides whether the battery question is due; tests pass their own.
 */
@Composable
fun rememberSurveyPermissionFlow(holdBatteryQuestion: Boolean = false, battery: BatteryPrompt? = null): SurveyPermissionFlow {
    val context = LocalContext.current
    val state: SurveyPermissionState = viewModel()
    val flow = remember(state) {
        SurveyPermissionFlow(context, state, battery ?: HelperBatteryPrompt(context.applicationContext))
    }
    val batteryHelper = remember { BatteryOptimizationHelper(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        flow.onRequestResult(allGranted = results.values.all { it })
    }
    val request = state.permissionRequest
    LaunchedEffect(request) {
        if (request != null) {
            state.permissionRequest = null
            launcher.launch(request)
        }
    }

    if (flow.batteryQuestion && !holdBatteryQuestion) {
        BatteryOptimizationDialog(
            onLater = flow::onBatteryLater,
            onGoToSettings = flow::onBatteryGoToSettings,
            onDismiss = flow::onBatteryDismissed,
            batteryOptimizationHelper = batteryHelper,
        )
    }

    flow.rationale?.let { rationale ->
        val confirm = { flow.onRationaleConfirmed(rationale) }
        when (rationale) {
            SurveyPermissionFlow.Rationale.BLUETOOTH -> NsConfirmationDialog(
                title = stringResource(R.string.bluetooth_permissions_rationale_title),
                message = stringResource(R.string.bluetooth_permissions_rationale),
                confirmText = stringResource(android.R.string.ok),
                onConfirm = confirm,
                onDismiss = flow::onRationaleDismissed,
                dismissText = null,
            )

            SurveyPermissionFlow.Rationale.CDR_REQUIRED -> NsConfirmationDialog(
                title = stringResource(R.string.cdr_required_permissions_rationale_title),
                message = stringResource(R.string.cdr_required_permissions_rationale),
                confirmText = stringResource(R.string.request),
                onConfirm = confirm,
                onDismiss = flow::onRationaleDismissed,
                dismissText = null,
            )

            SurveyPermissionFlow.Rationale.CDR_OPTIONAL -> NsConfirmationDialog(
                title = stringResource(R.string.cdr_optional_permissions_rationale_title),
                message = stringResource(R.string.cdr_optional_permissions_rationale),
                confirmText = stringResource(R.string.request),
                onConfirm = confirm,
                onDismiss = flow::onRationaleDismissed,
                dismissText = stringResource(R.string.ignore),
                onCancel = flow::onIgnoreOptional,
            )
        }
    }
    return flow
}
