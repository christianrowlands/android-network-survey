package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NetworkTagVariant
import com.craxiom.networksurvey.ui.common.NsBandChip
import com.craxiom.networksurvey.ui.common.NsNetworkTag
import com.craxiom.networksurvey.ui.common.NsRssiColumn
import com.craxiom.networksurvey.ui.common.classifySecurity
import com.craxiom.networksurvey.ui.common.securityLabelText
import com.craxiom.networksurvey.ui.manufacturer.WifiManufacturerLabelText
import com.craxiom.networksurvey.ui.manufacturer.rememberWifiManufacturerLabelText
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.model.WifiAccessPointDisplay

/**
 * "Variant A – Calm Data" row for the flat Wi-Fi list.
 *
 * Three lines:
 *  1. SSID (accent) + optional Passpoint pill. dBm (signal-colored, tabular) on the right.
 *  2. BSSID (monospace) · vendor/status text. Meter bar on the right.
 *  3. Band chip + Security tag + Standard tag, spanning full width (wraps at large text sizes).
 *
 * The row is one TalkBack item that reads the name, tags and signal before the BSSID; a tap opens
 * the details, and a long press on the SSID or the BSSID copies it (also offered as TalkBack
 * actions).
 */
@Composable
fun WifiRowA(
    ap: WifiAccessPointDisplay,
    onClick: () -> Unit,
    onCopySsid: (() -> Unit)?,
    onCopyBssid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vendorLabel = rememberWifiManufacturerLabelText(ap.bssid)
    Column(
        modifier = modifier
            .wifiRowSemantics(
                spoken = spokenAccessPoint(ap, vendorLabel),
                clickLabel = stringResource(R.string.wifi_row_open_details),
                onClick = onClick,
                copyActions = copyActions(onCopySsid, onCopyBssid),
            )
            .heightIn(min = NsTheme.spacing.touchTarget)
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
                ) {
                    WifiSsidText(
                        ssid = ap.ssid,
                        hidden = ap.ssidIsHidden,
                        onClick = onClick,
                        onLongPress = onCopySsid,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (ap.passpoint) {
                        NsNetworkTag(text = stringResource(R.string.wifi_tag_passpoint), variant = NetworkTagVariant.Passpoint)
                    }
                }
                BssidVendorRow(ap, vendorLabel, onClick = onClick, onBssidLongPress = onCopyBssid)
            }
            NsRssiColumn(rssi = ap.rssi)
        }
        ChipsRow(ap, Modifier.clearAndSetSemantics {})
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsRow(ap: WifiAccessPointDisplay, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        ap.band?.let { band ->
            NsBandChip(band = band, channel = ap.channel)
        }
        if (ap.encryptionLabel.isNotBlank()) {
            NsNetworkTag(
                text = securityLabelText(ap.encryptionLabel),
                variant = NetworkTagVariant.Security(classifySecurity(ap.encryptionLabel)),
            )
        }
        if (ap.standardLabel.isNotBlank()) {
            NsNetworkTag(text = ap.standardLabel, variant = NetworkTagVariant.Standard)
        }
        if (ap.isExcluded) {
            ExcludedTag()
        }
    }
}

/**
 * Marks an SSID that is on the exclusion list, i.e. left out of survey data output. Shared by
 * the flat row and the group child row.
 */
@Composable
internal fun ExcludedTag(modifier: Modifier = Modifier) {
    val excludedDescription = stringResource(R.string.wifi_details_excluded_from_survey)
    NsNetworkTag(
        text = stringResource(R.string.wifi_tag_excluded),
        variant = NetworkTagVariant.Excluded,
        modifier = modifier.semantics { contentDescription = excludedDescription },
    )
}

/** @return The row's copy actions: the SSID's (when visible) and the BSSID's. */
@Composable
internal fun copyActions(onCopySsid: (() -> Unit)?, onCopyBssid: (() -> Unit)?): List<WifiCopyAction> {
    val resources = LocalResources.current
    return listOfNotNull(
        onCopySsid?.let { WifiCopyAction(resources.getString(R.string.wifi_row_copy_ssid), it) },
        onCopyBssid?.let { WifiCopyAction(resources.getString(R.string.bssid_copy_content_description), it) },
    )
}

/** @return What TalkBack reads for an access point row, the BSSID last. */
@Composable
internal fun spokenAccessPoint(
    ap: WifiAccessPointDisplay,
    vendorLabel: WifiManufacturerLabelText,
    includeName: Boolean = true,
): String {
    val resources = LocalResources.current
    val name = if (ap.ssidIsHidden) resources.getString(R.string.wifi_hidden_network_a11y) else ap.ssid
    return listOfNotNull(
        name.takeIf { includeName },
        resources.getString(R.string.wifi_tag_passpoint).takeIf { ap.passpoint && includeName },
        spokenBand(ap.band, ap.channel),
        ap.bandwidthLabel.takeIf { !includeName && it.isNotBlank() },
        securityLabelText(ap.encryptionLabel).takeIf { includeName && it.isNotBlank() },
        ap.standardLabel.takeIf { it.isNotBlank() },
        resources.getString(R.string.wifi_details_excluded_from_survey).takeIf { ap.isExcluded },
        spokenSignal(ap.rssi),
        vendorLabel.text,
        ap.bssid,
    ).joinToString(", ")
}
