package com.craxiom.networksurvey.ui.manufacturer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.oui.OuiRepository
import com.craxiom.networksurvey.data.oui.OuiResult
import kotlinx.coroutines.delay

private const val LOADING_DELAY_MS = 250L

/**
 * Resolved text + italic-or-not hint for a Wi-Fi manufacturer label. Returned by
 * [rememberWifiManufacturerLabelText]; callers are responsible for their own typography and color.
 *
 * `text` is null when the label should be suppressed (loading window, or disabled by user/MDM.
 * In those cases the list rows prefer to hide the field rather than show an inactive string).
 */
data class WifiManufacturerLabelText(
    val text: String?,
    val italic: Boolean,
)

/**
 * Resolves a BSSID to a manufacturer label suitable for inline display. Handles the
 * ~250 ms loading delay so cache-hit lookups don't flash "Looking up…".
 *
 * This powers both the details-screen sub-caption (via [WifiManufacturerSubCaption]) and the
 * redesigned list rows' "BSSID · vendor" line; callers choose their own typography and color.
 */
@Composable
fun rememberWifiManufacturerLabelText(bssid: String): WifiManufacturerLabelText {
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
        ManufacturerLabel.Randomized -> stringResource(R.string.wifi_randomized_mac)
        ManufacturerLabel.Unknown -> stringResource(R.string.wifi_unknown_vendor)
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

    return WifiManufacturerLabelText(text = text, italic = italic)
}
