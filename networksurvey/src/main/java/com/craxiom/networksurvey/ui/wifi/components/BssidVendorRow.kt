package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.ui.manufacturer.rememberWifiManufacturerLabelText
import com.craxiom.networksurvey.ui.theme.WifiTokens
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
    onClick: () -> Unit,
    onBssidLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vendorLabel = rememberWifiManufacturerLabelText(ap.bssid)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            text = ap.bssid,
            style = TextStyle(fontSize = 11.5.sp, fontFamily = FontFamily.Monospace),
            color = WifiTokens.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onBssidLongPress,
            ),
        )
        vendorLabel.text?.let { text ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "·",
                    style = TextStyle(fontSize = 11.5.sp),
                    color = WifiTokens.InkFaint,
                )
                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = 11.5.sp,
                        fontStyle = if (vendorLabel.italic) FontStyle.Italic else FontStyle.Normal,
                    ),
                    color = if (vendorLabel.italic) WifiTokens.InkFaint else WifiTokens.InkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
