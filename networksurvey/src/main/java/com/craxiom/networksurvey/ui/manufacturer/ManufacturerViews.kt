package com.craxiom.networksurvey.ui.manufacturer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.oui.OuiRepository
import com.craxiom.networksurvey.data.oui.OuiResult
import com.craxiom.networksurvey.data.oui.OuiStatus
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource

private const val LOADING_DELAY_MS = 250L

/**
 * Renders a one-line manufacturer sub-caption for a WiFi BSSID. Designed to slot directly under
 * the BSSID value with `labelMedium` typography + `onSurfaceVariant` color, a quiet indicator
 * that the value is derived from the BSSID above.
 *
 * Loading state suppresses text for the first [LOADING_DELAY_MS] ms to avoid flashing
 * "Looking up…" when the cache hits immediately. After that threshold, shows the muted loading
 * string until the lookup resolves.
 */
@Composable
fun WifiManufacturerSubCaption(
    bssid: String,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = remember(context) { OuiRepository.getInstance(context) }

    val result by produceState(initialValue = OuiResult.LOADING, key1 = bssid) {
        value = runCatching { repository.lookup(bssid) }.getOrElse { OuiResult.OFFLINE }
    }

    val label = remember(result, context) { result.toManufacturerLabel(context) }

    var showLoading by remember(bssid) { mutableStateOf(false) }
    LaunchedEffect(bssid) {
        showLoading = false
        delay(LOADING_DELAY_MS)
        showLoading = true
    }

    val text = when (label) {
        ManufacturerLabel.Loading -> if (showLoading) stringResource(R.string.manufacturer_loading) else null
        is ManufacturerLabel.Resolved -> label.vendor
        ManufacturerLabel.Randomized -> stringResource(R.string.manufacturer_randomized_wifi)
        ManufacturerLabel.Unknown -> stringResource(R.string.manufacturer_unknown)
        ManufacturerLabel.Private -> stringResource(R.string.manufacturer_private)
        ManufacturerLabel.SharedVendorBlock -> stringResource(R.string.manufacturer_shared_block)
        ManufacturerLabel.Offline -> stringResource(R.string.manufacturer_offline)
        ManufacturerLabel.DisabledByUser -> null
        ManufacturerLabel.DisabledByAdmin -> null
    }

    val italic = when (label) {
        ManufacturerLabel.Randomized,
        ManufacturerLabel.Unknown,
        ManufacturerLabel.Private,
        ManufacturerLabel.SharedVendorBlock,
        ManufacturerLabel.Offline -> true

        else -> false
    }

    Column(modifier = modifier) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (label is ManufacturerLabel.DisabledByUser || label is ManufacturerLabel.DisabledByAdmin) {
            Spacer(modifier = Modifier.height(4.dp))
            val disabledCopy = when (label) {
                is ManufacturerLabel.DisabledByAdmin ->
                    stringResource(R.string.manufacturer_disabled_mdm)

                else -> stringResource(R.string.manufacturer_disabled_user)
            }
            val clickModifier =
                if (label is ManufacturerLabel.DisabledByUser && onOpenSettings != null) {
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSettings() }
                } else {
                    Modifier.fillMaxWidth()
                }
            Text(
                text = disabledCopy,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = clickModifier
            )
        }
    }
}

/**
 * Copy for missing/unknown OUI status rows on the Bluetooth details expanded view.
 *
 * For [OuiStatus.LOOKUP_DISABLED] this returns a short "Lookup disabled" label intended to fit
 * inside the Chipset row's value cell. The actionable "enable in Settings" call-to-action is
 * rendered once beneath the whole card, not per-row, so it doesn't overflow a table cell.
 */
@Composable
fun ouiStatusCopy(status: OuiStatus): String {
    return when (status) {
        OuiStatus.LAA -> stringResource(R.string.manufacturer_randomized_bt)
        OuiStatus.UNKNOWN -> stringResource(R.string.manufacturer_unknown)
        OuiStatus.PRIVATE -> stringResource(R.string.manufacturer_private)
        OuiStatus.SHARED_VENDOR_BLOCK -> stringResource(R.string.manufacturer_shared_block)
        OuiStatus.OFFLINE, OuiStatus.TRANSIENT_FAILURE -> stringResource(R.string.manufacturer_offline)
        OuiStatus.LOADING -> stringResource(R.string.manufacturer_loading)
        OuiStatus.LOOKUP_DISABLED -> stringResource(R.string.manufacturer_lookup_disabled_short)
        OuiStatus.RESOLVED -> stringResource(R.string.manufacturer_unknown)
    }
}
