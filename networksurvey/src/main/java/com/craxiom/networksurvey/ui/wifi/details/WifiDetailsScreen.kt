package com.craxiom.networksurvey.ui.wifi.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.ASignalChartViewModel
import com.craxiom.networksurvey.ui.common.NsSignalHistoryCard
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.components.WifiBlockedNotice
import com.craxiom.networksurvey.ui.wifi.components.WifiCapabilitiesCard
import com.craxiom.networksurvey.ui.wifi.components.WifiDetailsHeroCard
import com.craxiom.networksurvey.ui.wifi.components.WifiRadioCard
import com.craxiom.networksurvey.ui.wifi.components.WifiScanFixes
import com.craxiom.networksurvey.ui.wifi.components.WifiSurveyDataCard
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState

/** What the user can do on the Wi-Fi details page; the route wires these up. */
interface WifiDetailsActions : WifiScanFixes {
    fun onBack()
    fun onToggleExcluded()
    fun onWatchlistClick()
    fun onOpenSettings()
}

/** What the Wi-Fi details page shows besides the network itself. */
data class WifiDetailsPageState(
    val currentRssi: Int?,
    val scanRateSeconds: Int,
    val scanState: WifiScanState,
    val excluded: Boolean,
    val watched: Boolean,
)

/**
 * The Wi-Fi details page: when the phone cannot scan, a notice with the one fix; then five cards
 * (hero, radio, signal history, capabilities, survey data).
 */
@Composable
fun WifiDetailsScreen(
    network: WifiNetwork,
    page: WifiDetailsPageState,
    permissionAccess: PermissionAccess,
    chart: ASignalChartViewModel?,
    actions: WifiDetailsActions,
) {
    Scaffold(
        topBar = { TitleBar(stringResource(R.string.wifi_details_title), onBackClick = actions::onBack) },
    ) { insetPadding ->
        Column(Modifier.padding(insetPadding)) {
            // Pinned above the cards: an item inserted before the first visible one would scroll
            // in out of view.
            if (page.scanState != WifiScanState.READY) {
                WifiBlockedNotice(page.scanState, permissionAccess, actions, Modifier.padding(horizontal = NsTheme.spacing.screenGutter))
            }
            LazyColumn(
                state = rememberLazyListState(),
                contentPadding = PaddingValues(NsTheme.spacing.panelPaddingVertical),
                verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelPaddingVertical),
                modifier = Modifier.fillMaxSize().nsContentWidth(),
            ) {
                detailsCards(network, page, chart, actions)
            }
        }
    }
}

private fun LazyListScope.detailsCards(
    network: WifiNetwork,
    page: WifiDetailsPageState,
    chart: ASignalChartViewModel?,
    actions: WifiDetailsActions,
) {
    item(key = "hero") {
        WifiDetailsHeroCard(network = network, currentRssi = page.currentRssi)
    }
    item(key = "radio") {
        WifiRadioCard(network = network)
    }
    item(key = "signal") {
        NsSignalHistoryCard(title = stringResource(R.string.wifi_details_signal_last_2_min), chart = chart)
    }
    item(key = "capabilities") {
        WifiCapabilitiesCard(capabilities = network.capabilities)
    }
    item(key = "survey") {
        WifiSurveyDataCard(
            isExcluded = page.excluded,
            isWatched = page.watched,
            hiddenSsid = network.ssid.isEmpty(),
            scanRateSeconds = page.scanRateSeconds,
            onToggle = actions::onToggleExcluded,
            onWatchlistClick = actions::onWatchlistClick,
            onNavigateToSettings = actions::onOpenSettings,
        )
    }
}
