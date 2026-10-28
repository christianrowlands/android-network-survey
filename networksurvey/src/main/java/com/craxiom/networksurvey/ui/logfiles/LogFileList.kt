package com.craxiom.networksurvey.ui.logfiles

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsEmptyState
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSummaryText
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.friendlyTimeText
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.CountFormat

/**
 * Where the recent files list is: how many surveys it shows, whether it is selecting files to share,
 * and which files are selected (by name).
 */
data class LogFileListState(
    val shownGroups: Int,
    val selecting: Boolean,
    val selected: Set<String>,
)

/** What the recent files list can ask for. */
interface LogFileListActions {
    fun onShare(file: LogFile)
    fun onStartSelecting(file: LogFile)
    fun onSetSelected(files: List<LogFile>, selected: Boolean)
    fun onShowMore()
}

/**
 * The recent files, one panel per survey with its start time and size on top. Tapping a file
 * shares it; while selecting, tapping toggles it and the survey's checkbox toggles all its files.
 */
@Composable
fun LogFileList(listing: LogFilesListing.Files, nowMs: Long, state: LogFileListState, actions: LogFileListActions) {
    if (listing.groups.isEmpty()) {
        NsRowGroup { NsEmptyState(stringResource(R.string.log_files_empty), R.drawable.ic_description) }
        NsSummaryText(stringResource(R.string.log_files_reinstall, LogFilesRepository.DISPLAY_PATH))
        return
    }
    listing.groups.take(state.shownGroups).forEach { group ->
        NsRowGroup {
            GroupHeader(group, nowMs, state, actions)
            group.files.forEach { file ->
                NsRowDivider()
                FileRow(file, nowMs, state, actions)
            }
        }
    }
    if (listing.groups.size > state.shownGroups) {
        Column {
            NsSummaryText(
                pluralStringResource(
                    R.plurals.log_files_showing,
                    listing.groups.size,
                    CountFormat.format(state.shownGroups),
                    CountFormat.format(listing.groups.size),
                ),
            )
            NsTextAction(stringResource(R.string.log_files_show_more), actions::onShowMore)
        }
    }
}

@Composable
private fun GroupHeader(group: LogFileGroup, nowMs: Long, state: LogFileListState, actions: LogFileListActions) {
    val context = LocalContext.current
    val title = friendlyTimeText(group.startedMs, nowMs)
    val chosen = group.files.count { it.name in state.selected }
    val toggleState = when (chosen) {
        0 -> ToggleableState.Off
        group.files.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val toggle = if (state.selecting) {
        Modifier.triStateToggleable(state = toggleState, role = Role.Checkbox) {
            actions.onSetSelected(group.files, toggleState != ToggleableState.On)
        }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .then(toggle)
            .padding(vertical = NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(
                text = pluralStringResource(
                    R.plurals.log_files_group_detail, group.files.size, group.files.size,
                    Formatter.formatShortFileSize(context, group.totalBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.selecting) TriStateCheckbox(state = toggleState, onClick = null)
    }
}

@Composable
private fun FileRow(file: LogFile, nowMs: Long, state: LogFileListState, actions: LogFileListActions) {
    val context = LocalContext.current
    val detail = stringResource(
        R.string.log_files_row_detail,
        Formatter.formatShortFileSize(context, file.sizeBytes),
        friendlyTimeText(file.modifiedMs, nowMs),
    )
    if (state.selecting) {
        val checked = file.name in state.selected
        NsRow(
            title = breakableFileName(file.name),
            subtitle = detail,
            icon = R.drawable.ic_description,
            modifier = Modifier.toggleable(value = checked, role = Role.Checkbox) { actions.onSetSelected(listOf(file), it) },
        ) {
            // The row carries the semantics, so the checkbox itself is not a second target.
            Checkbox(checked = checked, onCheckedChange = null)
        }
    } else {
        NsRow(
            title = breakableFileName(file.name),
            subtitle = detail,
            icon = R.drawable.ic_description,
            onClickLabel = stringResource(R.string.log_files_share, file.name),
            onClick = { actions.onShare(file) },
            onLongClickLabel = stringResource(R.string.log_files_select_named, file.name),
            onLongClick = { actions.onStartSelecting(file) },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A zero-width space: an invisible point where a line may break. */
private const val LINE_BREAK_OPPORTUNITY = "\u200B"

/**
 * The file name with a break opportunity after each hyphen. Unicode line breaking keeps a hyphen
 * that is followed by a digit together with the digits (it reads as a minus sign), so a name like
 * craxiom-devicestatus-20261028-124351.csv had no break point and wrapped mid-extension (".cs" / "v").
 * Only the displayed text changes; share and select labels keep the real name.
 */
internal fun breakableFileName(name: String): String = name.replace("-", "-$LINE_BREAK_OPPORTUNITY")
