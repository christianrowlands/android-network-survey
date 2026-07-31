package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsFormBottomSheet
import com.craxiom.networksurvey.ui.common.NsSectionLabel
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.SegmentedOption

private val BSSID_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

private const val MATCH_BY_NAME = 0
private const val MATCH_BY_AP = 1

/**
 * Bottom sheet for adding a watched network. The user gives an optional display name and chooses to
 * match by either the network name (SSID) or a specific access point (BSSID) via a segmented toggle;
 * exactly one identifier is captured. Validates that the chosen identifier is present (the Add button
 * stays disabled until it is) and that a BSSID is well formed. Duplicate detection is done by the
 * ViewModel.
 *
 * Keeps the same [onAdd] contract as the screen, passing the selected identifier and null for the
 * other.
 */
@Composable
fun WatchlistAddSheet(
    onAdd: (label: String, ssid: String?, bssid: String?) -> Unit,
    onDismiss: () -> Unit,
    initialSsid: String = "",
    initialBssid: String = ""
) {
    var label by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf(initialSsid) }
    var bssid by remember { mutableStateOf(initialBssid) }
    var matchBy by remember {
        mutableIntStateOf(if (initialBssid.isNotBlank() && initialSsid.isBlank()) MATCH_BY_AP else MATCH_BY_NAME)
    }
    var error by remember { mutableStateOf<String?>(null) }

    val badBssidError = stringResource(R.string.watchlist_validation_bssid_format)

    val matchByName = matchBy == MATCH_BY_NAME
    // isNotBlank() already treats whitespace-only input as empty, so the Add button stays disabled.
    val canAdd = if (matchByName) ssid.isNotBlank() else bssid.isNotBlank()

    NsFormBottomSheet(
        title = stringResource(R.string.watchlist_add_dialog_title),
        confirmText = stringResource(R.string.watchlist_add_confirm),
        confirmEnabled = canAdd,
        onConfirm = {
            if (matchByName) {
                onAdd(label, ssid.trim(), null)
                onDismiss()
            } else {
                val trimmedBssid = bssid.trim()
                if (!BSSID_REGEX.matches(trimmedBssid)) {
                    error = badBssidError
                } else {
                    onAdd(label, null, trimmedBssid)
                    onDismiss()
                }
            }
        },
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringResource(R.string.watchlist_field_display_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        NsSectionLabel(text = stringResource(R.string.watchlist_match_by))
        Spacer(Modifier.height(8.dp))
        NsSegmentedToggle(
            options = listOf(
                SegmentedOption(stringResource(R.string.watchlist_match_by_name)),
                SegmentedOption(stringResource(R.string.watchlist_match_by_ap)),
            ),
            selectedIndex = matchBy,
            onSelected = {
                matchBy = it
                error = null
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        if (matchByName) {
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
        } else {
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
