package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import com.craxiom.networksurvey.ui.manufacturer.WifiManufacturerLabelText
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.model.WifiAccessPointDisplay

/**
 * BSSID (monospace, long press to copy) plus the optional manufacturer label separated by a
 * dot. Shared by the flat Wi-Fi row and the group child row. Uses a FlowRow so that at large
 * font scales the vendor label wraps below the BSSID instead of being pushed out of the row.
 * The dot is grouped with the vendor so a wrap cannot strand it on its own line.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun BssidVendorRow(
    ap: WifiAccessPointDisplay,
    vendorLabel: WifiManufacturerLabelText,
    onClick: () -> Unit,
    onBssidLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodySmall
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            text = ap.bssid,
            style = style.copy(fontFamily = NsTheme.textStyles.mono.fontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onBssidLongPress,
            ),
        )
        vendorLabel.text?.let { text ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "·",
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = text,
                    style = style,
                    fontStyle = if (vendorLabel.italic) FontStyle.Italic else FontStyle.Normal,
                    color = if (vendorLabel.italic) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
