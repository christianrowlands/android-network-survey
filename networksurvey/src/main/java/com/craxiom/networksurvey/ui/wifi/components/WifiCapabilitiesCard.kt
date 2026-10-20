package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsCardHeader
import com.craxiom.networksurvey.ui.common.NsDetailsCardFrame

/**
 * Capabilities card: renders the raw ScanResult.capabilities string in monospace.
 */
@Composable
fun WifiCapabilitiesCard(capabilities: String, modifier: Modifier = Modifier) {
    NsDetailsCardFrame(modifier = modifier) {
        NsCardHeader(text = stringResource(R.string.wifi_details_capabilities))
        SelectionContainer {
            Text(
                text = capabilities.ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
