package com.craxiom.networksurvey.ui.scout

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.countText
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.survey.components.recordTypeNamesOr
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * "Remove NS-ABCD?": Wi-Fi then comes from this phone, saved setups that record Scout-only types
 * skip them (the count is shown only when there are any), and the Scout should also be forgotten
 * in Android's Bluetooth settings (`removeBond` is a hidden API, so the app can't do it).
 */
@Composable
fun RemoveScoutDialog(scoutName: String, setupsNeedingScout: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.scout_remove_title, scoutName),
        message = stringResource(R.string.scout_remove_body),
        confirmText = stringResource(R.string.remove),
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        if (setupsNeedingScout > 0) {
            Paragraph(countText(R.plurals.scout_remove_setups, setupsNeedingScout, recordTypeNamesOr(ScoutSetups.SCOUT_ONLY_TYPES)))
        }
        Paragraph(stringResource(R.string.scout_remove_bluetooth, scoutName))
    }
}

/** "Erase pairings on NS-ABCD?": the Scout forgets every phone, and the app removes it. */
@Composable
fun EraseScoutDialog(scoutName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.scout_erase_title, scoutName),
        message = stringResource(R.string.scout_erase_body, scoutName),
        confirmText = stringResource(R.string.scout_erase_confirm),
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** "Replace NS-ABCD?": this phone uses one Scout at a time. */
@Composable
fun ReplaceScoutDialog(pairedName: String, newName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.scout_replace_title, pairedName),
        message = stringResource(R.string.scout_replace_body, pairedName, newName),
        confirmText = stringResource(R.string.scout_replace_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun Paragraph(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = NsTheme.spacing.s))
}
