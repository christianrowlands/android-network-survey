package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The sort choice for a Signals list: one radio row per option, applied and closed on tap.
 *
 * @param options The option labels, in the order of the list's sort indices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NsSortSheet(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NsTheme.spacing.l, vertical = NsTheme.spacing.s)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.menu_option_sort_by),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = NsTheme.spacing.s),
            )
            options.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = NsTheme.spacing.touchTarget)
                        .selectable(
                            selected = index == selectedIndex,
                            role = Role.RadioButton,
                            onClick = {
                                onSelected(index)
                                onDismiss()
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
                ) {
                    RadioButton(selected = index == selectedIndex, onClick = null)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
