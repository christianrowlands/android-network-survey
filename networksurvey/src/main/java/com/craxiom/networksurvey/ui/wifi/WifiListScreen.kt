package com.craxiom.networksurvey.ui.wifi

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.common.PermissionRequest
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.common.openAppSettings
import com.craxiom.networksurvey.ui.common.openLocationSettings
import com.craxiom.networksurvey.ui.common.openSystemSettings
import com.craxiom.networksurvey.ui.common.openWifiSettings
import com.craxiom.networksurvey.ui.common.rememberPermissionRequest
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.signals.SignalsSubTab
import com.craxiom.networksurvey.ui.signals.signalsStripWords
import com.craxiom.networksurvey.ui.util.copyWithSystemConfirmation
import com.craxiom.networksurvey.ui.wifi.model.ThrottleWarning
import com.craxiom.networksurvey.ui.wifi.model.WifiEnvironment
import com.craxiom.networksurvey.ui.wifi.model.WifiListMode
import com.craxiom.networksurvey.ui.wifi.model.WifiListViewModel
import com.craxiom.networksurvey.util.WifiBand

/**
 * The Wi-Fi sub-tab of Signals: binds to the survey service while it shows, re-checks the
 * permission, location and Wi-Fi on every resume, and asks for nothing until the user taps the one
 * button that fixes what is missing.
 *
 * @param onOpenSpectrum Opens the Wi-Fi spectrum with this list's last scan.
 * @param onManageExclusionList Opens the SSID exclusion list, from the filter sheet.
 */
@Composable
fun WifiListScreen(onOpenSpectrum: () -> Unit, onManageExclusionList: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val viewModel: WifiListViewModel = viewModel(viewModelStoreOwner = activity)
    val sharedViewModel: SharedViewModel = viewModel(viewModelStoreOwner = activity)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openSpectrum by rememberUpdatedState(onOpenSpectrum)
    val manageExclusionList by rememberUpdatedState(onManageExclusionList)

    ServiceConnectionHandler(viewModel::onServiceChanged)
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val permission = rememberPermissionRequest(WifiEnvironment.PERMISSIONS, onAnswered = viewModel::refresh) {
        viewModel.refresh()
    }

    WifiListContent(
        state = state,
        stripWords = signalsStripWords(SignalsSubTab.WIFI),
        permissionAccess = permission.access,
        actions = remember(viewModel, permission) {
            ScreenActions(context, viewModel, sharedViewModel, permission, { openSpectrum() }, { manageExclusionList() })
        },
    )
}

/** Wires [WifiListActions] to the view models, the permission request and the system screens. */
private class ScreenActions(
    private val context: Context,
    private val viewModel: WifiListViewModel,
    private val sharedViewModel: SharedViewModel,
    private val permission: PermissionRequest,
    private val openSpectrum: () -> Unit,
    private val manageExclusionList: () -> Unit,
) : WifiListActions {

    override fun onNetworkClick(bssid: String) {
        val wrapper = viewModel.wrapperFor(bssid) ?: return
        sharedViewModel.triggerNavigationToWifiDetails(WifiNetwork.from(wrapper))
    }

    override fun onCopySsid(ssid: String) = copyWithSystemConfirmation(
        context,
        context.getString(R.string.ssid_clip_label),
        ssid,
        context.getString(R.string.ssid_copied),
    )

    override fun onCopyBssid(bssid: String) = copyWithSystemConfirmation(
        context,
        context.getString(R.string.bssid_clip_label),
        bssid,
        context.getString(R.string.bssid_copied),
    )

    override fun onToggleGroup(groupKey: String) = viewModel.toggleGroupExpanded(groupKey)

    override fun onModeChanged(mode: WifiListMode) = viewModel.setMode(mode)

    override fun onSortSelected(index: Int) = viewModel.setSortIndex(index)

    override fun onTogglePause() = viewModel.togglePaused()

    override fun onClearFilter() = viewModel.clearFilter()

    override fun onFilterQueryChanged(query: String) = viewModel.setFilterQuery(query)

    override fun onFilterBandToggled(band: WifiBand) = viewModel.toggleFilterBand(band)

    override fun onManageExclusionList() = manageExclusionList()

    override fun onOpenSpectrum() = openSpectrum()

    override fun onAllow() = permission.request()

    override fun onOpenAppSettings() = openAppSettings(context)

    override fun onTurnOnLocation() = openLocationSettings(context)

    override fun onTurnOnWifi() = openWifiSettings(context)

    /** The throttle switch is in developer options; with those off, About phone is where they turn on. */
    override fun onOpenThrottleSettings(warning: ThrottleWarning) = openSystemSettings(
        context,
        if (warning.developerOptionsOn) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_DEVICE_INFO_SETTINGS,
    )

    override fun onDismissThrottle() = viewModel.dismissThrottleWarning()
}
