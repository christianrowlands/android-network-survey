package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsFormBottomSheet

/**
 * Bottom sheet for adding an SSID to the exclusion list. The confirm button stays disabled until a
 * non-blank SSID is entered; duplicate and capacity handling are the ViewModel's job. The screen
 * only offers this sheet while the list is below its capacity limit.
 */
@Composable
fun SsidExclusionAddSheet(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var ssid by remember { mutableStateOf("") }

    NsFormBottomSheet(
        title = stringResource(R.string.ssid_exclusion_add_sheet_title),
        confirmText = stringResource(R.string.ssid_exclusion_add_confirm),
        confirmEnabled = ssid.isNotBlank(),
        onConfirm = {
            onAdd(ssid.trim())
            onDismiss()
        },
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text(stringResource(R.string.enter_ssid)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.ssid_exclusion_match_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
