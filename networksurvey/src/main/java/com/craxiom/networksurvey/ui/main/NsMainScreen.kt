package com.craxiom.networksurvey.ui.main

import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.bluetooth.details.BLUETOOTH_DATA_KEY
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.onboarding.OnboardingChoice
import com.craxiom.networksurvey.ui.onboarding.OnboardingScreen
import com.craxiom.networksurvey.ui.onboarding.OnboardingViewModel
import com.craxiom.networksurvey.ui.onboarding.UpgradeWelcomeScreen
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.theme.rememberAccent
import timber.log.Timber

/**
 * The activity's whole UI: first run on a fresh install, then the page graph (the tabs at its root,
 * every other page above them), the startup dialogs, the navigation requests from the Java fragments and deep links, and keeping the
 * screen on while a survey records, whichever screen is showing.
 */
@Composable
fun MainCompose(
    mainNavController: NavHostController = rememberNavController(),
    deepLinkViewModel: DeepLinkViewModel = viewModel(),
) {
    val context = LocalContext.current

    val viewModel = viewModel<SharedViewModel>()
    val onboardingViewModel = viewModel<OnboardingViewModel>()
    val activity = context as? android.app.Activity

    // Bound with the activity's lifecycle; it only reads the published survey state.
    val surveyStatusViewModel = viewModel<SurveyStatusViewModel>()
    ServiceConnectionHandler(surveyStatusViewModel::onServiceChanged)
    val keepScreenOn by surveyStatusViewModel.keepScreenOn.collectAsStateWithLifecycle()
    KeepScreenOnWhileRecording(keepScreenOn)
    // The graph is set while the NavHost composes, after these effects first run; a navigation
    // request that arrives first (a cold-start deep link) waits for it.
    val graphReady = mainNavController.currentBackStackEntryAsState().value != null
    NavigationEventEffect(viewModel, mainNavController, deepLinkViewModel, graphReady)

    NsTheme(accent = rememberAccent()) {
        // First run replaces the tabs until it is finished or skipped. The NavHost is not composed
        // meanwhile, so graphReady stays false and any deep link or navigation request waits for it.
        val onboarding by onboardingViewModel.visible.collectAsStateWithLifecycle()
        if (onboarding) {
            OnboardingScreen(onboardingViewModel) { choice ->
                // A deep link that arrived during first run wins over the walkthrough
                if (choice == OnboardingChoice.NS_ANALYTICS && deepLinkViewModel.navigationDestination.value == null) {
                    deepLinkViewModel.navigateToPage(NavDrawerOption.NsAnalyticsConnection.name)
                }
            }
            return@NsTheme
        }
        // An upgraded phone gets a one-time welcome instead
        val welcome by onboardingViewModel.welcome.collectAsStateWithLifecycle()
        if (welcome) {
            UpgradeWelcomeScreen(onboardingViewModel)
            return@NsTheme
        }

        val deepLinkDestination by deepLinkViewModel.navigationDestination.collectAsStateWithLifecycle()
        LaunchedEffect(deepLinkDestination, graphReady) {
            if (!graphReady) return@LaunchedEffect
            deepLinkDestination?.let { destination ->
                Timber.i("Navigating to %s from deep link", destination)
                if (destination == NavDrawerOption.None.name) {
                    // The home screen is the root of the graph, so pop back to it rather than
                    // pushing a second copy on top of the stack.
                    mainNavController.popBackStack(NavDrawerOption.None.name, inclusive = false)
                } else {
                    mainNavController.navigate(destination) {
                        popUpTo(NavDrawerOption.None.name)
                    }
                }
                // Clear the navigation event to prevent re-navigation
                deepLinkViewModel.clearNavigation()
            }
        }

        // Render the activity-driven background location and GNSS dialogs using the shared
        // Compose dialog components.
        val startupActions = activity as? StartupDialogActions
        if (startupActions != null) {
            StartupDialogs(actions = startupActions, viewModel = viewModel)
        }

        // Each page draws its own Scaffold and handles the system bars itself.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavHost(
                mainNavController,
                startDestination = NavRoutes.MainRoute.name
            ) {
                mainGraph(
                    mainNavController = mainNavController,
                    sharedViewModel = viewModel
                )
            }
        }
    }
}

/**
 * Keeps the screen on while [keepScreenOn], whichever screen is showing, through the activity
 * window's FLAG_KEEP_SCREEN_ON. The window flag is applied by the window manager at once, where a
 * view's keepScreenOn is only folded into the window on its next traversal: after the activity
 * paused and resumed (a notification tap), that traversal missed the change and the screen stayed
 * on after Stop. Only this effect sets the window flag; the map keeps the screen on through its own
 * view, so neither can clear the other.
 */
@Composable
private fun KeepScreenOnWhileRecording(keepScreenOn: Boolean) {
    val window = LocalContext.current.findComponentActivity()?.window ?: return
    DisposableEffect(window, keepScreenOn) {
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** Opens the page a [NavigationEvent] asks for, then clears the event. */
@Composable
private fun NavigationEventEffect(
    viewModel: SharedViewModel,
    mainNavController: NavHostController,
    deepLinkViewModel: DeepLinkViewModel,
    graphReady: Boolean,
) {
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    LaunchedEffect(navigationEvent, graphReady) {
        if (!graphReady) return@LaunchedEffect
        val event = navigationEvent ?: return@LaunchedEffect
        when (event) {
            NavigationEvent.UploadSettings -> mainNavController.navigate(NavOption.UploadSettings.name)
            NavigationEvent.TowerMapSettings -> mainNavController.navigate(NavOption.TowerMapSettings.name)
            NavigationEvent.MapTab -> deepLinkViewModel.navigateToHomeTab(MainScreens.Map.route)
            NavigationEvent.Watchlist -> mainNavController.navigate(NavDrawerOption.Watchlist.name)
            NavigationEvent.QrCodeShare -> mainNavController.navigate(NavOption.QrCodeShare.name)
            is NavigationEvent.WifiDetails -> {
                mainNavController.currentBackStackEntry?.savedStateHandle?.set(WifiNetwork.KEY, event.wifiNetwork)
                mainNavController.navigate(NavOption.WifiDetails.name)
            }

            is NavigationEvent.BluetoothDetails -> {
                mainNavController.currentBackStackEntry?.savedStateHandle?.set(BLUETOOTH_DATA_KEY, event.bluetoothData)
                mainNavController.navigate(NavOption.BluetoothDetails.name)
            }

            NavigationEvent.MqttConnection -> mainNavController.navigate(NavDrawerOption.MqttBrokerConnection.name)
            NavigationEvent.Settings -> mainNavController.navigate(NavDrawerOption.Settings.name)
            NavigationEvent.GrpcConnection -> mainNavController.navigate(NavDrawerOption.ServerConnection.name)
            NavigationEvent.CommunityDatabases -> mainNavController.navigate(NavOption.CommunityDatabases.name)
            NavigationEvent.OrganizationSettings -> mainNavController.navigate(NavOption.OrganizationSettings.name)
            NavigationEvent.LogFiles -> mainNavController.navigate(NavOption.LogFiles.name)
            // Back from the QR scanner to the NS Analytics page
            NavigationEvent.NsAnalyticsConnection -> mainNavController.navigate(NavDrawerOption.NsAnalyticsConnection.name) {
                popUpTo(NavDrawerOption.None.name)
            }
        }
        // Clear the event after handling to prevent re-processing
        viewModel.clearNavigationEvent()
    }
}

enum class NavRoutes {
    MainRoute,
}
