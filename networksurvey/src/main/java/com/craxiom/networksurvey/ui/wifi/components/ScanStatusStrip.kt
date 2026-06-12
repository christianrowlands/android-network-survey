package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Top chrome for the Wi-Fi list: "Scan #N · APs: M", scan status with pulsing dot, and the
 * sort + pause controls. When the display filter is active the AP count switches to
 * "APs: M of N" so it is always visible that some networks are hidden.
 */
@Composable
fun ScanStatusStrip(
    scanNumber: Int,
    apCount: Int,
    visibleApCount: Int,
    filterActive: Boolean,
    scanStatusId: Int,
    isPaused: Boolean,
    sortIndex: Int,
    onSortClick: () -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(min = 72.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.wifi_list_scan_number, scanNumber),
                style = TextStyle(fontSize = 13.sp),
                color = WifiTokens.InkMuted,
            )
            Text(
                text = if (filterActive) {
                    stringResource(R.string.wifi_list_ap_count_filtered, visibleApCount, apCount)
                } else {
                    stringResource(R.string.wifi_list_ap_count, apCount)
                },
                style = TextStyle(fontSize = 13.sp),
                color = WifiTokens.InkMuted,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isPaused) {
                PulsingStatusDot()
            }
            Text(
                text = stringResource(scanStatusId),
                style = TextStyle(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        SortPill(sortIndex = sortIndex, onClick = onSortClick)
        PausePlayButton(isPaused = isPaused, onClick = onTogglePause)
    }
}

@Composable
private fun PulsingStatusDot() {
    val transition = rememberInfiniteTransition(label = "scan-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan-dot-alpha",
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(WifiTokens.SignalStrong)
            .alpha(alpha),
    )
}

@Composable
private fun SortPill(sortIndex: Int, onClick: () -> Unit) {
    val options = stringArrayResource(R.array.wifi_network_sort_options)
    val label = options.getOrNull(sortIndex) ?: options.firstOrNull().orEmpty()
    val text = stringResource(R.string.wifi_sort_pill_label, label)
    val a11y = stringResource(R.string.wifi_sort_pill_content_description, label)
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics {
                role = Role.Button
                contentDescription = a11y
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PausePlayButton(isPaused: Boolean, onClick: () -> Unit) {
    val description =
        stringResource(R.string.content_description_pause_wifi_network_scan_ui_updates)
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                if (isPaused) R.drawable.ic_play else R.drawable.ic_pause
            ),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

