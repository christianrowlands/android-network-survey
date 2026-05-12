package com.craxiom.networksurvey.ui.plmn.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.api.PlmnRecord
import com.craxiom.networksurvey.data.plmn.displayName
import com.craxiom.networksurvey.ui.plmn.PlmnGroup
import com.craxiom.networksurvey.ui.plmn.isoToFlag
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.util.copyTextToClipboard

private const val CHEVRON_ANIM_MS = 180

/**
 * Two-line result row for a single operator. Structure mirrors
 * `com.craxiom.networksurvey.ui.wifi.components.WifiRowA`:
 *
 *  1. Display name (brand or operator) in the SSID accent + MCC-MNC on the right.
 *  2. Flag emoji, country, ISO, optional TADIG. PLMN identifier on the right.
 *
 * Tap navigates to the PLMN details screen via [onRecordClick]; long-press copies the PLMN.
 * A trailing chevron icon indicates the row is navigable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlmnRow(
    record: PlmnRecord,
    onRecordClick: (PlmnRecord) -> Unit,
    modifier: Modifier = Modifier,
    indent: Boolean = false,
) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.plmn_lookup_clip_label)
    val toastMessage = stringResource(R.string.plmn_lookup_copied_toast)
    val openLabel = stringResource(R.string.plmn_row_open_details)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onRecordClick(record) },
                onLongClick = {
                    copyTextToClipboard(context, clipLabel, record.plmn, toastMessage)
                },
            )
            .padding(
                start = if (indent) 32.dp else 14.dp,
                end = 14.dp,
                top = if (indent) 10.dp else 12.dp,
                bottom = if (indent) 10.dp else 12.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LeftBlock(record = record, modifier = Modifier.weight(1f))
            RightBlock(mcc = record.mcc, mnc = record.mnc, plmn = record.plmn)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = openLabel,
                tint = WifiTokens.InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LeftBlock(record: PlmnRecord, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        TopNameRow(record = record)
        CountryRow(record = record)
    }
}

@Composable
private fun TopNameRow(record: PlmnRecord) {
    val brand = record.brand?.takeIf { it.isNotBlank() }
    val operator = record.operator?.takeIf { it.isNotBlank() }
    val primary = record.displayName()
    val secondary = if (brand != null && operator != null &&
        !brand.trim().equals(operator.trim(), ignoreCase = true)
    ) {
        operator
    } else {
        null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = primary,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = WifiTokens.SsidAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (secondary != null) {
            Text(
                text = secondary,
                style = TextStyle(fontSize = 11.5.sp),
                color = WifiTokens.InkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CountryRow(record: PlmnRecord) {
    val country = record.country?.takeIf { it.isNotBlank() } ?: record.region.orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = isoToFlag(record.iso),
            style = TextStyle(fontSize = 14.sp),
        )
        if (country.isNotBlank()) {
            Text(
                text = country,
                style = TextStyle(fontSize = 12.sp),
                color = WifiTokens.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        record.iso?.takeIf { it.isNotBlank() }?.let {
            FaintMonoDot()
            Text(
                text = it,
                style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                color = WifiTokens.InkFaint,
            )
        }
        record.tadig?.takeIf { it.isNotBlank() }?.let {
            FaintMonoDot()
            Text(
                text = it,
                style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                color = WifiTokens.InkFaint,
            )
        }
    }
}

@Composable
private fun FaintMonoDot() {
    Text(
        text = "·",
        style = TextStyle(fontSize = 11.sp),
        color = WifiTokens.InkFaint,
    )
}

@Composable
private fun RightBlock(mcc: String, mnc: String, plmn: String) {
    Column(
        modifier = Modifier.widthIn(min = 88.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$mcc-$mnc",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.plmn_lookup_plmn_label, plmn),
            style = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            color = WifiTokens.InkFaint,
        )
    }
}

/**
 * Expand/collapse parent row used when 2+ operators share an MCC+MNC. Tapping the row toggles
 * [expanded] via [onToggle]; the chevron rotates 90° using the same animation curve as
 * `WifiGroupParentRow.kt`. Long-press copies the shared PLMN identifier.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlmnGroupRow(
    group: PlmnGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRecordClick: (PlmnRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.plmn_lookup_clip_label)
    val toastMessage = stringResource(R.string.plmn_lookup_copied_toast)
    val expandLabel = stringResource(
        if (expanded) R.string.plmn_group_collapse else R.string.plmn_group_expand
    )
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = CHEVRON_ANIM_MS),
        label = "plmn-group-chevron",
    )

    val parentBg = if (expanded) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }

    // Parent and child rows are siblings; only the parent picks up the expanded-state bg so the
    // children retain the default surface background (matching `WifiGroupParentRow`).
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(parentBg)
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = {
                        copyTextToClipboard(
                            context,
                            clipLabel,
                            group.plmn,
                            toastMessage
                        )
                    }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GroupParentLeftBlock(group = group, modifier = Modifier.weight(1f))
            RightBlock(mcc = group.mcc, mnc = group.mnc, plmn = group.plmn)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = expandLabel,
                tint = WifiTokens.InkMuted,
                modifier = Modifier.rotate(rotation),
            )
        }
        if (expanded) {
            group.records.forEach { child ->
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                )
                PlmnRow(record = child, onRecordClick = onRecordClick, indent = true)
            }
        }
    }
}

@Composable
private fun GroupParentLeftBlock(group: PlmnGroup, modifier: Modifier = Modifier) {
    val country = group.country?.takeIf { it.isNotBlank() } ?: group.region.orEmpty()
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = isoToFlag(group.iso),
                style = TextStyle(fontSize = 14.sp),
            )
            Text(
                text = country.ifBlank { "—" },
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = WifiTokens.SsidAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            OperatorsCountPill(group.records.size)
        }
    }
}

@Composable
private fun OperatorsCountPill(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.plmn_lookup_n_operators, count),
            style = TextStyle(fontSize = 11.sp),
            color = WifiTokens.InkMuted,
        )
    }
}
