package com.craxiom.networksurvey.ui.wifi.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.ASignalChartViewModel
import com.craxiom.networksurvey.ui.common.NsSignalHistoryCard
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.theme.NsTheme

/** What the Client details page needs from the Wi-Fi list it was opened from. */
interface WifiClientDetailsActions {
    fun onBack()

    /** @return The SSID the Wi-Fi list heard for [bssid], or null. */
    fun ssidOf(bssid: String): String? = null

    /** @return True when [bssid] is in the Wi-Fi list, so its details can open. */
    fun canOpenNetwork(bssid: String): Boolean = false

    fun onOpenNetwork(bssid: String) {}
}

/**
 * The Client details page: the hero, then (each only when there is something to show) the access
 * point it talks to, the networks it looked for, the kinds of frames it was heard sending, and its
 * signal over time.
 */
@Composable
fun WifiClientDetailsScreen(state: WifiClientDetailsState, chart: ASignalChartViewModel?, actions: WifiClientDetailsActions) {
    Scaffold(
        topBar = { TitleBar(stringResource(R.string.wifi_client_details_title), onBackClick = actions::onBack) },
    ) { insetPadding ->
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(NsTheme.spacing.panelPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelPaddingVertical),
            modifier = Modifier.padding(insetPadding).fillMaxSize().nsContentWidth(),
        ) {
            item(key = "hero") { WifiClientHeroCard(state) }
            val client = state.client ?: return@LazyColumn
            client.bssid?.let { bssid ->
                item(key = "connected") {
                    WifiClientConnectedCard(
                        bssid = bssid,
                        ssid = actions.ssidOf(bssid),
                        onOpenNetwork = if (actions.canOpenNetwork(bssid)) ({ actions.onOpenNetwork(bssid) }) else null,
                    )
                }
            }
            if (client.probedSsids.isNotEmpty()) item(key = "looked") { WifiClientLookedForCard(client) }
            if (client.frameTypes.isNotEmpty()) item(key = "seen") { WifiClientSeenInCard(client) }
            item(key = "signal") { NsSignalHistoryCard(title = stringResource(R.string.wifi_client_signal_title), chart = chart) }
        }
    }
}
