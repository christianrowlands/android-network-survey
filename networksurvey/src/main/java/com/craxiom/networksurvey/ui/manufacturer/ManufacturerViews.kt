package com.craxiom.networksurvey.ui.manufacturer

import androidx.compose.runtime.Composable
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.oui.OuiStatus
import androidx.compose.ui.res.stringResource

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
