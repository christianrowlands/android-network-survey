package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsBandDot
import com.craxiom.networksurvey.ui.common.NsFilterPill
import com.craxiom.networksurvey.ui.common.NsListFilterSheet
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.wifi.model.WifiDisplayFilter
import com.craxiom.networksurvey.util.WifiBand

/**
 * The Wi-Fi list's display filter: search by SSID or BSSID and pick bands. Display only: it never
 * changes what is recorded (the SSID exclusion list, linked at the bottom, does that).
 */
@Composable
fun WifiFilterSheet(
    filter: WifiDisplayFilter,
    visibleApCount: Int,
    totalApCount: Int,
    onQueryChanged: (String) -> Unit,
    onBandToggled: (WifiBand) -> Unit,
    onClear: () -> Unit,
    onManageExclusionList: () -> Unit,
    onDismiss: () -> Unit,
) {
    NsListFilterSheet(
        title = stringResource(R.string.wifi_filter_title),
        searchHint = stringResource(R.string.wifi_filter_search_hint),
        query = filter.searchQuery,
        active = filter.isActive,
        summary = pluralStringResource(R.plurals.wifi_filter_showing_count, totalApCount, visibleApCount, totalApCount),
        onQueryChanged = onQueryChanged,
        onClear = onClear,
        onDismiss = onDismiss,
        pills = {
            WifiBand.entries.forEach { band ->
                NsFilterPill(
                    label = stringResource(band.labelRes),
                    selected = band in filter.selectedBands,
                    onToggle = { onBandToggled(band) },
                    leading = { NsBandDot(color = band.dotColor) },
                )
            }
        },
        footer = {
            NsTextAction(text = stringResource(R.string.wifi_filter_manage_exclusions), onClick = onManageExclusionList)
        },
    )
}
