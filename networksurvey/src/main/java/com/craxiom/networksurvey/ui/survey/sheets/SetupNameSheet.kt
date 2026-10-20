package com.craxiom.networksurvey.ui.survey.sheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.ui.common.NsFormBottomSheet
import com.craxiom.networksurvey.ui.survey.model.SetupNameProblem
import com.craxiom.networksurvey.ui.survey.model.SetupNames
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * "Save current": names the current selection. A name that another saved setup already has
 * replaces that setup, and the sheet says so and turns Save into Replace.
 */
@Composable
fun SaveSetupSheet(saved: List<SurveySetup>, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    SetupNameSheet(
        title = stringResource(R.string.survey_setup_save_title),
        saved = saved,
        renaming = null,
        onConfirm = onSave,
        onDismiss = onDismiss,
    )
}

/**
 * Renames [setup]. A name another saved setup has is refused, since renaming must not replace a
 * setup.
 */
@Composable
fun RenameSetupSheet(setup: SurveySetup, saved: List<SurveySetup>, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    SetupNameSheet(
        title = stringResource(R.string.survey_setup_rename_title),
        saved = saved,
        renaming = setup,
        onConfirm = onRename,
        onDismiss = onDismiss,
    )
}

/**
 * The name field for saving ([renaming] is null) or renaming a setup, with the name rules from
 * [SetupNames]. A field inside a sheet rather than a dialog over the Manage sheet, so two windows
 * never fight over the keyboard.
 */
@Composable
private fun SetupNameSheet(
    title: String,
    saved: List<SurveySetup>,
    renaming: SurveySetup?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Renaming starts with the whole name selected, so typing replaces it
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val initial = renaming?.name.orEmpty()
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    val name = field.text
    val problem = SetupNames.problem(name, saved, exceptId = renaming?.id)
    val replaces = renaming == null && problem == SetupNameProblem.TAKEN
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The field attaches a frame after the sheet opens; before that the request would throw
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    NsFormBottomSheet(
        title = title,
        confirmText = stringResource(if (replaces) R.string.survey_setup_replace else R.string.save),
        confirmEnabled = problem == null || replaces,
        onConfirm = {
            onConfirm(name.trim())
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            label = { Text(stringResource(R.string.survey_setup_name_label)) },
            singleLine = true,
            isError = problem == SetupNameProblem.TOO_LONG || (renaming != null && problem == SetupNameProblem.TAKEN),
            supportingText = supportingText(problem, renaming != null),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        if (renaming == null) {
            Text(
                text = stringResource(R.string.survey_setup_save_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = NsTheme.spacing.xs),
            )
        }
    }
}

/** The line under the name field: the length rule, or what a name in use means here. */
private fun supportingText(problem: SetupNameProblem?, renaming: Boolean): (@Composable () -> Unit)? = when (problem) {
    SetupNameProblem.TOO_LONG -> {
        { Text(pluralStringResource(R.plurals.survey_setup_name_too_long, SurveySetup.MAX_NAME_LENGTH, SurveySetup.MAX_NAME_LENGTH)) }
    }

    SetupNameProblem.TAKEN -> {
        { Text(stringResource(if (renaming) R.string.survey_setup_name_taken else R.string.survey_setup_replace_note)) }
    }

    else -> null
}
