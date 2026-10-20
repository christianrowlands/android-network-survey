package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsEmptyState
import com.craxiom.networksurvey.ui.common.NsLazyPanelItem
import com.craxiom.networksurvey.ui.common.NsSortSheet
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.signals.ListScanStrip
import com.craxiom.networksurvey.ui.signals.SignalsStateMessage
import com.craxiom.networksurvey.ui.wifi.components.WifiGroupChildRow
import com.craxiom.networksurvey.ui.wifi.components.WifiGroupParentRow
import com.craxiom.networksurvey.ui.wifi.components.WifiModeTabs
import com.craxiom.networksurvey.ui.wifi.components.WifiRowA
import com.craxiom.networksurvey.ui.wifi.components.WifiScanFixes
import com.craxiom.networksurvey.ui.wifi.components.WifiThrottleNotice
import com.craxiom.networksurvey.ui.wifi.model.ThrottleWarning
import com.craxiom.networksurvey.ui.wifi.model.WifiDisplayItem
import com.craxiom.networksurvey.ui.wifi.model.WifiListMode
import com.craxiom.networksurvey.ui.wifi.model.WifiListUiState
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState

/** What the user can do on the Wi-Fi list; the stateful screen wires these up. */
interface WifiListActions : WifiScanFixes {
    fun onNetworkClick(bssid: String)
    fun onCopySsid(ssid: String)
    fun onCopyBssid(bssid: String)
    fun onToggleGroup(groupKey: String)
    fun onModeChanged(mode: WifiListMode)
    fun onSortSelected(index: Int)
    fun onTogglePause()
    fun onClearFilter()
    fun onOpenThrottleSettings(warning: ThrottleWarning)
    fun onDismissThrottle()
}

/**
 * The Wi-Fi list without its plumbing: the strip, then either what stops the scan (with the one
 * button that fixes it), or the throttling notice, the mode tabs and "No Wi-Fi networks found yet",
 * "No networks match", or the networks.
 *
 * @param stateWords The Signals state word and its spoken form ("Live view", "Recording"), shown
 *                   while the list is live.
 * @param permissionAccess Whether the permission can still be asked for or only granted in Settings.
 */
@Composable
fun WifiListContent(
    state: WifiListUiState,
    stateWords: Pair<String, String>,
    permissionAccess: PermissionAccess,
    actions: WifiListActions,
    modifier: Modifier = Modifier,
) {
    var sortSheetOpen by rememberSaveable { mutableStateOf(false) }
    val sortOptions = stringArrayResource(R.array.wifi_network_sort_options).toList()
    val ready = state.scanState == WifiScanState.READY
    val (status, statusSpoken) = stripStatus(state, stateWords)

    Column(modifier.fillMaxSize()) {
        ListScanStrip(
            // While the scan cannot run the rows are hidden, so counting them would mislead.
            counts = if (ready) stripCounts(state) else emptyList(),
            status = status,
            statusSpoken = statusSpoken,
            live = ready && !state.updatesPaused,
            sortLabel = sortOptions.getOrElse(state.sortIndex) { sortOptions.first() },
            onSortClick = { sortSheetOpen = true },
            paused = state.updatesPaused,
            onTogglePause = actions::onTogglePause,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (!ready) {
            ScanBlocked(state.scanState, permissionAccess, actions)
        } else {
            state.throttleWarning?.let { warning ->
                WifiThrottleNotice(
                    warning = warning,
                    onOpenSettings = { actions.onOpenThrottleSettings(warning) },
                    onDismiss = actions::onDismissThrottle,
                )
            }
            WifiModeTabs(mode = state.mode, onModeChanged = actions::onModeChanged)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            when {
                state.apCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NsEmptyState(text = stringResource(R.string.signals_wifi_empty), icon = R.drawable.ic_wifi)
                }

                state.items.isEmpty() -> SignalsStateMessage(
                    icon = R.drawable.ic_filter,
                    text = stringResource(R.string.wifi_filter_empty_state),
                    action = stringResource(R.string.wifi_filter_clear),
                    onAction = actions::onClearFilter,
                    prominent = false,
                )

                else -> NetworkList(state.items, actions)
            }
        }
    }

    if (sortSheetOpen) {
        NsSortSheet(
            options = sortOptions,
            selectedIndex = state.sortIndex,
            onSelected = actions::onSortSelected,
            onDismiss = { sortSheetOpen = false },
        )
    }
}

@Composable
private fun NetworkList(items: List<WifiDisplayItem>, actions: WifiListActions) {
    val listState = rememberLazyListState()
    val atTop by remember(listState) {
        derivedStateOf {
            !listState.isScrollInProgress && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    // A stronger network arriving above the first row would otherwise slide in out of view, since
    // the list keeps its first visible item; a user at the top stays at the top.
    if (atTop) {
        listState.requestScrollToItem(0)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        // No item animation: the design keeps motion for the Ready to Recording change alone.
        itemsIndexed(items = items, key = { _, item -> item.key }) { index, item ->
            NsLazyPanelItem(index = index, count = items.size) {
                when (item) {
                    is WifiDisplayItem.Flat -> WifiRowA(
                        ap = item.ap,
                        onClick = { actions.onNetworkClick(item.ap.bssid) },
                        onCopySsid = if (item.ap.ssidIsHidden) null else ({ actions.onCopySsid(item.ap.ssid) }),
                        onCopyBssid = { actions.onCopyBssid(item.ap.bssid) },
                    )

                    is WifiDisplayItem.GroupParent -> WifiGroupParentRow(
                        group = item,
                        onToggle = { actions.onToggleGroup(item.groupKey) },
                        onCopySsid = if (item.isHidden) null else ({ actions.onCopySsid(item.displaySsid) }),
                    )

                    is WifiDisplayItem.GroupChild -> WifiGroupChildRow(
                        ap = item.ap,
                        onClick = { actions.onNetworkClick(item.ap.bssid) },
                        onCopyBssid = { actions.onCopyBssid(item.ap.bssid) },
                    )
                }
            }
        }
    }
}

/** What stops the scan, with the one button that fixes it. */
@Composable
private fun ScanBlocked(scanState: WifiScanState, permissionAccess: PermissionAccess, actions: WifiListActions) {
    when (scanState) {
        WifiScanState.PERMISSION_NEEDED -> {
            val blocked = permissionAccess == PermissionAccess.BLOCKED
            SignalsStateMessage(
                icon = R.drawable.ic_info,
                text = stringResource(R.string.wifi_state_permission_body),
                action = stringResource(if (blocked) R.string.open_settings else R.string.survey_location_allow),
                onAction = if (blocked) actions::onOpenAppSettings else actions::onAllow,
            )
        }

        WifiScanState.LOCATION_OFF -> SignalsStateMessage(
            icon = R.drawable.ic_location_off,
            text = stringResource(R.string.wifi_state_location_off_body),
            action = stringResource(R.string.survey_location_turn_on),
            onAction = actions::onTurnOnLocation,
        )

        WifiScanState.WIFI_OFF -> SignalsStateMessage(
            icon = R.drawable.ic_wifi,
            text = stringResource(R.string.wifi_state_off_body),
            action = stringResource(R.string.survey_location_turn_on),
            onAction = actions::onTurnOnWifi,
        )

        WifiScanState.READY -> Unit
    }
}

/** "Scan #12" and "47 access points", or "3 of 47 access points" while filtered. */
@Composable
private fun stripCounts(state: WifiListUiState): List<String> = listOf(
    stringResource(R.string.wifi_list_scan_number, state.scanNumber),
    if (state.filter.isActive) {
        pluralStringResource(R.plurals.wifi_list_access_points_filtered, state.apCount, state.visibleApCount, state.apCount)
    } else {
        pluralStringResource(R.plurals.wifi_list_access_points, state.apCount, state.apCount)
    },
)

/**
 * The status on the scan strip and its spoken form: what stops the scan, then the user's pause,
 * then the Signals tab's state words (Live view, Recording, not in this survey, Paused) so all four
 * sub-tabs answer "is this saved?" the same way.
 */
@Composable
private fun stripStatus(state: WifiListUiState, stateWords: Pair<String, String>): Pair<String, String> {
    val resources = LocalResources.current
    val word = when (state.scanState) {
        WifiScanState.PERMISSION_NEEDED -> resources.getString(R.string.cellular_state_permission)
        WifiScanState.LOCATION_OFF -> resources.getString(R.string.survey_location_off)
        WifiScanState.WIFI_OFF -> resources.getString(R.string.wifi_state_off)
        WifiScanState.READY -> if (state.updatesPaused) resources.getString(R.string.list_updates_paused) else return stateWords
    }
    return word to word
}
