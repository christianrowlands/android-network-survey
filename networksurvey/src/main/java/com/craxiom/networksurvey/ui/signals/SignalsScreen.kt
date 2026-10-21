package com.craxiom.networksurvey.ui.signals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.ui.GnssScreen
import com.craxiom.networksurvey.ui.bluetooth.list.BluetoothListScreen
import com.craxiom.networksurvey.ui.cellular.details.CellularDetailsScreen
import com.craxiom.networksurvey.ui.cellular.details.CellularDetailsViewModel
import com.craxiom.networksurvey.ui.cellular.details.model.CellularScanState
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.main.NavOption
import com.craxiom.networksurvey.ui.main.SurveyStatusViewModel
import com.craxiom.networksurvey.ui.main.openWifiSpectrum
import com.craxiom.networksurvey.ui.wifi.WifiListScreen

/**
 * The Signals tab: the live Cellular, Wi-Fi, Bluetooth and GNSS views under one sub-tab row, with
 * a strip saying whether what is on screen is being recorded. There is no top bar: the sub-tab row
 * sits at the top, and each view keeps its own buttons (sort, filter, spectrum) on its own strip, so
 * a short window has as much room as it can for the signals.
 *
 * The sub-tab is rendered with a `when` block rather than a nested NavHost: a nested NavHost takes
 * over Back once its stack grows, and keeps nothing for fragments anyway. Each branch calls its own
 * host, inside `key`, so a fragment never inherits another fragment type's container.
 */
@Composable
fun SignalsScreen(
    subTab: SignalsSubTab,
    onSubTabChange: (SignalsSubTab) -> Unit,
    mainNavController: NavHostController,
) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    val signalsViewModel: SignalsViewModel = viewModel(viewModelStoreOwner = activity)
    val statusViewModel: SurveyStatusViewModel = viewModel(viewModelStoreOwner = activity)
    val cellularViewModel: CellularDetailsViewModel = viewModel(viewModelStoreOwner = activity)

    LifecycleResumeEffect(signalsViewModel) {
        signalsViewModel.refreshIntervals()
        onPauseOrDispose { }
    }

    val status by statusViewModel.status.collectAsStateWithLifecycle()
    val intervals by signalsViewModel.intervals.collectAsStateWithLifecycle()
    val viewedSim by signalsViewModel.viewedSim.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        SignalsSubTabRow(subTab, onSubTabChange)
        // The Wi-Fi and Bluetooth lists carry their own strip, with counts, sort and pause; GNSS has
        // its Status | Sky row, and its blocked states fill the view.
        if (subTab == SignalsSubTab.CELLULAR) {
            val interval = intervals[subTab] ?: subTab.defaultIntervalSeconds
            val cellular by cellularViewModel.uiState.collectAsStateWithLifecycle()
            ScanStrip(
                state = scanStripState(subTab, status, interval, viewedSim),
                // What stops the Cellular view replaces the interval, as the lists' strips show it.
                blocked = cellularBlockedWord(cellular.scanState),
                sweepKey = cellular.sim.scanCount,
            )
        }
        Box(Modifier.weight(1f)) {
            key(subTab) {
                when (subTab) {
                    SignalsSubTab.CELLULAR -> CellularDetailsScreen()
                    SignalsSubTab.WIFI -> WifiListScreen(
                        onOpenSpectrum = { openWifiSpectrum(mainNavController, seeded = true) },
                        onManageExclusionList = { mainNavController.navigate(NavOption.SsidExclusionList.name) },
                    )

                    SignalsSubTab.BLUETOOTH -> BluetoothListScreen()
                    SignalsSubTab.GNSS -> GnssScreen()
                }
            }
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
