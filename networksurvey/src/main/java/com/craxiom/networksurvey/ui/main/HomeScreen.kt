package com.craxiom.networksurvey.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.main.more.MoreScreen
import com.craxiom.networksurvey.ui.map.MapTabScreen
import com.craxiom.networksurvey.ui.map.MapTabViewModel
import com.craxiom.networksurvey.ui.signals.SignalsScreen
import com.craxiom.networksurvey.ui.signals.SignalsViewModel
import com.craxiom.networksurvey.ui.survey.SurveyScreen

/**
 * The root page of the app: the four tabs. Every other page opens above it in the outer graph and
 * comes back to the tab it was opened from.
 */
@Composable
fun HomeScreen(mainNavController: NavHostController, sharedViewModel: SharedViewModel) {
    val activity = LocalContext.current.findComponentActivity()
    // A notification tap or a page asks for a tab through the activity-scoped DeepLinkViewModel.
    val deepLinkViewModel: DeepLinkViewModel? = activity?.let { viewModel(viewModelStoreOwner = it) }
    val homeTabRequest = deepLinkViewModel?.homeTabDestination?.collectAsStateWithLifecycle()?.value

    HomeShell(
        homeTabRequest = homeTabRequest,
        onHomeTabHandled = { deepLinkViewModel?.clearHomeTab() },
        survey = { SurveyTab(mainNavController, sharedViewModel) },
        signals = { subTab, onSubTabChange, onOpenMap ->
            SignalsScreen(subTab, onSubTabChange, { openMapOnViewedSim(activity, onOpenMap) }, mainNavController)
        },
        map = { MapTabScreen(sharedViewModel) },
        more = { MoreScreen(onOpen = { route -> openFromMore(mainNavController, route) }) },
    )
}

/** The Survey tab draws its own top bar, since its Saved setups action opens a sheet of the screen's. */
@Composable
private fun SurveyTab(mainNavController: NavHostController, sharedViewModel: SharedViewModel) {
    SurveyScreen(
        sharedViewModel = sharedViewModel,
        onOpenSettings = { mainNavController.navigate(NavDrawerOption.Settings.name) },
    )
}

/**
 * The Cellular view's map action: the Map tab opens on the SIM the Cellular view shows, whichever
 * SIM the map showed before.
 */
private fun openMapOnViewedSim(activity: ComponentActivity?, openMap: () -> Unit) {
    if (activity != null) {
        val viewedSim = ViewModelProvider(activity)[SignalsViewModel::class.java].viewedSim.value
        ViewModelProvider(activity)[MapTabViewModel::class.java].requestSim(viewedSim)
    }
    openMap()
}

/** Opens a page from the More list; the Wi-Fi spectrum opens without the Wi-Fi tab's last scan. */
private fun openFromMore(mainNavController: NavHostController, route: String) {
    if (route == NavOption.WifiSpectrum.name) {
        openWifiSpectrum(mainNavController, seeded = false)
    } else {
        mainNavController.navigate(route)
    }
}

/** The saved-state key that tells the Wi-Fi spectrum whether to show the Wi-Fi list's last scan. */
internal const val WIFI_SPECTRUM_SEEDED_KEY = "wifiSpectrumSeeded"

/**
 * Opens the Wi-Fi spectrum. [seeded] shows the Wi-Fi list's last scan right away (from the Wi-Fi
 * sub-tab); otherwise the charts fill on the next scan, so they never show a stale list.
 */
fun openWifiSpectrum(mainNavController: NavHostController, seeded: Boolean) {
    mainNavController.currentBackStackEntry?.savedStateHandle?.set(WIFI_SPECTRUM_SEEDED_KEY, seeded)
    mainNavController.navigate(NavOption.WifiSpectrum.name)
}
