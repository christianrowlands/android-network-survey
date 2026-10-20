package com.craxiom.networksurvey.ui.survey.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.common.NsFormBottomSheet
import com.craxiom.networksurvey.ui.survey.components.labelRes
import com.craxiom.networksurvey.ui.survey.model.EditListItemUi
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * "Show on Survey screen": lets the user hide destinations they never use. A destination that is
 * on can't be hidden, so the screen never hides something that is receiving records.
 */
@Composable
fun EditDestinationListSheet(
    items: List<EditListItemUi>,
    onSetShown: (Destination, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    NsFormBottomSheet(
        title = stringResource(R.string.survey_edit_list_title),
        confirmText = stringResource(R.string.survey_done),
        confirmEnabled = true,
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NsTheme.spacing.rowMinHeight)
                    .toggleable(
                        value = item.shown,
                        enabled = !item.onInSetup,
                        role = Role.Checkbox,
                        onValueChange = { onSetShown(item.destination, it) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = item.shown, onCheckedChange = null, enabled = !item.onInSetup)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(item.destination.labelRes), style = MaterialTheme.typography.bodyLarge)
                    if (item.onInSetup) {
                        Text(
                            stringResource(R.string.survey_edit_list_on_in_setup),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
