package com.craxiom.networksurvey.ui.community

import androidx.annotation.PluralsRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.ui.common.NsManagedLockSheet
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSummaryText
import com.craxiom.networksurvey.ui.common.NsSwitchRow
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.survey.sheets.UseOwnSettingsDialog
import com.craxiom.networksurvey.ui.theme.NsTheme
import java.text.NumberFormat

/** Everything the Community databases page can ask for. */
interface CommunityDatabasesActions {
    fun onBack() {}
    fun onSetOpenCelliD(on: Boolean) {}
    fun onSetBeaconDb(on: Boolean) {}
    fun onSetAutoUpload(on: Boolean) {}
    fun onUploadNow() {}
    fun onCancelUpload() {}
    fun onSeeOnMap() {}
    fun onOpenUploadSettings() {}
    fun onSeeOrganizationSettings() {}
    fun onUseOwnSettings() {}
}

/** The Community databases page with its view model and upload dialogs. */
@Composable
fun CommunityDatabasesScreen(
    onBack: () -> Unit,
    onOpenUploadSettings: () -> Unit,
    onSeeOnMap: () -> Unit,
    onOpenOrganizationSettings: () -> Unit,
    viewModel: CommunityUploadViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Seen here (only while the page is on screen), so the Survey screen stops saying it failed
    val uploadFailure by viewModel.uploadFailure.collectAsStateWithLifecycle()
    LaunchedEffect(uploadFailure) {
        if (uploadFailure != null) viewModel.acknowledgeUploadFailure()
    }
    var showUploadDialog by rememberSaveable { mutableStateOf(false) }
    var showNoInternet by rememberSaveable { mutableStateOf(false) }

    val actions = remember(viewModel) {
        object : CommunityDatabasesActions {
            override fun onBack() = onBack()
            override fun onSetOpenCelliD(on: Boolean) = viewModel.setOpenCelliD(on)
            override fun onSetBeaconDb(on: Boolean) = viewModel.setBeaconDb(on)
            override fun onSetAutoUpload(on: Boolean) = viewModel.setAutoUpload(on)
            override fun onCancelUpload() = viewModel.cancelUpload()
            override fun onSeeOnMap() = onSeeOnMap()
            override fun onOpenUploadSettings() = onOpenUploadSettings()
            override fun onSeeOrganizationSettings() = onOpenOrganizationSettings()
            override fun onUseOwnSettings() = viewModel.useOwnSettings()
            override fun onUploadNow() {
                if (viewModel.shouldShowUploadConfigDialog()) {
                    showUploadDialog = true
                } else if (!viewModel.startUploadWithSavedSettings()) {
                    showNoInternet = true
                }
            }
        }
    }
    CommunityDatabasesContent(state, actions)

    if (showUploadDialog) {
        UploadConfirmationDialog(
            onUpload = { ocid, anonymously, beaconDb, retry, dontShowAgain ->
                if (dontShowAgain) viewModel.setDontShowUploadDialog(true)
                if (!viewModel.startUpload(ocid, anonymously, beaconDb, retry)) showNoInternet = true
            },
            onNavigateToUploadSettings = onOpenUploadSettings,
            onDismiss = { showUploadDialog = false },
        )
    }
    if (showNoInternet) NoInternetDialog(onDismiss = { showNoInternet = false })
}

/**
 * The Community databases page, stateless: which public databases receive the towers and Wi-Fi
 * networks this phone sees, whether uploads run on their own, and Upload now with the running or
 * last upload. Whether the survey collects records for them is set on the Survey screen.
 */
@Composable
fun CommunityDatabasesContent(state: UploadUiState, actions: CommunityDatabasesActions, modifier: Modifier = Modifier) {
    var showLockSheet by rememberSaveable { mutableStateOf(false) }
    var confirmOwnSettings by rememberSaveable { mutableStateOf(false) }

    NsPageScaffold(title = stringResource(R.string.survey_dest_community), onBack = actions::onBack, modifier = modifier) {
        NsSummaryText(stringResource(if (state.visible) R.string.community_intro else R.string.community_not_allowed))
        if (!state.visible) return@NsPageScaffold

        NsRowGroup {
            val keyMissing = state.openCelliDOn && state.openCelliDKey == OpenCelliDKey.MISSING
            NsSwitchRow(
                title = stringResource(R.string.survey_dest_community_opencellid),
                subtitle = stringResource(state.openCelliDKey.detailRes),
                subtitleColor = if (keyMissing) NsTheme.colors.status.warningText else MaterialTheme.colorScheme.onSurfaceVariant,
                checked = state.openCelliDOn,
                onCheckedChange = actions::onSetOpenCelliD,
                icon = R.drawable.ic_public,
            )
            NsRowDivider()
            NsSwitchRow(
                title = stringResource(R.string.survey_dest_community_beacondb),
                subtitle = stringResource(R.string.community_beacondb_detail),
                checked = state.beaconDbOn,
                onCheckedChange = actions::onSetBeaconDb,
                icon = R.drawable.ic_public,
            )
        }

        SectionHeader(stringResource(R.string.community_uploads))
        NsRowGroup {
            NsSwitchRow(
                title = stringResource(R.string.community_auto_upload),
                subtitle = pluralStringResource(
                    if (state.autoUploadWifiOnly) R.plurals.community_auto_upload_detail_wifi else R.plurals.community_auto_upload_detail,
                    NetworkSurveyConstants.AUTO_UPLOAD_RECORD_THRESHOLD,
                    NumberFormat.getIntegerInstance().format(NetworkSurveyConstants.AUTO_UPLOAD_RECORD_THRESHOLD),
                ),
                checked = state.autoUploadEnabled,
                onCheckedChange = actions::onSetAutoUpload,
                icon = R.drawable.ic_schedule,
                locked = state.autoUploadLocked,
                onLocked = { showLockSheet = true },
            )
            NsRowDivider()
            NsRow(
                title = stringResource(R.string.community_upload_now),
                subtitle = readyText(state),
                icon = R.drawable.ic_upload_24,
                onClick = if (state.uploadButtonEnabled) actions::onUploadNow else null,
            )
            NsRowDivider()
            NsRow(title = stringResource(R.string.community_upload_settings), icon = R.drawable.ic_settings, onClick = actions::onOpenUploadSettings)
        }

        when (val progress = state.uploadProgress) {
            is UploadProgressState.InProgress -> NsPanel { UploadProgressSection(progress, actions::onCancelUpload) }
            is UploadProgressState.Finished -> NsPanel { UploadResultsSection(progress, actions::onSeeOnMap) }
            UploadProgressState.Hidden -> Unit
        }
    }

    if (showLockSheet) {
        NsManagedLockSheet(
            onSeeAll = actions::onSeeOrganizationSettings,
            onUseOwnSettings = {
                showLockSheet = false
                confirmOwnSettings = true
            },
            onDismiss = { showLockSheet = false },
        )
    }
    if (confirmOwnSettings) {
        UseOwnSettingsDialog(onConfirm = actions::onUseOwnSettings, onDismiss = { confirmOwnSettings = false })
    }
}

@Composable
private fun readyText(state: UploadUiState): String = when {
    state.cellularQueueCount < 0 || state.wifiQueueCount < 0 -> ""
    state.cellularQueueCount + state.wifiQueueCount == 0 -> stringResource(R.string.community_nothing_ready)
    state.wifiQueueCount == 0 -> countText(R.plurals.community_ready_cellular, state.cellularQueueCount)
    state.cellularQueueCount == 0 -> countText(R.plurals.community_ready_wifi, state.wifiQueueCount)
    else -> pluralStringResource(
        R.plurals.community_ready_both,
        state.wifiQueueCount,
        NumberFormat.getIntegerInstance().format(state.cellularQueueCount),
        NumberFormat.getIntegerInstance().format(state.wifiQueueCount),
    )
}

/** A plural whose only argument is [count], grouped for the phone's locale ("1,204"). */
@Composable
private fun countText(@PluralsRes id: Int, count: Int): String =
    pluralStringResource(id, count, NumberFormat.getIntegerInstance().format(count))

private val OpenCelliDKey.detailRes: Int
    get() = when (this) {
        OpenCelliDKey.YOUR_KEY -> R.string.community_ocid_your_key
        OpenCelliDKey.ANONYMOUS -> R.string.community_ocid_anonymous
        OpenCelliDKey.MISSING -> R.string.community_ocid_needs_key
    }
