package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.NsAlertDialogScaffold

private val BSSID_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

/**
 * Dialog for adding a watched network. Supports a label, an SSID, and an optional BSSID behind an
 * "Advanced" disclosure (the shared single-field input dialog cannot model this). Validates that at
 * least one of SSID/BSSID is provided and that any BSSID is well formed; duplicate detection is done
 * by the ViewModel.
 */
@Composable
fun WatchlistAddDialog(
    onAdd: (label: String, ssid: String?, bssid: String?) -> Unit,
    onDismiss: () -> Unit,
    initialSsid: String = "",
    initialBssid: String = ""
) {
    var label by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf(initialSsid) }
    var bssid by remember { mutableStateOf(initialBssid) }
    var advancedExpanded by remember { mutableStateOf(initialBssid.isNotEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    val needOneError = stringResource(R.string.watchlist_validation_need_one)
    val badBssidError = stringResource(R.string.watchlist_validation_bssid_format)

    NsAlertDialogScaffold(
        title = stringResource(R.string.watchlist_add_dialog_title),
        onDismissRequest = onDismiss,
        // Require an explicit Cancel or Add tap so an accidental outside-tap or back-press does not
        // discard what the user has typed.
        cancelable = false,
        confirmButton = {
            TextButton(onClick = {
                val trimmedSsid = ssid.trim().ifEmpty { null }
                val trimmedBssid = bssid.trim().ifEmpty { null }
                when {
                    trimmedSsid == null && trimmedBssid == null -> error = needOneError
                    trimmedBssid != null && !BSSID_REGEX.matches(trimmedBssid) -> error =
                        badBssidError

                    else -> {
                        onAdd(label, trimmedSsid, trimmedBssid)
                        onDismiss()
                    }
                }
            }) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringResource(R.string.watchlist_field_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ssid,
            onValueChange = {
                ssid = it
                error = null
            },
            label = { Text(stringResource(R.string.watchlist_field_ssid)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(
            onClick = { advancedExpanded = !advancedExpanded },
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(stringResource(R.string.watchlist_advanced_bssid))
        }

        if (advancedExpanded) {
            OutlinedTextField(
                value = bssid,
                onValueChange = {
                    bssid = it
                    error = null
                },
                label = { Text(stringResource(R.string.watchlist_field_bssid)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.watchlist_bssid_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
