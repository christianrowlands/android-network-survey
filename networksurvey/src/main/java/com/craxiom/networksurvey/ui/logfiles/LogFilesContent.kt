package com.craxiom.networksurvey.ui.logfiles

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsCtaBar
import com.craxiom.networksurvey.ui.common.NsManagedLockSheet
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSummaryText
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.survey.DestinationPageStatus
import com.craxiom.networksurvey.ui.survey.components.DestinationStatusPanel
import com.craxiom.networksurvey.ui.survey.sheets.UseOwnSettingsDialog
import com.craxiom.networksurvey.ui.common.dialogs.NsSingleChoiceDialog
import com.craxiom.networksurvey.ui.theme.NsTheme

/** Everything the Log files page can ask for. */
interface LogFilesActions {
    fun onBack() {}
    fun onSetRollover(megabytes: Int) {}
    fun onShare(files: List<LogFile>) {}
    fun onOpenFolder() {}
    fun onSeeOrganizationSettings() {}
    fun onUseOwnSettings() {}
}

/**
 * The Log files page, stateless: where the files are (tap to open them in a file manager), when a
 * new file starts, and the recent files by survey. A file shares when tapped; Select (or a long
 * press) picks several files or whole surveys to share at once.
 */
@Composable
fun LogFilesContent(
    state: LogFilesUiState,
    actions: LogFilesActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    status: DestinationPageStatus? = null,
) {
    val context = LocalContext.current
    var choosingRollover by rememberSaveable { mutableStateOf(false) }
    var showLockSheet by rememberSaveable { mutableStateOf(false) }
    var confirmOwnSettings by rememberSaveable { mutableStateOf(false) }
    var shownGroups by rememberSaveable { mutableIntStateOf(GROUPS_PER_PAGE) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedNames by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val files = (state.listing as? LogFilesListing.Files)?.files.orEmpty()
    // Files that went away since they were picked drop out
    val chosen = files.filter { it.name in selectedNames }
    val stopSelecting = {
        selecting = false
        selectedNames = emptySet()
    }
    BackHandler(enabled = selecting, onBack = stopSelecting)

    val listActions = object : LogFileListActions {
        override fun onShare(file: LogFile) = actions.onShare(listOf(file))
        override fun onStartSelecting(file: LogFile) {
            selecting = true
            selectedNames = setOf(file.name)
        }

        override fun onSetSelected(files: List<LogFile>, selected: Boolean) {
            val names = files.map { it.name }.toSet()
            selectedNames = if (selected) selectedNames + names else selectedNames - names
        }

        override fun onShowMore() {
            shownGroups += GROUPS_PER_PAGE
        }
    }

    NsPageScaffold(
        title = stringResource(R.string.survey_dest_log_files),
        onBack = actions::onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        bottomBar = {
            if (selecting) {
                NsCtaBar {
                    NsPrimaryCta(
                        text = pluralStringResource(R.plurals.log_files_share_count, chosen.size, chosen.size),
                        onClick = {
                            actions.onShare(chosen)
                            stopSelecting()
                        },
                        icon = R.drawable.ic_share,
                        enabled = chosen.isNotEmpty(),
                    )
                }
            }
        },
    ) {
        // While a survey saves here: the same Saving or Partly saving as the Survey screen's row
        status?.let { DestinationStatusPanel(it) }
        NsRowGroup {
            val free = state.freeSpaceBytes?.let { stringResource(R.string.log_files_free, Formatter.formatShortFileSize(context, it)) }
            NsRow(
                title = stringResource(R.string.log_files_location),
                subtitle = listOfNotNull(stringResource(R.string.log_files_location_detail, LogFilesRepository.DISPLAY_PATH), free)
                    .joinToString(LINE_BREAK),
                icon = R.drawable.ic_folder,
                onClickLabel = stringResource(R.string.log_files_open_folder),
                onClick = actions::onOpenFolder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_open_in_new),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NsRowDivider()
            NsRow(
                title = stringResource(R.string.log_files_rollover),
                subtitle = rolloverLabel(state.rolloverMb),
                icon = R.drawable.ic_autorenew,
                locked = state.rolloverLocked,
                onClick = { if (state.rolloverLocked) showLockSheet = true else choosingRollover = true },
            )
        }

        SectionHeader(
            title = stringResource(R.string.log_files_recent),
            action = if (files.isEmpty()) {
                null
            } else {
                {
                    NsTextAction(
                        text = stringResource(if (selecting) R.string.cancel else R.string.log_files_select),
                        onClick = { if (selecting) stopSelecting() else selecting = true },
                    )
                }
            },
        )
        when (val listing = state.listing) {
            LogFilesListing.Unreadable -> NsSummaryText(stringResource(R.string.log_files_unreadable), color = NsTheme.colors.status.warningText)
            is LogFilesListing.Files -> if (!state.loading) {
                LogFileList(listing, state.nowMs, LogFileListState(shownGroups, selecting, selectedNames), listActions)
            }
        }
    }

    if (choosingRollover) {
        val choices = state.rolloverChoices
        NsSingleChoiceDialog(
            title = stringResource(R.string.log_files_rollover),
            options = choices.map { rolloverLabel(it) },
            selectedIndex = choices.indexOf(state.rolloverMb),
            onConfirm = { index ->
                choosingRollover = false
                choices.getOrNull(index)?.let(actions::onSetRollover)
            },
            onDismiss = { choosingRollover = false },
        )
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

/** "10 MB", or "Never (...)" for 0. */
@Composable
private fun rolloverLabel(megabytes: Int): String =
    if (megabytes <= 0) stringResource(R.string.log_files_rollover_never) else stringResource(R.string.log_files_rollover_mb, megabytes)

private const val LINE_BREAK = "\n"

/** How many surveys the list shows at first, and how many more each "Show more" adds. */
private const val GROUPS_PER_PAGE = 20
