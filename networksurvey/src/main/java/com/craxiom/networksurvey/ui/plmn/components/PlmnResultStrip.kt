package com.craxiom.networksurvey.ui.plmn.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsPulsingDot
import com.craxiom.networksurvey.ui.common.NsSortPill
import com.craxiom.networksurvey.ui.plmn.PlmnSortKey
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.CountFormat

/**
 * Top-of-list status strip for the PLMN lookup screen, on the same pulsing dot and sort pill as the
 * Signals lists' strip (`ui/signals/ListScanStrip`).
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
                .padding(horizontal = NsTheme.spacing.screenGutter - NsTheme.spacing.xxs, vertical = NsTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            ResultMeta(count = count)
            LiveIndicator(isLoading = isLoading, modifier = Modifier.weight(1f))
            NsSortPill(text = stringResource(R.string.plmn_sort_label, stringResource(sortLabel(sortKey))), onClick = onSortClick)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** "50 results", with the number in the stronger color and weight wherever the language puts it. */
@Composable
private fun ResultMeta(count: Int) {
    val number = CountFormat.format(count)
    val full = pluralStringResource(R.plurals.plmn_lookup_result_count, count, number)
    val start = full.indexOf(number)
    val numberStyle = SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    Text(
        text = buildAnnotatedString {
            append(full)
            if (start >= 0) addStyle(numberStyle, start, start + number.length)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LiveIndicator(isLoading: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s - NsTheme.spacing.xxs),
    ) {
        if (isLoading) {
            NsPulsingDot()
            Text(
                text = stringResource(R.string.plmn_lookup_live_label),
                style = NsTheme.textStyles.compact,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun sortLabel(sortKey: PlmnSortKey): Int = when (sortKey) {
    PlmnSortKey.MccMnc -> R.string.plmn_sort_mcc_mnc
    PlmnSortKey.Country -> R.string.plmn_sort_country
    PlmnSortKey.Provider -> R.string.plmn_sort_provider
}
