package com.craxiom.networksurvey.ui.wifi.clients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.main.NavOption
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiListViewModel

/** The saved-state key that carries the client's MAC address to Client details. */
const val WIFI_CLIENT_MAC_KEY = "wifiClientMac"

/**
 * The Client details page in the main graph, opened from the Clients list and an access point's
 * Connected clients (through [SharedViewModel.triggerNavigationToWifiClientDetails]).
 */
fun NavGraphBuilder.wifiClientPages(mainNavController: NavHostController, sharedViewModel: SharedViewModel) {
    composable(NavOption.WifiClientDetails.name) {
        val mac = mainNavController.previousBackStackEntry?.savedStateHandle?.get<String>(WIFI_CLIENT_MAC_KEY)
        WifiClientDetailsRoute(mac, sharedViewModel, onBack = { mainNavController.navigateUp() })
    }
}

/**
 * The Client details route: binds to the survey service while the page shows (so the NS Scout keeps
 * capturing clients), pauses the chart while away, and opens the access point's details from the
 * Wi-Fi list it shares with Signals. Shows nothing without a MAC address.
 */
@Composable
private fun WifiClientDetailsRoute(mac: String?, sharedViewModel: SharedViewModel, onBack: () -> Unit) {
    if (mac == null) return
    val activity = LocalContext.current.findComponentActivity() ?: return
    val viewModel: WifiClientDetailsViewModel = viewModel()
    val listViewModel: WifiListViewModel = viewModel(viewModelStoreOwner = activity)
    val clientsViewModel: WifiClientsViewModel = viewModel(viewModelStoreOwner = activity)
    LaunchedEffect(viewModel, mac) { viewModel.show(mac, clientsViewModel.lastOpened(mac)) }

    ServiceConnectionHandler(viewModel::onServiceChanged)
    LifecycleResumeEffect(viewModel) {
        viewModel.setChartPaused(false)
        onPauseOrDispose { viewModel.setChartPaused(true) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    WifiClientDetailsScreen(
        state = state,
        chart = viewModel.chart,
        actions = remember(listViewModel, sharedViewModel) {
            object : WifiClientDetailsActions {
                override fun onBack() = onBack()

                override fun ssidOf(bssid: String): String? = listViewModel.ssidOf(bssid)

                override fun canOpenNetwork(bssid: String): Boolean = listViewModel.wrapperFor(bssid) != null

                override fun onOpenNetwork(bssid: String) {
                    val wrapper = listViewModel.wrapperFor(bssid) ?: return
                    sharedViewModel.triggerNavigationToWifiDetails(WifiNetwork.from(wrapper))
                }
            }
        },
    )
}
