package com.craxiom.networksurvey.ui.plmn.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.plmn.PlmnSortKey
import com.craxiom.networksurvey.ui.theme.WifiTokens

private const val PULSE_DURATION_MS = 900
private const val PULSE_MIN_ALPHA = 0.35f

/**
 * Top-of-list status strip for the PLMN lookup screen, modelled on
 * `com.craxiom.networksurvey.ui.wifi.components.ScanStatusStrip`.
 *
 * Layout: "Results: N" (left) / pulsing live dot + label (middle) / sort pill (right).
 * The pulse only renders when [isLoading] or there is active input; callers gate the
 * strip's visibility based on whether any input is present.
 */
@Composable
fun PlmnResultStrip(
    count: Int,
    isLoading: Boolean,
    sortKey: PlmnSortKey,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ResultMeta(count = count)
            LiveIndicator(isLoading = isLoading, modifier = Modifier.weight(1f))
            SortPill(sortKey = sortKey, onClick = onSortClick)
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
        )
    }
}

@Composable
private fun ResultMeta(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.plmn_lookup_results_meta_label),
            style = TextStyle(fontSize = 12.sp),
            color = WifiTokens.InkMuted,
        )
        Text(
            text = count.toString(),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LiveIndicator(isLoading: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isLoading) {
            PulsingStatusDot()
            Text(
                text = stringResource(R.string.plmn_lookup_live_label),
                style = TextStyle(fontSize = 13.sp),
                color = WifiTokens.InkMuted,
            )
        }
    }
}

/**
 * 7 dp pulsing green dot. Visual / animation parameters duplicated from
 * `ScanStatusStrip.PulsingStatusDot` so the wifi screen does not have to change.
 */
@Composable
private fun PulsingStatusDot() {
    val transition = rememberInfiniteTransition(label = "plmn-scan-dot")
    val alpha by transition.animateFloat(
        initialValue = PULSE_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "plmn-scan-dot-alpha",
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(50))
            .background(WifiTokens.SignalStrong)
    )
}

@Composable
private fun SortPill(sortKey: PlmnSortKey, onClick: () -> Unit) {
    val labelRes = when (sortKey) {
        PlmnSortKey.MccMnc -> R.string.plmn_sort_mcc_mnc
        PlmnSortKey.Country -> R.string.plmn_sort_country
        PlmnSortKey.Provider -> R.string.plmn_sort_provider
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .height(32.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.plmn_sort_label, stringResource(labelRes)),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "▾",
            style = TextStyle(fontSize = 10.sp),
            color = WifiTokens.InkFaint,
        )
    }
}
