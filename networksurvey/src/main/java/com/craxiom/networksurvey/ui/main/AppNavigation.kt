package com.craxiom.networksurvey.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.api.PlmnRecord
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.acknowledgments.AcknowledgmentsScreen
import com.craxiom.networksurvey.ui.bluetooth.details.BLUETOOTH_DATA_KEY
import com.craxiom.networksurvey.ui.bluetooth.details.BluetoothDetailsRoute
import com.craxiom.networksurvey.ui.cellular.CellularToolsScreen
import com.craxiom.networksurvey.ui.cellular.towermap.ProviderColorOverrideScreen
import com.craxiom.networksurvey.ui.cellular.towermap.ProviderColorOverrideViewModel
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.community.CommunityDatabasesScreen
import com.craxiom.networksurvey.ui.grpc.GrpcServerScreen
import com.craxiom.networksurvey.ui.logfiles.LogFilesScreen
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.mqtt.MqttBrokerEditorScreen
import com.craxiom.networksurvey.ui.mqtt.MqttBrokersScreen
import com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsConnectionScreen
import com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsConnectionViewModel
import com.craxiom.networksurvey.ui.organization.OrganizationSettingsScreen
import com.craxiom.networksurvey.ui.oui.OuiLookupScreen
import com.craxiom.networksurvey.ui.plmn.PlmnDetailsScreen
import com.craxiom.networksurvey.ui.plmn.PlmnLookupScreen
import com.craxiom.networksurvey.ui.plmn.components.PlmnInfoSheet
import com.craxiom.networksurvey.ui.settings.settingsPages
import com.craxiom.networksurvey.ui.watchlist.WATCHLIST_ENTRY_ID_KEY
import com.craxiom.networksurvey.ui.watchlist.WatchlistHistoryDetailScreen
import com.craxiom.networksurvey.ui.watchlist.WatchlistHistoryDetailViewModel
import com.craxiom.networksurvey.ui.watchlist.WatchlistHistoryScreen
import com.craxiom.networksurvey.ui.watchlist.WatchlistHistoryViewModel
import com.craxiom.networksurvey.ui.watchlist.WatchlistScreen
import com.craxiom.networksurvey.ui.watchlist.WatchlistViewModel
import com.craxiom.networksurvey.ui.wifi.SsidExclusionListViewModel
import com.craxiom.networksurvey.ui.wifi.details.WifiDetailsRoute
import com.craxiom.networksurvey.ui.wifi.spectrum.WifiSpectrumRoute

fun NavGraphBuilder.mainGraph(
    mainNavController: NavHostController,
    sharedViewModel: SharedViewModel
) {
    navigation(startDestination = NavDrawerOption.None.name, route = NavRoutes.MainRoute.name) {
        composable(NavDrawerOption.None.name) {
            HomeScreen(mainNavController = mainNavController, sharedViewModel = sharedViewModel)
        }

        // Deep links reach these pages through DeepLinkRouter, never through navDeepLink, so a link
        // is handled exactly once.
        composable(NavDrawerOption.NsAnalyticsConnection.name) {
            val viewModel: NsAnalyticsConnectionViewModel = viewModel()
            val resources = LocalResources.current
            val returnToSurvey = rememberReturnToSurvey(mainNavController, sharedViewModel)
            NsAnalyticsConnectionScreen(
                viewModel = viewModel,
                onNavigateUp = { mainNavController.navigateUp() },
                onNavigateToQrScanner = {
                    mainNavController.navigate(NavOption.NsAnalyticsQrScanner.name)
                },
                // Back to the Survey tab, which says where the phone is now connected.
                onConnected = { workspace -> returnToSurvey(resources.getString(R.string.ns_analytics_connected_to, workspace)) },
            )
        }

        composable(NavDrawerOption.ServerConnection.name) {
            GrpcServerScreen(
                onBack = { mainNavController.navigateUp() },
                onReturnToSurvey = rememberReturnToSurvey(mainNavController, sharedViewModel),
                onOpenOrganizationSettings = { sharedViewModel.triggerNavigationToOrganizationSettings() },
            )
        }

        composable(NavDrawerOption.MqttBrokerConnection.name) {
            MqttBrokersScreen(
                scannedSettings = sharedViewModel.scannedMqttSettings,
                onScannedSettingsConsumed = sharedViewModel::consumeScannedMqttSettings,
                editorMessage = sharedViewModel.mqttEditorMessage,
                onEditorMessageConsumed = sharedViewModel::consumeMqttEditorMessage,
                onBack = { mainNavController.navigateUp() },
                onOpenEditor = { profileId ->
                    mainNavController.currentBackStackEntry?.savedStateHandle?.set(MQTT_BROKER_PROFILE_ID_KEY, profileId)
                    mainNavController.navigate(NavOption.MqttBrokerEditor.name)
                },
                onOpenScanner = { mainNavController.navigate(NavOption.QrCodeScanner.name) },
                onOpenOrganizationSettings = { sharedViewModel.triggerNavigationToOrganizationSettings() },
            )
        }

        composable(NavOption.MqttBrokerEditor.name) {
            val profileId = mainNavController.previousBackStackEntry?.savedStateHandle?.get<String>(MQTT_BROKER_PROFILE_ID_KEY)
            MqttBrokerEditorScreen(
                profileId = profileId,
                onBack = { mainNavController.navigateUp() },
                onDone = { message ->
                    sharedViewModel.postMqttEditorMessage(message)
                    mainNavController.navigateUp()
                },
                onShare = { settings -> sharedViewModel.triggerNavigationToQrCodeShare(settings) },
            )
        }

        composable(NavDrawerOption.CellularCalculators.name) {
            var showBandHelp by rememberSaveable { mutableStateOf(false) }
            Scaffold(
                topBar = {
                    TitleBar(
                        title = stringResource(R.string.cellular_tools),
                        appBarActions = listOf(
                            AppBarAction(
                                icon = R.drawable.ic_help,
                                description = R.string.band_help_title,
                                onClick = { showBandHelp = true },
                            )
                        ),
                        onBackClick = { mainNavController.navigateUp() },
                    )
                },
            ) { innerPadding ->
                Box(modifier = Modifier.padding(paddingValues = innerPadding).nsContentWidth()) {
                    CellularToolsScreen(
                        showHelp = showBandHelp,
                        onHelpDismissed = { showBandHelp = false },
                    )
                }
            }
        }

        composable(NavOption.CommunityDatabases.name) {
            CommunityDatabasesScreen(
                onBack = { mainNavController.navigateUp() },
                onOpenUploadSettings = { mainNavController.navigate(NavOption.UploadSettings.name) },
                onSeeOnMap = { sharedViewModel.triggerNavigationToMapTab() },
                onOpenOrganizationSettings = { sharedViewModel.triggerNavigationToOrganizationSettings() },
            )
        }

        composable(NavOption.LogFiles.name) {
            LogFilesScreen(
                onBack = { mainNavController.navigateUp() },
                onOpenOrganizationSettings = { sharedViewModel.triggerNavigationToOrganizationSettings() },
            )
        }

        composable(NavOption.OrganizationSettings.name) {
            OrganizationSettingsScreen(onBack = { mainNavController.navigateUp() })
        }

        composable(NavDrawerOption.OuiLookup.name) {
            Scaffold(
                topBar = {
                    TitleBar(stringResource(R.string.oui_lookup_drawer_title)) {
                        mainNavController.navigateUp()
                    }
                },
            ) { innerPadding ->
                Box(modifier = Modifier.padding(paddingValues = innerPadding).nsContentWidth()) {
                    OuiLookupScreen(
                        onOpenSettings = {
                            mainNavController.navigate(NavDrawerOption.Settings.name)
                        }
                    )
                }
            }
        }

        composable(NavDrawerOption.PlmnLookup.name) {
            PlmnLookupInCompose(mainNavController)
        }

        composable(NavOption.PlmnDetails.name) {
            val record = mainNavController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<PlmnRecord>(PlmnRecord.KEY)
            PlmnDetailsInCompose(record, mainNavController)
        }

        settingsPages(mainNavController, sharedViewModel)

        // --------- Pages opened from other pages --------- //

        composable(NavOption.SsidExclusionList.name) {
            val viewModel =
                viewModel<SsidExclusionListViewModel>()
            com.craxiom.networksurvey.ui.wifi.SsidExclusionListScreen(
                viewModel = viewModel,
                onNavigateUp = { mainNavController.navigateUp() }
            )
        }

        composable(NavDrawerOption.Watchlist.name) {
            val watchlistViewModel = viewModel<WatchlistViewModel>()
            WatchlistScreen(
                viewModel = watchlistViewModel,
                onNavigateUp = { mainNavController.navigateUp() },
                onNavigateToHistory = { mainNavController.navigate(NavOption.WatchlistHistory.name) }
            )
        }

        composable(NavOption.WatchlistHistory.name) {
            val watchlistHistoryViewModel = viewModel<WatchlistHistoryViewModel>()
            WatchlistHistoryScreen(
                viewModel = watchlistHistoryViewModel,
                onNavigateUp = { mainNavController.navigateUp() },
                onOpenDetail = { entryId ->
                    mainNavController.currentBackStackEntry?.savedStateHandle?.set(
                        WATCHLIST_ENTRY_ID_KEY,
                        entryId
                    )
                    mainNavController.navigate(NavOption.WatchlistHistoryDetail.name)
                }
            )
        }

        composable(NavOption.WatchlistHistoryDetail.name) {
            val entryId = mainNavController.previousBackStackEntry?.savedStateHandle
                ?.get<Long>(WATCHLIST_ENTRY_ID_KEY) ?: -1L
            val detailViewModel = viewModel<WatchlistHistoryDetailViewModel>()
            WatchlistHistoryDetailScreen(
                viewModel = detailViewModel,
                entryId = entryId,
                onNavigateUp = { mainNavController.navigateUp() }
            )
        }

        composable(NavOption.ProviderColorOverrides.name) {
            val viewModel =
                viewModel<ProviderColorOverrideViewModel>()
            ProviderColorOverrideScreen(
                viewModel = viewModel,
                onNavigateUp = { mainNavController.navigateUp() }
            )
        }

        composable(NavOption.Acknowledgments.name) {
            AcknowledgmentsScreen(
                onNavigateUp = { mainNavController.navigateUp() }
            )
        }

        composable(NavOption.QrCodeScanner.name) {
            QrCodeScannerInCompose(mainNavController)
        }

        composable(NavOption.QrCodeShare.name) {
            QrCodeShareInCompose(mainNavController)
        }

        composable(NavOption.WifiSpectrum.name) {
            val seeded = mainNavController.previousBackStackEntry?.savedStateHandle?.get<Boolean>(WIFI_SPECTRUM_SEEDED_KEY) == true
            TitledPage(stringResource(R.string.wifi_spectrum_title), mainNavController) { modifier ->
                WifiSpectrumRoute(seeded = seeded, modifier = modifier)
            }
        }

        composable(NavOption.WifiDetails.name) {
            val wifiNetwork =
                mainNavController.previousBackStackEntry?.savedStateHandle?.get<WifiNetwork>(
                    WifiNetwork.KEY
                )

            WifiDetailsRoute(wifiNetwork, onBack = { mainNavController.navigateUp() })
        }

        composable(NavOption.BluetoothDetails.name) {
            val bluetoothRecordData =
                mainNavController.previousBackStackEntry?.savedStateHandle?.get<BluetoothRecordData>(
                    BLUETOOTH_DATA_KEY
                )

            BluetoothDetailsRoute(bluetoothRecordData, onBack = { mainNavController.navigateUp() })
        }

        composable(NavOption.NsAnalyticsQrScanner.name) {
            NsAnalyticsQrScannerInCompose(mainNavController)
        }
    }
}

enum class NavDrawerOption {
    None,
    NsAnalyticsConnection,
    ServerConnection,
    MqttBrokerConnection,
    CellularCalculators,
    OuiLookup,
    PlmnLookup,
    Watchlist,
    Settings,

    // External Links
    UserManual,
    MessagingDocs,
    ReportAnIssue,
    GitHub
}

enum class NavOption {
    UploadSettings,
    TowerMapSettings,
    QrCodeScanner,
    QrCodeShare,
    WifiSpectrum,
    WifiDetails,
    BluetoothDetails,
    PlmnDetails,
    SsidExclusionList,
    ProviderColorOverrides,
    Acknowledgments,
    NsAnalyticsQrScanner,
    WatchlistHistory,
    WatchlistHistoryDetail,
    CommunityDatabases,
    MqttBrokerEditor,
    OrganizationSettings,
    LogFiles,
}

/** The saved-state key that carries the broker profile id to the broker editor (null adds one). */
private const val MQTT_BROKER_PROFILE_ID_KEY = "mqttBrokerProfileId"

/**
 * @return An action that goes back to the Survey tab and shows [message] there, for a destination
 * page whose job is done (NS Analytics connected, the first gRPC server saved).
 */
@Composable
private fun rememberReturnToSurvey(mainNavController: NavHostController, sharedViewModel: SharedViewModel): (String) -> Unit {
    val activity = LocalContext.current.findComponentActivity()
    val deepLinkViewModel: DeepLinkViewModel? = activity?.let { viewModel(viewModelStoreOwner = it) }
    return remember(mainNavController, sharedViewModel, deepLinkViewModel) {
        { message ->
            sharedViewModel.postSurveyMessage(message)
            deepLinkViewModel?.navigateToHomeTab(MainScreens.Survey.route)
                ?: mainNavController.popBackStack(NavDrawerOption.None.name, false)
        }
    }
}

@Composable
fun PlmnLookupInCompose(mainNavController: NavHostController) {
    var showInfoSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TitleBar(
                title = stringResource(R.string.plmn_lookup_drawer_title),
                onBackClick = { mainNavController.navigateUp() },
                appBarActions = listOf(
                    AppBarAction(
                        icon = R.drawable.ic_help,
                        description = R.string.plmn_help_description,
                        onClick = { showInfoSheet = true }
                    )
                )
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(paddingValues = innerPadding).nsContentWidth()) {
            PlmnLookupScreen(
                onRecordClick = { record ->
                    mainNavController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(PlmnRecord.KEY, record)
                    mainNavController.navigate(NavOption.PlmnDetails.name)
                }
            )
        }
    }

    if (showInfoSheet) {
        PlmnInfoSheet(onDismiss = { showInfoSheet = false })
    }
}

@Composable
fun PlmnDetailsInCompose(record: PlmnRecord?, mainNavController: NavHostController) {
    var showInfoSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TitleBar(
                title = stringResource(R.string.plmn_details_title),
                onBackClick = { mainNavController.navigateUp() },
                appBarActions = listOf(
                    AppBarAction(
                        icon = R.drawable.ic_help,
                        description = R.string.plmn_help_description,
                        onClick = { showInfoSheet = true }
                    )
                )
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(paddingValues = innerPadding)) {
            if (record != null) {
                PlmnDetailsScreen(record = record)
            }
        }
    }

    if (showInfoSheet) {
        PlmnInfoSheet(onDismiss = { showInfoSheet = false })
    }
}

