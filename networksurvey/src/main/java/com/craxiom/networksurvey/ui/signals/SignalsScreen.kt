package com.craxiom.networksurvey.ui.signals

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.ui.GnssEnvironmentViewModel
import com.craxiom.networksurvey.gpstest.ui.GnssScanState
import com.craxiom.networksurvey.gpstest.ui.GnssScreen
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SignalInfoViewModel
import com.craxiom.networksurvey.ui.bluetooth.list.BluetoothFilterSheet
import com.craxiom.networksurvey.ui.bluetooth.list.BluetoothListScreen
import com.craxiom.networksurvey.ui.bluetooth.list.BluetoothListViewModel
import com.craxiom.networksurvey.ui.cellular.details.CellularDetailsScreen
import com.craxiom.networksurvey.ui.cellular.details.CellularDetailsViewModel
import com.craxiom.networksurvey.ui.cellular.details.model.CellularScanState
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.main.NavOption
import com.craxiom.networksurvey.ui.main.SurveyStatusViewModel
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.main.appbar.TabTopBar
import com.craxiom.networksurvey.ui.main.openWifiSpectrum
import com.craxiom.networksurvey.ui.wifi.components.WifiFilterSheet
import com.craxiom.networksurvey.ui.wifi.WifiListScreen
import com.craxiom.networksurvey.ui.wifi.model.WifiListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * The Signals tab: the live Cellular, Wi-Fi, Bluetooth and GNSS views under one sub-tab row, with
 * a strip saying whether what is on screen is being recorded.
 *
 * The sub-tab is rendered with a `when` block rather than a nested NavHost: a nested NavHost takes
 * over Back once its stack grows, and keeps nothing for fragments anyway. Each branch calls its own
 * host, inside `key`, so a fragment never inherits another fragment type's container.
 *
 * @param onOpenMap Selects the Map tab (the Cellular map action).
 */
@Composable
fun SignalsScreen(
    subTab: SignalsSubTab,
    onSubTabChange: (SignalsSubTab) -> Unit,
    onOpenMap: () -> Unit,
    mainNavController: NavHostController,
) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    val signalsViewModel: SignalsViewModel = viewModel(viewModelStoreOwner = activity)
    val statusViewModel: SurveyStatusViewModel = viewModel(viewModelStoreOwner = activity)
    val wifiListViewModel: WifiListViewModel = viewModel(viewModelStoreOwner = activity)
    val bluetoothListViewModel: BluetoothListViewModel = viewModel(viewModelStoreOwner = activity)
    val cellularViewModel: CellularDetailsViewModel = viewModel(viewModelStoreOwner = activity)
    val gnssEnvironment: GnssEnvironmentViewModel = viewModel(viewModelStoreOwner = activity)

    LifecycleResumeEffect(signalsViewModel) {
        signalsViewModel.refreshIntervals()
        onPauseOrDispose { }
    }

    val status by statusViewModel.status.collectAsStateWithLifecycle()
    val intervals by signalsViewModel.intervals.collectAsStateWithLifecycle()
    val gnssPage by signalsViewModel.gnssPage.collectAsStateWithLifecycle()
    val viewedSim by signalsViewModel.viewedSim.collectAsStateWithLifecycle()
    val gnssFilterActive by signalsViewModel.gnssFilterActive.collectAsStateWithLifecycle()
    // Only collect the Wi-Fi list while it shows, so its scans don't recompose the other sub-tabs.
    val wifiState = if (subTab == SignalsSubTab.WIFI) wifiListViewModel.uiState.collectAsStateWithLifecycle().value else null
    val bluetoothState = if (subTab == SignalsSubTab.BLUETOOTH) {
        bluetoothListViewModel.uiState.collectAsStateWithLifecycle().value
    } else {
        null
    }

    // What stops the Cellular or GNSS view replaces the strip's state word, as the lists' strips do.
    val blockedWord = when (subTab) {
        SignalsSubTab.CELLULAR -> cellularBlockedWord(cellularViewModel.uiState.collectAsStateWithLifecycle().value.scanState)
        SignalsSubTab.GNSS -> gnssBlockedWord(gnssEnvironment.scanState.collectAsStateWithLifecycle().value)
        else -> null
    }

    var dialog by remember { mutableStateOf(SignalsDialog.NONE) }

    Column(Modifier.fillMaxSize()) {
        TabTopBar(
            title = stringResource(R.string.nav_signals),
            actions = signalsActions(subTab, gnssPage).map { action ->
                val filterActive = wifiState?.filter?.isActive == true || bluetoothState?.filter?.isActive == true ||
                    (subTab == SignalsSubTab.GNSS && gnssFilterActive)
                appBarAction(action, filterActive) {
                    when (action) {
                        SignalsAction.OPEN_MAP -> onOpenMap()
                        SignalsAction.WIFI_SPECTRUM -> openWifiSpectrum(mainNavController, seeded = true)
                        SignalsAction.WIFI_FILTER -> dialog = SignalsDialog.WIFI_FILTER
                        SignalsAction.BLUETOOTH_FILTER -> dialog = SignalsDialog.BLUETOOTH_FILTER
                        SignalsAction.GNSS_SORT -> dialog = SignalsDialog.GNSS_SORT
                        SignalsAction.GNSS_FILTER -> dialog = SignalsDialog.GNSS_FILTER
                    }
                }
            },
        )
        SignalsSubTabRow(subTab, onSubTabChange)
        // The Wi-Fi and Bluetooth lists carry their own strip, with counts, sort and pause.
        if (subTab == SignalsSubTab.CELLULAR || subTab == SignalsSubTab.GNSS) {
            val interval = intervals[subTab] ?: subTab.defaultIntervalSeconds
            ScanStrip(
                state = scanStripState(subTab, status, interval, viewedSim),
                blocked = blockedWord,
            )
        }
        Box(Modifier.weight(1f)) {
            key(subTab) {
                when (subTab) {
                    SignalsSubTab.CELLULAR -> CellularDetailsScreen()
                    SignalsSubTab.WIFI -> WifiListScreen()
                    SignalsSubTab.BLUETOOTH -> BluetoothListScreen()

                    SignalsSubTab.GNSS -> GnssScreen()
                }
            }
        }
    }

    when (dialog) {
        SignalsDialog.NONE -> Unit
        SignalsDialog.GNSS_FILTER -> SatsFilterDialog(
            onDismissRequest = { dialog = SignalsDialog.NONE },
            onSave = { dialog = SignalsDialog.NONE },
        )

        SignalsDialog.GNSS_SORT -> GnssSortDialogHost(activity, onDone = { dialog = SignalsDialog.NONE })

        // Each filter action only exists on its own sub-tab, so its state is collected here.
        SignalsDialog.WIFI_FILTER -> wifiState?.let {
            WifiFilterSheet(
                filter = it.filter,
                visibleApCount = it.visibleApCount,
                totalApCount = it.apCount,
                onQueryChanged = wifiListViewModel::setFilterQuery,
                onBandToggled = wifiListViewModel::toggleFilterBand,
                onClear = wifiListViewModel::clearFilter,
                onManageExclusionList = {
                    dialog = SignalsDialog.NONE
                    mainNavController.navigate(NavOption.SsidExclusionList.name)
                },
                onDismiss = { dialog = SignalsDialog.NONE },
            )
        }

        SignalsDialog.BLUETOOTH_FILTER -> bluetoothState?.let {
            BluetoothFilterSheet(
                filter = it.filter,
                visibleCount = it.visibleCount,
                totalCount = it.totalCount,
                onQueryChanged = bluetoothListViewModel::setFilterQuery,
                onNamedOnlyChanged = bluetoothListViewModel::setNamedOnly,
                onTrackersOnlyChanged = bluetoothListViewModel::setTrackersOnly,
                onClear = bluetoothListViewModel::clearFilter,
                onDismiss = { dialog = SignalsDialog.NONE },
            )
        }
    }
}

/** @return The strip's word for what stops the Cellular view, or null when nothing does. */
@Composable
private fun cellularBlockedWord(scanState: CellularScanState): String? = when (scanState) {
    CellularScanState.PERMISSION_NEEDED -> stringResource(R.string.cellular_state_permission)
    CellularScanState.LOCATION_OFF -> stringResource(R.string.survey_location_off)
    CellularScanState.AIRPLANE_MODE -> stringResource(R.string.cellular_state_airplane)
    CellularScanState.READY -> null
}

/** @return The strip's word for what stops the GNSS view, or null when nothing does. */
@Composable
private fun gnssBlockedWord(scanState: GnssScanState): String? = when (scanState) {
    GnssScanState.PERMISSION_NEEDED -> stringResource(R.string.cellular_state_permission)
    GnssScanState.LOCATION_OFF -> stringResource(R.string.survey_location_off)
    GnssScanState.READY -> null
}

/** The dialog or sheet the Signals top bar opened. */
private enum class SignalsDialog { NONE, GNSS_FILTER, GNSS_SORT, WIFI_FILTER, BLUETOOTH_FILTER }

/** Maps a [SignalsAction] to its icon, description and badge. */
private fun appBarAction(action: SignalsAction, filterActive: Boolean, onClick: () -> Unit): AppBarAction =
    when (action) {
        SignalsAction.OPEN_MAP -> AppBarAction(R.drawable.ic_map, R.string.open_map, onClick)
        SignalsAction.WIFI_SPECTRUM -> AppBarAction(R.drawable.ic_bar_chart, R.string.open_wifi_spectrum, onClick)
        // The description says when the filter is on; the badge dot alone is not announced.
        SignalsAction.WIFI_FILTER -> AppBarAction(
            icon = R.drawable.ic_filter,
            description = if (filterActive) {
                R.string.wifi_filter_content_description_active
            } else {
                R.string.wifi_filter_content_description
            },
            onClick = onClick,
            active = filterActive,
        )

        SignalsAction.BLUETOOTH_FILTER -> AppBarAction(
            icon = R.drawable.ic_filter,
            description = if (filterActive) {
                R.string.bluetooth_filter_content_description_active
            } else {
                R.string.bluetooth_filter_content_description
            },
            onClick = onClick,
            active = filterActive,
        )

        SignalsAction.GNSS_SORT -> AppBarAction(R.drawable.ic_sort, R.string.menu_option_sort_by, onClick)
        SignalsAction.GNSS_FILTER -> AppBarAction(
            icon = R.drawable.ic_filter,
            description = if (filterActive) {
                R.string.menu_option_filter_content_description_active
            } else {
                R.string.menu_option_filter_content_description
            },
            onClick = onClick,
            active = filterActive,
        )
    }

/** The satellite sort dialog, applied through the view model the GNSS pages share. */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun GnssSortDialogHost(activity: ComponentActivity, onDone: () -> Unit) {
    // The activity's Hilt factory builds the GPSTest view model, the one the GNSS pages use.
    val signalInfo: SignalInfoViewModel = viewModel(viewModelStoreOwner = activity)
    GnssSortByDialog(
        onDismissRequest = onDone,
        onSave = { index ->
            signalInfo.setSort(activity, index, Application.getPrefs())
            onDone()
        },
    )
}
