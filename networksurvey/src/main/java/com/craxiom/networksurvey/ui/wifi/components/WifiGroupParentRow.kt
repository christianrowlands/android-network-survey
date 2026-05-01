package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.util.copyTextToClipboard
import com.craxiom.networksurvey.ui.wifi.model.WifiDisplayItem
import com.craxiom.networksurvey.util.toWifiSignalCategory

/**
 * Grouped-by-SSID parent row. Taps toggle expand/collapse only; individual APs navigate from
 * the child rows. Passpoint badge shows if ANY AP in the group is Passpoint.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WifiGroupParentRow(
    group: WifiDisplayItem.GroupParent,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (group.expanded) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val context = LocalContext.current
    val ssidClipLabel = stringResource(R.string.ssid_clip_label)
    val ssidCopiedToast = stringResource(R.string.ssid_copied)
    val onSsidLongPress: (() -> Unit)? = if (group.isHidden) {
        null
    } else {
        { copyTextToClipboard(context, ssidClipLabel, group.displaySsid, ssidCopiedToast) }
    }
    Row(
        modifier = modifier
            .background(bg)
            .combinedClickable(onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TitleRow(group, onClick = onToggle, onSsidLongPress = onSsidLongPress)
            MetaRow(group)
        }
        CappedSignalCellDensity { SignalCell(group) }
        Chevron(expanded = group.expanded)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TitleRow(
    group: WifiDisplayItem.GroupParent,
    onClick: () -> Unit,
    onSsidLongPress: (() -> Unit)?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (group.isHidden) {
            val hiddenDescription = stringResource(R.string.wifi_hidden_network_a11y)
            Text(
                text = stringResource(R.string.wifi_hidden_ssid_placeholder),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = WifiTokens.HiddenSsid,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = hiddenDescription },
            )
        } else {
            Text(
                text = group.displaySsid,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = WifiTokens.SsidAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onSsidLongPress,
                ),
            )
        }
        if (group.anyPasspoint) {
            NetworkTag(
                text = stringResource(R.string.wifi_tag_passpoint),
                variant = NetworkTagVariant.Passpoint,
            )
        }
        GroupCountPill(count = group.apCount)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(group: WifiDisplayItem.GroupParent) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        group.bands.forEach { band ->
            BandChip(band = band, channel = null)
        }
        if (group.encryptionLabel.isNotBlank()) {
            Text(
                text = group.encryptionLabel,
                style = TextStyle(fontSize = 12.sp),
                color = WifiTokens.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GroupCountPill(count: Int) {
    val text = stringResource(
        if (count == 1) R.string.wifi_ap_count_one else R.string.wifi_ap_count_other,
        count,
    )
    Text(
        text = text,
        style = TextStyle(fontSize = 11.sp),
        color = WifiTokens.InkMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun SignalCell(group: WifiDisplayItem.GroupParent) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.widthIn(min = 76.dp),
    ) {
        val color = group.bestRssi?.toWifiSignalCategory()?.color ?: LocalContentColor.current
        Text(
            text = if (group.bestRssi != null) "${group.bestRssi} dBm" else "--",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End
            ),
            color = color,
        )
        SignalMeter(rssi = group.bestRssi, width = 60.dp)
    }
}

@Composable
private fun Chevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "chevron",
    )
    val description = stringResource(
        if (expanded) R.string.wifi_group_collapse else R.string.wifi_group_expand
    )
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = description,
        tint = WifiTokens.InkMuted,
        modifier = Modifier.rotate(rotation),
    )
}
