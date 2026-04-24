package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R

/**
 * Capabilities card — renders the raw ScanResult.capabilities string in monospace.
 */
@Composable
fun WifiCapabilitiesCard(capabilities: String, modifier: Modifier = Modifier) {
    DetailsCardFrame(modifier = modifier) {
        CardHeader(text = stringResource(R.string.wifi_details_capabilities))
        SelectionContainer {
            Text(
                text = capabilities.ifBlank { "-" },
                style = TextStyle(
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
