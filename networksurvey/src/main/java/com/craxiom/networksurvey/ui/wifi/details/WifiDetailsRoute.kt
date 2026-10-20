package com.craxiom.networksurvey.ui.wifi.details

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.common.PermissionRequest
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.common.openAppSettings
import com.craxiom.networksurvey.ui.common.openLocationSettings
import com.craxiom.networksurvey.ui.common.openWifiSettings
import com.craxiom.networksurvey.ui.common.rememberPermissionRequest
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.signals.SignalsSubTab
import com.craxiom.networksurvey.ui.signals.SignalsViewModel
import com.craxiom.networksurvey.ui.watchlist.WatchlistAddHelper
import com.craxiom.networksurvey.ui.watchlist.WatchlistAddSheet
import com.craxiom.networksurvey.ui.wifi.model.WifiEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Wi-Fi details route: binds to the survey service while the page shows, re-checks the
 * permission, location and Wi-Fi on every resume, pauses the chart while away, and owns the
 * watchlist sheet and the confirmations. Shows nothing without a network (a route opened without one).
 */
@Composable
fun WifiDetailsRoute(network: WifiNetwork?, onBack: () -> Unit) {
    if (network == null) return
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val viewModel: WifiDetailsViewModel = viewModel()
    val sharedViewModel: SharedViewModel = viewModel(viewModelStoreOwner = activity)
    val signalsViewModel: SignalsViewModel = viewModel(viewModelStoreOwner = activity)
    // Only the first call counts, so a rotation keeps the chart's history.
    LaunchedEffect(viewModel, network) { viewModel.show(network) }

    ServiceConnectionHandler(viewModel::onServiceChanged)
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        viewModel.setChartPaused(false)
        onPauseOrDispose { viewModel.setChartPaused(true) }
    }
    val permission = rememberPermissionRequest(WifiEnvironment.PERMISSIONS, onAnswered = viewModel::refresh) {
        viewModel.refresh()
    }

    val rssi by viewModel.rssi.collectAsStateWithLifecycle()
    val intervals by signalsViewModel.intervals.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val excluded by viewModel.excluded.collectAsStateWithLifecycle()
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    var watchlistSheetOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    WifiDetailsScreen(
        network = network,
        page = WifiDetailsPageState(
            currentRssi = rssi.takeIf { it != UNKNOWN_RSSI }?.toInt(),
            scanRateSeconds = intervals.getValue(SignalsSubTab.WIFI),
            scanState = scanState,
            excluded = excluded,
            watched = watched,
        ),
        permissionAccess = permission.access,
        chart = viewModel.chart,
        actions = remember(viewModel, permission) {
            RouteActions(context, viewModel, sharedViewModel, permission, onBack) { watchlistSheetOpen = true }
        },
    )

    if (watchlistSheetOpen) {
        WatchlistAddSheet(
            onAdd = { label, ssid, bssid -> scope.addToWatchlist(context, label, ssid, bssid) },
            onDismiss = { watchlistSheetOpen = false },
            initialSsid = network.ssid,
            initialBssid = network.bssid,
        )
    }
}

/** Adds a watchlist entry and turns watchlist alerts on, since adding from here is an explicit opt-in. */
private fun CoroutineScope.addToWatchlist(context: Context, label: String, ssid: String?, bssid: String?) = launch {
    when (val result = WatchlistAddHelper.addEntry(context, label, ssid, bssid)) {
        is WatchlistAddHelper.AddResult.Added -> {
            val justEnabled = WatchlistAddHelper.enableWatchlistIfDisabled(context)
            val message = if (justEnabled) {
                context.getString(R.string.watchlist_added_alerts_enabled, result.label)
            } else {
                context.getString(R.string.watchlist_added, result.label)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        WatchlistAddHelper.AddResult.Duplicate ->
            Toast.makeText(context, context.getString(R.string.watchlist_validation_duplicate), Toast.LENGTH_SHORT).show()

        WatchlistAddHelper.AddResult.Invalid -> Unit
    }
}

/** Wires [WifiDetailsActions] to the view models, the permission request and the system screens. */
private class RouteActions(
    private val context: Context,
    private val viewModel: WifiDetailsViewModel,
    private val sharedViewModel: SharedViewModel,
    private val permission: PermissionRequest,
    private val onBack: () -> Unit,
    private val onOpenWatchlistSheet: () -> Unit,
) : WifiDetailsActions {

    override fun onBack() = onBack.invoke()

    override fun onToggleExcluded() {
        val ssid = viewModel.network.value?.ssid ?: return
        when (viewModel.toggleExcluded()) {
            ExclusionChange.ADDED -> toast(context.getString(R.string.ssid_added_to_exclusion, ssid))
            ExclusionChange.REMOVED -> toast(context.getString(R.string.ssid_removed_from_exclusion, ssid))
            ExclusionChange.LIST_FULL -> toast(context.getString(R.string.exclusion_list_full), Toast.LENGTH_LONG)
            ExclusionChange.UNCHANGED -> Unit
        }
    }

    override fun onWatchlistClick() {
        if (viewModel.watched.value) sharedViewModel.triggerNavigationToWatchlist() else onOpenWatchlistSheet()
    }

    override fun onOpenSettings() = sharedViewModel.triggerNavigationToSettings()

    override fun onAllow() = permission.request()

    override fun onOpenAppSettings() = openAppSettings(context)

    override fun onTurnOnLocation() = openLocationSettings(context)

    override fun onTurnOnWifi() = openWifiSettings(context)

    private fun toast(message: String, length: Int = Toast.LENGTH_SHORT) = Toast.makeText(context, message, length).show()
}
