package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NetworkTagVariant
import com.craxiom.networksurvey.ui.common.NsBandChip
import com.craxiom.networksurvey.ui.common.NsNetworkTag
import com.craxiom.networksurvey.ui.common.NsRssiColumn
import com.craxiom.networksurvey.ui.common.securityLabelText
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.model.WifiDisplayItem

private const val CHEVRON_TURN_MS = 180

/**
 * Grouped-by-SSID parent row. Taps toggle expand/collapse only; individual APs navigate from
 * the child rows. Passpoint badge shows if ANY AP in the group is Passpoint. One TalkBack item
 * reads the name, AP count, bands, security and best signal; a long press on the SSID copies it.
 */
@Composable
fun WifiGroupParentRow(
    group: WifiDisplayItem.GroupParent,
    onToggle: () -> Unit,
    onCopySsid: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val bg = if (group.expanded) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Row(
        modifier = modifier
            .background(bg)
            .wifiRowSemantics(
                spoken = spokenGroup(group),
                clickLabel = stringResource(if (group.expanded) R.string.wifi_group_collapse else R.string.wifi_group_expand),
                onClick = onToggle,
                copyActions = copyActions(onCopySsid, onCopyBssid = null),
            )
            .heightIn(min = NsTheme.spacing.touchTarget)
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.m),
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        ) {
            TitleRow(group, onClick = onToggle, onSsidLongPress = onCopySsid)
            MetaRow(group)
        }
        NsRssiColumn(rssi = group.bestRssi, modifier = Modifier.clearAndSetSemantics {})
        Chevron(expanded = group.expanded)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleRow(
    group: WifiDisplayItem.GroupParent,
    onClick: () -> Unit,
    onSsidLongPress: (() -> Unit)?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        WifiSsidText(
            ssid = group.displaySsid,
            hidden = group.isHidden,
            onClick = onClick,
            onLongPress = onSsidLongPress,
        )
        if (group.anyPasspoint) {
            NsNetworkTag(text = stringResource(R.string.wifi_tag_passpoint), variant = NetworkTagVariant.Passpoint)
        }
        GroupCountPill(count = group.apCount)
        if (group.allExcluded) {
            ExcludedTag()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(group: WifiDisplayItem.GroupParent) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        group.bands.forEach { band ->
            NsBandChip(band = band, channel = null)
        }
        if (group.encryptionLabel.isNotBlank()) {
            MetaText(securityLabelText(group.encryptionLabel), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // A mixed group (possible if exclusion ever becomes per-BSSID) gets a muted count
        // instead of the full tag; fully excluded groups show the tag in the title row.
        if (group.excludedCount > 0 && !group.allExcluded) {
            MetaText(
                pluralStringResource(R.plurals.wifi_group_excluded_count, group.excludedCount, group.excludedCount),
                NsTheme.colors.status.warning.content,
            )
        }
    }
}

@Composable
private fun MetaText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun GroupCountPill(count: Int) {
    Text(
        text = pluralStringResource(R.plurals.wifi_ap_count, count, count),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(NsTheme.shapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = NsTheme.spacing.s, vertical = NsTheme.spacing.xxs),
    )
}

@Composable
private fun Chevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = CHEVRON_TURN_MS),
        label = "chevron",
    )
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.rotate(rotation),
    )
}

/** @return What TalkBack reads for a group row. */
@Composable
private fun spokenGroup(group: WifiDisplayItem.GroupParent): String {
    val resources = LocalResources.current
    val name = if (group.isHidden) resources.getString(R.string.wifi_hidden_network_a11y) else group.displaySsid
    return listOfNotNull(
        name,
        resources.getString(R.string.wifi_tag_passpoint).takeIf { group.anyPasspoint },
        pluralStringResource(R.plurals.wifi_ap_count, group.apCount, group.apCount),
        group.bands.mapNotNull { spokenBand(it, channel = null) }.joinToString(", ").ifBlank { null },
        securityLabelText(group.encryptionLabel).ifBlank { null },
        resources.getString(R.string.wifi_details_excluded_from_survey).takeIf { group.allExcluded },
        pluralStringResource(R.plurals.wifi_group_excluded_count, group.excludedCount, group.excludedCount)
            .takeIf { group.excludedCount > 0 && !group.allExcluded },
        spokenSignal(group.bestRssi),
    ).joinToString(", ")
}
