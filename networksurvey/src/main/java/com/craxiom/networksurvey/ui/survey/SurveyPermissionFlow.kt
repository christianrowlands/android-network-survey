package com.craxiom.networksurvey.ui.survey

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
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
 * Asks for the permissions a record type needs at the moment the user turns its tile on, with the
 * same rationale the old Dashboard switches showed. Once the permission is granted the tile turns
 * on by itself, so the user does not have to tap it a second time. It also asks for local network
 * access before anything connects to an MQTT broker or a gRPC server, and asks about battery
 * optimization right before a survey starts.
 */
class SurveyPermissionFlow internal constructor(
    private val context: Context,
    private val battery: BatteryPrompt = HelperBatteryPrompt(context),
) {

    internal enum class Rationale { BLUETOOTH, CDR_REQUIRED, CDR_OPTIONAL }

    internal var rationale by mutableStateOf<Rationale?>(null)
    internal var pending: (() -> Unit)? = null
    internal var request: (Array<String>) -> Unit = {}

    /** Set when the user chose Ignore on the optional CDR permissions, for the next check only. */
    internal var skipOptionalOnce = false

    /** Set while the local network request is open; its action runs whatever the answer. */
    internal var runWhateverTheAnswer = false

    /** True while the battery optimization question is open. */
    internal var batteryQuestion by mutableStateOf(false)

    /** The start waiting for the battery question; its own slot, so a permission request cannot replace it. */
    private var batteryPending: ((afterStart: () -> Unit) -> Unit)? = null

    /** Runs [action] once [type] has the permissions it needs, asking for them first if not. */
    fun ensure(type: RecordType, action: () -> Unit) {
        val needed = when {
            type == RecordType.BLUETOOTH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                missing(AppPermissions.BLUETOOTH_PERMISSIONS) -> Rationale.BLUETOOTH

            type == RecordType.CDR && missing(CdrPermissions.CDR_REQUIRED_PERMISSIONS) -> Rationale.CDR_REQUIRED
            type == RecordType.CDR && !skipOptionalOnce && missing(CdrPermissions.CDR_OPTIONAL_PERMISSIONS) ->
                Rationale.CDR_OPTIONAL

            else -> null
        }
        skipOptionalOnce = false
        if (needed == null) {
            action()
        } else {
            pending = { ensure(type, action) }
            rationale = needed
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
        pending = action
        runWhateverTheAnswer = true
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
        batteryPending = start
        batteryQuestion = true
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
        val next = batteryPending
        batteryPending = null
        batteryQuestion = false
        return next
    }

    /**
     * Runs the pending action after the system permission dialog closes. Optional CDR permissions
     * never block CDR and local network access never blocks a connection; required permissions
     * must all be granted.
     */
    internal fun onRequestResult(allGranted: Boolean, optionalRequest: Boolean) {
        val next = pending
        pending = null
        val runAnyway = runWhateverTheAnswer
        runWhateverTheAnswer = false
        when {
            runAnyway -> next?.invoke()
            optionalRequest -> next?.let { runIgnoringOptional(this, it) }
            allGranted -> next?.invoke()
        }
    }

    internal fun permissionsFor(rationale: Rationale): Array<String> = when (rationale) {
        Rationale.BLUETOOTH -> AppPermissions.BLUETOOTH_PERMISSIONS
        Rationale.CDR_REQUIRED -> CdrPermissions.CDR_REQUIRED_PERMISSIONS
        Rationale.CDR_OPTIONAL -> CdrPermissions.CDR_OPTIONAL_PERMISSIONS
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
 *
 * @param holdBatteryQuestion True while another dialog (the background location explanation) must
 * not be covered; the question waits and shows after it.
 */
@Composable
fun rememberSurveyPermissionFlow(holdBatteryQuestion: Boolean = false): SurveyPermissionFlow {
    val context = LocalContext.current
    val flow = remember { SurveyPermissionFlow(context) }
    val batteryHelper = remember { BatteryOptimizationHelper(context) }
    var optionalRequest by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        flow.onRequestResult(allGranted = results.values.all { it }, optionalRequest = optionalRequest)
        optionalRequest = false
    }
    flow.request = { launcher.launch(it) }

    if (flow.batteryQuestion && !holdBatteryQuestion) {
        BatteryOptimizationDialog(
            onLater = flow::onBatteryLater,
            onGoToSettings = flow::onBatteryGoToSettings,
            onDismiss = flow::onBatteryDismissed,
            batteryOptimizationHelper = batteryHelper,
        )
    }

    flow.rationale?.let { rationale ->
        val dismiss = { flow.rationale = null }
        when (rationale) {
            SurveyPermissionFlow.Rationale.BLUETOOTH -> NsConfirmationDialog(
                title = stringResource(R.string.bluetooth_permissions_rationale_title),
                message = stringResource(R.string.bluetooth_permissions_rationale),
                confirmText = stringResource(android.R.string.ok),
                onConfirm = { flow.request(flow.permissionsFor(rationale)) },
                onDismiss = dismiss,
                dismissText = null,
            )

            SurveyPermissionFlow.Rationale.CDR_REQUIRED -> NsConfirmationDialog(
                title = stringResource(R.string.cdr_required_permissions_rationale_title),
                message = stringResource(R.string.cdr_required_permissions_rationale),
                confirmText = stringResource(R.string.request),
                onConfirm = { flow.request(flow.permissionsFor(rationale)) },
                onDismiss = dismiss,
                dismissText = null,
            )

            SurveyPermissionFlow.Rationale.CDR_OPTIONAL -> NsConfirmationDialog(
                title = stringResource(R.string.cdr_optional_permissions_rationale_title),
                message = stringResource(R.string.cdr_optional_permissions_rationale),
                confirmText = stringResource(R.string.request),
                onConfirm = {
                    optionalRequest = true
                    flow.request(flow.permissionsFor(rationale))
                },
                onDismiss = dismiss,
                dismissText = stringResource(R.string.ignore),
                onCancel = { flow.pending?.let { runIgnoringOptional(flow, it) } },
            )
        }
    }
    return flow
}

/** Runs the pending action once, without asking for the optional CDR permissions again. */
private fun runIgnoringOptional(flow: SurveyPermissionFlow, action: () -> Unit) {
    flow.pending = null
    flow.skipOptionalOnce = true
    action()
}
