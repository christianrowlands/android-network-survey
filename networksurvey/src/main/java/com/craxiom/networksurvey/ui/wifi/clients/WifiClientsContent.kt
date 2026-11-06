package com.craxiom.networksurvey.ui.wifi.clients

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsChevron
import com.craxiom.networksurvey.ui.common.NsEmptyState
import com.craxiom.networksurvey.ui.common.NsLazyPanelItem
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlinx.coroutines.delay

private const val CHEVRON_OPEN_DEGREES = 90f

/** How long the clients keep their order after the finger leaves the list or it stops scrolling. */
private const val ORDER_HOLD_AFTER_TOUCH_MS = 1_500L

/** What the user can do in the Clients mode; the Wi-Fi list screen wires these up. */
interface WifiClientsActions {
    fun onClientsViewChanged(view: WifiClientsView) {}
    fun onToggleProbedNetwork(ssid: String) {}
    fun onClientClick(mac: String) {}
}

/**
 * The Clients mode of Signals > Wi-Fi, under the mode row: "By client | By network looked for",
 * then the clients (strongest first) or the network names they looked for (most clients first; a
 * tap opens the row to list its clients), or a short note while nothing was heard yet.
 *
 * @param ssidOf The SSID the Wi-Fi list knows for a BSSID, or null, so a client names its network.
 */
@Composable
fun WifiClientsContent(state: WifiClientsUiState, ssidOf: (String) -> String?, actions: WifiClientsActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        val views = WifiClientsView.entries
        NsSegmentedToggle(
            options = listOf(
                SegmentedOption(stringResource(R.string.wifi_clients_by_client)),
                SegmentedOption(stringResource(R.string.wifi_clients_by_network)),
            ),
            selectedIndex = views.indexOf(state.view),
            onSelected = { actions.onClientsViewChanged(views[it]) },
            segmentMinHeight = NsTheme.spacing.touchTarget,
            modifier = Modifier
                .nsContentWidth()
                .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.s),
        )
        when {
            state.clients.isEmpty() -> EmptyNote(stringResource(R.string.wifi_clients_empty, state.scoutName))
            state.view == WifiClientsView.BY_CLIENT -> ClientList(state, ssidOf, actions)
            state.networks.isEmpty() -> EmptyNote(stringResource(R.string.wifi_clients_networks_empty))
            else -> NetworkList(state, actions)
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NsEmptyState(text = text, icon = R.drawable.ic_devices)
    }
}

/**
 * The clients, strongest first. While a finger is on the list, while it scrolls and for a moment
 * after, the rows keep their order (their values still update), so the row the user aims for
 * doesn't move away under the finger.
 */
@Composable
private fun ClientList(state: WifiClientsUiState, ssidOf: (String) -> String?, actions: WifiClientsActions) {
    val listState = rememberLazyListState()
    var touching by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val busy = touching || listState.isScrollInProgress
    LaunchedEffect(busy) {
        if (busy) {
            holding = true
        } else {
            delay(ORDER_HOLD_AFTER_TOUCH_MS)
            holding = false
        }
    }
    val latest = state.clients
    val heldOrder = remember(holding) { if (holding) latest.map { it.mac } else null }
    val clients = heldOrder?.let { WifiClientsMapper.inOrder(latest, it) } ?: latest
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) touching = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                }
            },
    ) {
        itemsIndexed(items = clients, key = { _, client -> client.mac }) { index, client ->
            NsLazyPanelItem(index = index, count = clients.size) {
                WifiClientRow(client, state.nowMs, ssidOf, onClick = { actions.onClientClick(client.mac) })
            }
        }
    }
}

@Composable
private fun NetworkList(state: WifiClientsUiState, actions: WifiClientsActions) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(items = state.networks, key = { _, network -> network.ssid }) { index, network ->
            NsLazyPanelItem(index = index, count = state.networks.size) {
                val expanded = network.ssid in state.expandedNetworks
                ProbedNetworkRow(network, expanded, onToggle = { actions.onToggleProbedNetwork(network.ssid) })
                if (expanded) {
                    network.clients.forEach { client ->
                        WifiClientCompactRow(
                            client = client,
                            onClick = { actions.onClientClick(client.mac) },
                            startPadding = NsTheme.spacing.screenGutter + NsTheme.spacing.l,
                        )
                    }
                }
            }
        }
    }
}

/** A network name clients looked for, with how many did; a tap opens or closes its clients. */
@Composable
private fun ProbedNetworkRow(network: ProbedNetwork, expanded: Boolean, onToggle: () -> Unit) {
    val count = pluralStringResource(R.plurals.wifi_clients_looked_for_by, network.clients.size, network.clients.size)
    val spoken = listOf(network.ssid, count).joinToString(stringResource(R.string.wifi_client_list_separator))
    val rotation by animateFloatAsState(if (expanded) CHEVRON_OPEN_DEGREES else 0f, label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = expanded, role = Role.Button, onValueChange = { onToggle() })
            .semantics { contentDescription = spoken }
            .heightIn(min = NsTheme.spacing.touchTarget)
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
            Text(
                text = network.ssid,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NsChevron(Modifier.rotate(rotation))
    }
}
