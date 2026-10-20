package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The filter sheet of a Signals list: a title with Clear, a search field, a row of pills, and how
 * many rows the filter leaves. Changes apply as the user types or toggles. Display only: it never
 * changes what is recorded.
 *
 * The query lives in local state so typing stays synchronous; binding the field to a view model's
 * StateFlow makes every keystroke an async round trip, which can drop characters or move the
 * cursor. The only outside change while the sheet is open is Clear, which resets it explicitly.
 *
 * @param summary "Showing 3 of 12 devices".
 * @param pills The filter's toggles, laid out in a wrapping row.
 * @param footer Anything under the summary, such as a link to a related setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NsListFilterSheet(
    title: String,
    searchHint: String,
    query: String,
    active: Boolean,
    summary: String,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    pills: @Composable FlowRowScope.() -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = NsTheme.spacing.l, vertical = NsTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    TextButton(onClick = {
                        text = ""
                        onClear()
                    }) {
                        Text(text = stringResource(R.string.wifi_filter_clear))
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onQueryChanged(it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(text = searchHint) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = {
                            text = ""
                            onQueryChanged("")
                        }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.wifi_filter_clear_search))
                        }
                    }
                },
            )
            // A wrapping row so the pills never clip at large font sizes.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
                verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
                itemVerticalAlignment = Alignment.CenterVertically,
                content = pills,
            )
            Text(text = summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            footer()
        }
    }
}
