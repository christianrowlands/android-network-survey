package com.craxiom.networksurvey.ui.survey.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.ui.common.DISABLED_ALPHA
import com.craxiom.networksurvey.ui.common.NsFormBottomSheet
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.survey.components.destinationNames
import com.craxiom.networksurvey.ui.survey.components.recordTypeNames
import com.craxiom.networksurvey.ui.survey.model.SavedSetupsUi
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The saved setups, opened from the Survey tab's app bar while Ready. Tapping a setup sets both
 * questions to it and closes the sheet; the star lists it first; Rename and Delete sit in each
 * row's menu; "Save current setup" names what the Survey screen is set to now. Saving and renaming
 * swap this sheet for the name sheet and come back to it after.
 */
@Composable
fun SetupsSheet(
    state: SavedSetupsUi,
    onPick: (String) -> Unit,
    onSave: (String) -> Unit,
    onStar: (String, Boolean) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val setups = state.setups
    var saving by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    if (saving) {
        SaveSetupSheet(setups, onSave, onDismiss = { saving = false })
        return
    }
    setups.firstOrNull { it.id == renaming }?.let { setup ->
        RenameSetupSheet(setup, setups, onRename = { onRename(setup.id, it) }, onDismiss = { renaming = null })
        return
    }

    NsFormBottomSheet(
        title = stringResource(R.string.survey_setups_heading),
        confirmText = stringResource(R.string.survey_done),
        confirmEnabled = true,
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        if (setups.isEmpty()) {
            Text(
                text = stringResource(R.string.survey_setups_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = NsTheme.spacing.s),
            )
        }
        setups.forEach { setup ->
            SetupRow(
                setup = setup,
                selected = setup.id == state.selected?.id,
                onPick = {
                    onPick(setup.id)
                    onDismiss()
                },
                onStar = { onStar(setup.id, it) },
                onRename = { renaming = setup.id },
                onDelete = { deleting = setup.id },
            )
        }
        if (setups.isNotEmpty()) HorizontalDivider()
        SaveCurrentRow(enabled = state.canSave, onClick = { saving = true })
    }

    setups.firstOrNull { it.id == deleting }?.let { setup ->
        NsConfirmationDialog(
            title = stringResource(R.string.survey_setup_delete_title, setup.name),
            message = stringResource(R.string.survey_setup_delete_message),
            confirmText = stringResource(R.string.survey_setup_delete),
            onConfirm = { onDelete(setup.id) },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
}

@Composable
private fun SetupRow(
    setup: SurveySetup,
    selected: Boolean,
    onPick: () -> Unit,
    onStar: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val starredText = stringResource(R.string.survey_setup_starred)
    val notStarredText = stringResource(R.string.survey_setup_not_starred)
    val starLabel = stringResource(R.string.survey_setup_star_named, setup.name)
    val selectedText = stringResource(R.string.survey_setup_in_use)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .clickable(
                onClickLabel = stringResource(R.string.survey_setup_use_named, setup.name),
                role = Role.Button,
                onClick = onPick,
            )
            .semantics { if (selected) stateDescription = selectedText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(
            checked = setup.starred,
            onCheckedChange = onStar,
            modifier = Modifier.semantics {
                contentDescription = starLabel
                stateDescription = if (setup.starred) starredText else notStarredText
            },
        ) {
            Icon(
                painterResource(if (setup.starred) R.drawable.ic_star else R.drawable.ic_star_border),
                contentDescription = null,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(setup.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = setupSummary(setup),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        SetupMenu(setup.name, onRename, onDelete)
    }
}

/** Rename and Delete, behind the row's overflow button so the row itself stays one big target. */
@Composable
private fun SetupMenu(name: String, onRename: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.survey_setup_more_named, name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.survey_setup_rename)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                onClick = {
                    open = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.survey_setup_delete)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun SaveCurrentRow(enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 12 dp on each side of a 24 dp icon lines it up with the 48 dp star buttons above
        Icon(
            painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(horizontal = NsTheme.spacing.m),
        )
        Text(stringResource(R.string.survey_setups_save_current_row), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** "Cellular, Phone state and Wi-Fi to NS Analytics". */
@Composable
fun setupSummary(setup: SurveySetup): String = stringResource(
    R.string.survey_setup_summary,
    recordTypeNames(SurveyUiStateMapper.ordered(setup.recordTypes)),
    destinationNames(setup.destinations.sortedBy { it.ordinal }),
)
