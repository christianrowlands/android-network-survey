package com.craxiom.networksurvey.ui.wifi.spectrum

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.craxiom.networksurvey.ui.wifi.model.AWifiSpectrumChartViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiEnvironment
import com.craxiom.networksurvey.ui.wifi.model.WifiListViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum24ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum5Group1ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum5Group2ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum5Group3ViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiSpectrum6ViewModel

/**
 * The Wi-Fi spectrum route: binds to the survey service while the page shows, re-checks the
 * permission, location and Wi-Fi on every resume, and feeds each scan to the five band charts.
 *
 * @param seeded Opened from the Wi-Fi list, so its last scan is drawn until the next one (from More
 * the charts wait for a scan).
 */
@Composable
fun WifiSpectrumRoute(seeded: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val viewModel: WifiSpectrumViewModel = viewModel()
    val sharedViewModel: SharedViewModel = viewModel(viewModelStoreOwner = activity)
    val signalsViewModel: SignalsViewModel = viewModel(viewModelStoreOwner = activity)
    val charts = bandCharts()
    if (seeded) {
        val wifiList: WifiListViewModel = viewModel(viewModelStoreOwner = activity)
        LaunchedEffect(wifiList) { viewModel.seed(wifiList.seedRecords()) }
    }

    ServiceConnectionHandler(viewModel::onServiceChanged)
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val permission = rememberPermissionRequest(WifiEnvironment.PERMISSIONS, onAnswered = viewModel::refresh) {
        viewModel.refresh()
    }

    val networks by viewModel.networks.collectAsStateWithLifecycle()
    LaunchedEffect(networks, charts) {
        networks?.let { list -> charts.values.forEach { it.onWifiScanResults(list) } }
    }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val intervals by signalsViewModel.intervals.collectAsStateWithLifecycle()

    WifiSpectrumScreen(
        scanState = scanState,
        scanRateSeconds = intervals.getValue(SignalsSubTab.WIFI),
        permissionAccess = permission.access,
        actions = remember(permission) { RouteActions(context, sharedViewModel, permission) },
        modifier = modifier,
    ) { band ->
        val chart = charts.getValue(band)
        val wifiList by chart.wifiNetworkInfoList.collectAsStateWithLifecycle()
        WifiSpectrumChart(
            wifiList = wifiList,
            modelProducer = chart.modelProducer,
            minX = band.chartView.first().toFloat(),
            maxX = band.chartView.last().toFloat(),
            customLabelValues = band.channels,
            labelInterval = band.labelInterval,
        )
    }
}

/** The five band charts, each kept by the page's navigation entry and drawn empty until a scan. */
@Composable
private fun bandCharts(): Map<SpectrumBand, AWifiSpectrumChartViewModel> {
    val charts = mapOf(
        SpectrumBand.GHZ_24 to viewModel<WifiSpectrum24ViewModel>(key = "chart24Ghz"),
        SpectrumBand.GHZ_5_LOW to viewModel<WifiSpectrum5Group1ViewModel>(key = "chart5GhzGroup1"),
        SpectrumBand.GHZ_5_MID to viewModel<WifiSpectrum5Group2ViewModel>(key = "chart5GhzGroup2"),
        SpectrumBand.GHZ_5_HIGH to viewModel<WifiSpectrum5Group3ViewModel>(key = "chart5GhzGroup3"),
        SpectrumBand.GHZ_6 to viewModel<WifiSpectrum6ViewModel>(key = "chart6Ghz"),
    )
    return remember(charts.values.toList()) {
        charts.values.forEach { it.initializeCharts() }
        charts
    }
}

/** Wires [WifiSpectrumActions] to the settings page, the permission request and the system screens. */
private class RouteActions(
    private val context: Context,
    private val sharedViewModel: SharedViewModel,
    private val permission: PermissionRequest,
) : WifiSpectrumActions {
    override fun onOpenSettings() = sharedViewModel.triggerNavigationToSettings()
    override fun onAllow() = permission.request()
    override fun onOpenAppSettings() = openAppSettings(context)
    override fun onTurnOnLocation() = openLocationSettings(context)
    override fun onTurnOnWifi() = openWifiSettings(context)
}
