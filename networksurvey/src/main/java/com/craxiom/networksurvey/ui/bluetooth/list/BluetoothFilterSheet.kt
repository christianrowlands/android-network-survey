package com.craxiom.networksurvey.ui.bluetooth.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothDisplayFilter
import com.craxiom.networksurvey.ui.common.NsFilterPill
import com.craxiom.networksurvey.ui.common.NsListFilterSheet

/**
 * The Bluetooth list's filter, opened from the Signals app bar: search by name, address or company,
 * and the Named only and Trackers only toggles. Display only; it never changes what is recorded.
 */
@Composable
fun BluetoothFilterSheet(
    filter: BluetoothDisplayFilter,
    visibleCount: Int,
    totalCount: Int,
    onQueryChanged: (String) -> Unit,
    onNamedOnlyChanged: (Boolean) -> Unit,
    onTrackersOnlyChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    NsListFilterSheet(
        title = stringResource(R.string.bluetooth_filter_title),
        searchHint = stringResource(R.string.bluetooth_filter_search_hint),
        query = filter.query,
        active = filter.isActive,
        summary = pluralStringResource(R.plurals.bluetooth_filter_showing_count, totalCount, visibleCount, totalCount),
        onQueryChanged = onQueryChanged,
        onClear = onClear,
        onDismiss = onDismiss,
        pills = {
            NsFilterPill(
                label = stringResource(R.string.bluetooth_filter_named_only),
                selected = filter.namedOnly,
                onToggle = onNamedOnlyChanged,
            )
            NsFilterPill(
                label = stringResource(R.string.bluetooth_filter_trackers_only),
                selected = filter.trackersOnly,
                onToggle = onTrackersOnlyChanged,
            )
        },
    )
}
