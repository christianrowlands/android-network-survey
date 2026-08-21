package com.craxiom.networksurvey.ui.wifi

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.fragments.WifiDetailsFragment
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.services.watchlist.WatchlistMatcher
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.watchlist.WatchlistAddHelper
import com.craxiom.networksurvey.ui.watchlist.WatchlistAddSheet
import com.craxiom.networksurvey.ui.wifi.components.WifiCapabilitiesCard
import com.craxiom.networksurvey.ui.wifi.components.WifiDetailsHeroCard
import com.craxiom.networksurvey.ui.wifi.components.WifiRadioCard
import com.craxiom.networksurvey.ui.wifi.components.WifiSignalCard
import com.craxiom.networksurvey.ui.wifi.components.WifiSurveyDataCard
import com.craxiom.networksurvey.ui.wifi.model.WifiDetailsViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val cardGap = 14.dp

/**
 * The redesigned Wi-Fi Details screen. Five stacked cards: hero, radio, signal history,
 * capabilities, survey data. Data and side-effect patterns (service flow, exclusion manager,
 * toasts) carry over from the legacy implementation unchanged.
 */
@Composable
internal fun WifiDetailsScreen(
    viewModel: WifiDetailsViewModel,
    wifiDetailsFragment: WifiDetailsFragment,
) {
    val rssi by viewModel.rssiFlow.collectAsStateWithLifecycle()
    val scanRate by viewModel.scanRate.collectAsStateWithLifecycle()
    val currentRssi = if (rssi == UNKNOWN_RSSI) null else rssi.toInt()

    Scaffold(
        topBar = {
            TitleBar(
                stringResource(R.string.wifi_details_title),
                onBackClick = { wifiDetailsFragment.navigateBack() },
            )
        },
    ) { insetPadding ->
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(cardGap),
            modifier = Modifier
                .fillMaxSize()
                .padding(insetPadding),
        ) {
            detailsCards(viewModel, currentRssi, scanRate, wifiDetailsFragment)
        }
    }
}

private fun LazyListScope.detailsCards(
    viewModel: WifiDetailsViewModel,
    currentRssi: Int?,
    scanRateSeconds: Int,
    wifiDetailsFragment: WifiDetailsFragment,
) {
    item(key = "hero") {
        WifiDetailsHeroCard(
            network = viewModel.wifiNetwork,
            currentRssi = currentRssi,
        )
    }
    item(key = "radio") {
        WifiRadioCard(network = viewModel.wifiNetwork)
    }
    item(key = "signal") {
        WifiSignalCard(viewModel = viewModel)
    }
    item(key = "capabilities") {
        WifiCapabilitiesCard(capabilities = viewModel.wifiNetwork.capabilities)
    }
    item(key = "survey") {
        SurveyDataCardContainer(
            viewModel = viewModel,
            scanRateSeconds = scanRateSeconds,
            wifiDetailsFragment = wifiDetailsFragment,
        )
    }
}

@Composable
private fun SurveyDataCardContainer(
    viewModel: WifiDetailsViewModel,
    scanRateSeconds: Int,
    wifiDetailsFragment: WifiDetailsFragment,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service by wifiDetailsFragment.serviceFlow.collectAsStateWithLifecycle()
    val wifiNetwork = viewModel.wifiNetwork
    val hiddenSsid = wifiNetwork.ssid.isEmpty()
    var isExcluded by remember { mutableStateOf(false) }
    var showWatchlistSheet by remember { mutableStateOf(false) }

    // Membership check, not an alerting check: disabled entries still count, so match against ALL
    // entries. The flow is remembered so the DAO lookup and mapping are not rebuilt each recomposition.
    val watchedFlow = remember(wifiNetwork) {
        SurveyDatabase.getInstance(context).watchlistDao().observeAll().map { entries ->
            entries.any { WatchlistMatcher.matches(it, wifiNetwork.ssid, wifiNetwork.bssid) }
        }
    }
    val isWatched by watchedFlow.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(service) {
        val exclusionManager = service?.getSsidExclusionManager()
        isExcluded = exclusionManager?.isExcluded(wifiNetwork.ssid) ?: false
    }

    WifiSurveyDataCard(
        isExcluded = isExcluded,
        isWatched = isWatched,
        hiddenSsid = hiddenSsid,
        scanRateSeconds = scanRateSeconds,
        onNavigateToSettings = { wifiDetailsFragment.navigateToSettings() },
        onWatchlistClick = {
            if (isWatched) {
                wifiDetailsFragment.navigateToWatchlist()
            } else {
                showWatchlistSheet = true
            }
        },
        onToggle = {
            scope.launch {
                val exclusionManager = service?.getSsidExclusionManager() ?: return@launch
                val ssid = viewModel.wifiNetwork.ssid

                if (isExcluded) {
                    exclusionManager.removeExcludedSsid(ssid)
                    isExcluded = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.ssid_removed_from_exclusion, ssid),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    if (exclusionManager.addExcludedSsid(ssid)) {
                        isExcluded = true
                        Toast.makeText(
                            context,
                            context.getString(R.string.ssid_added_to_exclusion, ssid),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else if (exclusionManager.isAtMaxCapacity()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.exclusion_list_full),
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        // `addExcludedSsid` returned false for a reason other than capacity.
                        // The most likely cause is a stale Compose state that thinks the SSID
                        // isn't excluded when it already is. Resync so the pill reflects reality.
                        isExcluded = exclusionManager.isExcluded(ssid)
                    }
                }
            }
        },
    )

    if (showWatchlistSheet) {
        WatchlistAddSheet(
            onAdd = { label, ssid, bssid ->
                scope.launch {
                    when (val result = WatchlistAddHelper.addEntry(context, label, ssid, bssid)) {
                        is WatchlistAddHelper.AddResult.Added -> {
                            // Adding from here is an explicit opt-in, so make sure alerts can
                            // actually fire (mirrors the deep-link import's force-enable).
                            val justEnabled = WatchlistAddHelper.enableWatchlistIfDisabled(context)
                            val message = if (justEnabled) {
                                context.getString(
                                    R.string.watchlist_added_alerts_enabled,
                                    result.label,
                                )
                            } else {
                                context.getString(R.string.watchlist_added, result.label)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }

                        WatchlistAddHelper.AddResult.Duplicate -> Toast.makeText(
                            context,
                            context.getString(R.string.watchlist_validation_duplicate),
                            Toast.LENGTH_SHORT,
                        ).show()

                        WatchlistAddHelper.AddResult.Invalid -> Unit
                    }
                }
            },
            onDismiss = { showWatchlistSheet = false },
            initialSsid = wifiNetwork.ssid,
            initialBssid = wifiNetwork.bssid,
        )
    }
}
