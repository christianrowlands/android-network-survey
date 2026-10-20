package com.craxiom.networksurvey.ui.survey

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.common.openAppSettings
import com.craxiom.networksurvey.ui.common.openLocationSettings
import com.craxiom.networksurvey.ui.common.openWifiSettings
import com.craxiom.networksurvey.ui.community.CommunityUploadLauncher
import com.craxiom.networksurvey.ui.community.CommunityUploadRepository
import com.craxiom.networksurvey.ui.community.UploadConfirmationDialog
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi
import com.craxiom.networksurvey.ui.survey.model.SurveyEvent
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import timber.log.Timber

/**
 * The Survey tab: one survey, one Start and one Stop. Ready shows what it will record and where
 * it goes; Recording shows proof that it is working.
 */
@Composable
fun SurveyScreen(
    sharedViewModel: SharedViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SurveyViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Never on top of the background location explanation, as before
    val backgroundLocationShowing by sharedViewModel.backgroundLocationRationaleVisible.collectAsStateWithLifecycle()
    val permissions = rememberSurveyPermissionFlow(holdBatteryQuestion = backgroundLocationShowing)
    val currentState = rememberUpdatedState(state)
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.none { it } && !canAskForLocation(context)) openAppSettings(context)
        viewModel.onResume()
    }

    SurveyScreenEffects(viewModel, sharedViewModel, snackbarHostState)

    var confirmCommunityUpload by rememberSaveable { mutableStateOf(false) }
    val currentOpenSettings = rememberUpdatedState(onOpenSettings)
    val actions = remember(viewModel, sharedViewModel, permissions) {
        ScreenActions(
            context, viewModel, sharedViewModel, permissions, currentState, { currentOpenSettings.value() },
            confirmCommunityUpload = { confirmCommunityUpload = true },
        ) {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    SurveyScreenContent(state, actions, viewModel::preview, snackbarHostState, modifier)

    // The same dialog the Community databases page shows before Upload now
    if (confirmCommunityUpload) {
        UploadConfirmationDialog(
            onUpload = viewModel::uploadCommunity,
            onNavigateToUploadSettings = sharedViewModel::triggerNavigationToUploadSettings,
            onDismiss = { confirmCommunityUpload = false },
        )
    }
}

/** Wires the screen's actions to the view model, navigation and system settings. */
private class ScreenActions(
    private val context: Context,
    private val viewModel: SurveyViewModel,
    private val sharedViewModel: SharedViewModel,
    private val permissions: SurveyPermissionFlow,
    private val state: State<SurveyUiState>,
    private val openSettings: () -> Unit,
    private val confirmCommunityUpload: () -> Unit,
    private val requestLocation: () -> Unit,
) : SurveyActions {

    override fun onOpenSettings() = openSettings()

    /** Local network access first (a system dialog), then the battery question, then the start. */
    override fun onStart() {
        permissions.ensureLocalNetwork(destinationsOn()) {
            permissions.ensureBatteryChoice { afterStart -> viewModel.start(afterStart) }
        }
    }

    override fun onStop() = viewModel.stop()

    override fun onToggleRecordType(type: RecordType) {
        if (type in state.value.userSetup.recordTypes) {
            viewModel.toggleRecordType(type)
        } else {
            permissions.ensure(type) { viewModel.toggleRecordType(type) }
        }
    }

    override fun onToggleDestination(destination: Destination) {
        if (destination in destinationsOn()) {
            viewModel.toggleDestination(destination)
        } else {
            permissions.ensureLocalNetwork(listOf(destination)) { viewModel.toggleDestination(destination) }
        }
    }

    override fun onOpenDestination(destination: Destination) = when (destination) {
        Destination.LOG_FILES -> sharedViewModel.triggerNavigationToLogFiles()
        Destination.NS_ANALYTICS -> sharedViewModel.triggerNavigationToNsAnalyticsConnection()
        Destination.MQTT -> sharedViewModel.triggerNavigationToMqttConnection()
        Destination.GRPC -> sharedViewModel.triggerNavigationToGrpcConnection()
        Destination.COMMUNITY -> sharedViewModel.triggerNavigationToCommunityDatabases()
    }

    override fun onExplainPhoneState() = viewModel.emit(SurveyEvent.PhoneStateFollowsCellular)

    override fun onUseLogFiles() = viewModel.useLogFiles()

    override fun onConnectNsAnalytics() = sharedViewModel.triggerNavigationToNsAnalyticsConnection()

    override fun onTurnOnLocation() = openLocationSettings(context)

    override fun onAllowLocation() = requestLocation()

    override fun onUseOwnSettings() = viewModel.setOverride(true)

    override fun onRestoreOrganizationSettings() = viewModel.setOverride(false)

    override fun onCopyMissionId(missionId: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.mission_id_card_title), missionId))
        // Android 13 and later confirm a copy themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) viewModel.emit(SurveyEvent.MissionIdCopied)
    }

    override fun onApplyDraft(types: Set<RecordType>, destinations: Set<Destination>) =
        withPermissionsFor(types, destinations) { viewModel.applyDraft(types, destinations) }

    override fun onPickSetup(id: String) {
        val setup = state.value.savedSetups?.setups?.firstOrNull { it.id == id } ?: return
        withPermissionsFor(setup.recordTypes, setup.destinations) { viewModel.pickSetup(id) }
    }

    override fun onSaveSetup(name: String) = viewModel.saveSetup(name)

    override fun onRenameSetup(id: String, name: String) = viewModel.renameSetup(id, name)

    override fun onStarSetup(id: String, starred: Boolean) = viewModel.starSetup(id, starred)

    override fun onDeleteSetup(id: String) = viewModel.deleteSetup(id)
    /** The community databases ask first, unless the user chose not to see the upload dialog again. */
    override fun onUploadNow(destination: Destination) {
        if (destination == Destination.COMMUNITY && CommunityUploadLauncher.shouldConfirm(context)) {
            confirmCommunityUpload()
        } else {
            viewModel.uploadNow(destination)
        }
    }

    /**
     * Runs [action] once the permissions that switching to [types] and [destinations] newly needs
     * are granted: Bluetooth or call records, and local network access for a new stream.
     */
    private fun withPermissionsFor(types: Set<RecordType>, destinations: Set<Destination>, action: () -> Unit) {
        val added = types - state.value.userSetup.recordTypes
        val needsPermission = added.firstOrNull { it == RecordType.BLUETOOTH || it == RecordType.CDR }
        val apply = { permissions.ensureLocalNetwork(destinations - destinationsOn()) { action() } }
        if (needsPermission == null) {
            apply()
        } else {
            permissions.ensure(needsPermission, apply)
        }
    }

    override fun onSetDestinationShown(destination: Destination, shown: Boolean) =
        viewModel.setDestinationShown(destination, shown)

    override fun onCancelCommunityUpload() = CommunityUploadRepository.get(context).cancel()

    override fun onOpenOrganizationSettings() = sharedViewModel.triggerNavigationToOrganizationSettings()

    override fun onChangeBatteryThreshold() = sharedViewModel.triggerNavigationToSettings()

    override fun onAdjustQueueLimit() = sharedViewModel.triggerNavigationToSettings()

    override fun onDisableQueueLimit() = viewModel.disableQueueLimit()

    override fun onFixItem(item: AttentionItemUi) {
        when (item) {
            is AttentionItemUi.Type -> fixRecordType(item)
            is AttentionItemUi.Delivery -> onOpenDestination(item.destination)
            is AttentionItemUi.Dest -> when (item.reason) {
                SkipReason.NOT_CONFIGURED, SkipReason.NOT_REGISTERED -> onOpenDestination(item.destination)
                SkipReason.NO_UPLOAD_TARGET -> sharedViewModel.triggerNavigationToUploadSettings()
                else -> permissions.ensureLocalNetwork(listOf(item.destination)) { viewModel.retrySkipped() }
            }
        }
    }

    private fun fixRecordType(item: AttentionItemUi.Type) {
        when {
            // Wi-Fi never left the survey, so turning it on is all it takes
            item.reason == SkipReason.RADIO_OFF && item.type == RecordType.WIFI -> openWifiSettings(context)

            item.reason == SkipReason.RADIO_OFF && item.type == RecordType.BLUETOOTH -> {
                viewModel.retryAfterFix()
                open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }

            item.reason == SkipReason.PERMISSION_MISSING -> permissions.ensure(item.type) { viewModel.retrySkipped() }
            else -> viewModel.retrySkipped()
        }
    }

    /** The destinations the survey sends to (or will), as the rows show them. */
    private fun destinationsOn(): Set<Destination> = state.value.destinations.filter { it.on }.map { it.destination }.toSet()

    private fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No settings screen for %s", intent.action)
        }
    }
}

/** False once the user has denied location for good, when only app settings can grant it. */
private fun canAskForLocation(context: Context): Boolean {
    val activity = context as? Activity ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
}

