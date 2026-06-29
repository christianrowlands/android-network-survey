package com.craxiom.networksurvey.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.api.PlmnRecord
import com.craxiom.networksurvey.databinding.ContainerBluetoothDetailsFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerGrpcFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerMqttFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerMqttQrCodeScannerFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerMqttQrCodeShareFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerNsAnalyticsQrScannerFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerSettingsFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerTowerMapFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerTowerMapSettingsFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerUploadSettingsFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerWifiDetailsFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerWifiSpectrumFragmentBinding
import com.craxiom.networksurvey.fragments.BLUETOOTH_DATA_KEY
import com.craxiom.networksurvey.fragments.BluetoothDetailsFragment
import com.craxiom.networksurvey.fragments.MqttFragment
import com.craxiom.networksurvey.fragments.TowerMapFragment
import com.craxiom.networksurvey.fragments.WifiDetailsFragment
import com.craxiom.networksurvey.fragments.WifiSpectrumFragment
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.acknowledgments.AcknowledgmentsScreen
import com.craxiom.networksurvey.ui.activesurvey.SurveyMonitorScreen
import com.craxiom.networksurvey.ui.cellular.CalculatorScreen
import com.craxiom.networksurvey.ui.cellular.towermap.ProviderColorOverrideScreen
import com.craxiom.networksurvey.ui.cellular.towermap.ProviderColorOverrideViewModel
import com.craxiom.networksurvey.ui.grpc.GrpcHelpDialog
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.mqtt.MqttHelpDialog
import com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsConnectionScreen
import com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsConnectionViewModel
import com.craxiom.networksurvey.ui.oui.OuiLookupScreen
import com.craxiom.networksurvey.ui.plmn.PlmnDetailsScreen
import com.craxiom.networksurvey.ui.plmn.PlmnLookupScreen
import com.craxiom.networksurvey.ui.plmn.components.PlmnInfoSheet
import com.craxiom.networksurvey.ui.watchlist.WatchlistHistoryScreen
import com.craxiom.networksurvey.ui.watchlist.WatchlistHistoryViewModel
import com.craxiom.networksurvey.ui.watchlist.WatchlistScreen
import com.craxiom.networksurvey.ui.watchlist.WatchlistViewModel
import com.craxiom.networksurvey.ui.wifi.SsidExclusionListViewModel
import com.craxiom.networksurvey.ui.wifi.model.WifiNetworkInfoList

fun NavGraphBuilder.mainGraph(
    drawerState: DrawerState,
    paddingValues: PaddingValues,
    mainNavController: NavHostController,
    sharedViewModel: SharedViewModel
) {
    navigation(startDestination = NavDrawerOption.None.name, route = NavRoutes.MainRoute.name) {
        composable(NavDrawerOption.None.name) {
            HomeScreen(
                drawerState,
                mainNavController = mainNavController,
                sharedViewModel = sharedViewModel
            )
        }

        composable(
            NavDrawerOption.NsAnalyticsConnection.name,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "https://networksurvey.app/app/register"
                }
            )
        ) {
            val viewModel: NsAnalyticsConnectionViewModel = viewModel()
            NsAnalyticsConnectionScreen(
                viewModel = viewModel,
                onNavigateUp = { mainNavController.navigateUp() },
                onNavigateToQrScanner = {
                    mainNavController.navigate(NavOption.NsAnalyticsQrScanner.name)
                }
            )
        }

        composable(
            NavDrawerOption.ServerConnection.name,
            deepLinks = listOf(navDeepLink {
                uriPattern = "http://craxiom.com/grpc_server_connection"
            })
        ) {
            GrpcFragmentInCompose(mainNavController)
        }

        composable(NavDrawerOption.MqttBrokerConnection.name)
        {
            val mqttConnectionSettings =
                mainNavController.previousBackStackEntry?.savedStateHandle?.get<MqttConnectionSettings>(
                    MqttConnectionSettings.KEY
                )

            MqttFragmentInCompose(
                mqttConnectionSettings = mqttConnectionSettings,
                mainNavController = mainNavController
            )
        }

        composable(NavDrawerOption.CellularCalculators.name) {
            Scaffold(
                topBar = { TitleBar("Cellular Calculators") { mainNavController.navigateUp() } },
            ) { innerPadding ->
                Box(modifier = Modifier.padding(paddingValues = innerPadding)) {
                    CalculatorScreen(viewModel = viewModel())
                }
            }
        }

        composable(NavDrawerOption.SurveyMonitor.name) {
            SurveyMonitorScreen(
                onBackPressed = { mainNavController.navigateUp() },
                onNavigateToTowerMapSettings = {
                    mainNavController.navigate(NavOption.TowerMapSettings.name)
                }
            )
        }

        composable(NavDrawerOption.OuiLookup.name) {
            Scaffold(
                topBar = {
                    TitleBar(stringResource(R.string.oui_lookup_drawer_title)) {
                        mainNavController.navigateUp()
                    }
                },
            ) { innerPadding ->
                Box(modifier = Modifier.padding(paddingValues = innerPadding)) {
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

        composable(NavDrawerOption.Settings.name) {
            SettingsFragmentInCompose(mainNavController)
        }

        // --------- Deeper navigation (beyond the nav drawer) --------- //

        composable(NavOption.UploadSettings.name) {
            CommunityUploadSettingsFragmentInCompose(mainNavController)
        }

        composable(NavOption.TowerMapSettings.name) {
            TowerMapSettingsFragmentInCompose(mainNavController)
        }

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

        composable(NavOption.TowerMap.name) {
            TowerMapInCompose(paddingValues)
        }

        composable(NavOption.WifiSpectrum.name) {
            WifiSpectrumInCompose(sharedViewModel.wifiNetworkList, mainNavController)
        }

        composable(NavOption.WifiDetails.name) {
            val wifiNetwork =
                mainNavController.previousBackStackEntry?.savedStateHandle?.get<WifiNetwork>(
                    WifiNetwork.KEY
                )

            WifiDetailsInCompose(paddingValues, wifiNetwork)
        }

        composable(NavOption.BluetoothDetails.name) {
            val bluetoothRecordData =
                mainNavController.previousBackStackEntry?.savedStateHandle?.get<BluetoothRecordData>(
                    BLUETOOTH_DATA_KEY
                )

            BluetoothDetailsInCompose(paddingValues, bluetoothRecordData)
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
    SurveyMonitor,
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
    TowerMap,
    WifiSpectrum,
    WifiDetails,
    BluetoothDetails,
    PlmnDetails,
    SsidExclusionList,
    ProviderColorOverrides,
    Acknowledgments,
    NsAnalyticsQrScanner,
    WatchlistHistory
}

@Composable
fun GrpcFragmentInCompose(mainNavController: NavHostController) {
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        GrpcHelpDialog(onDismissRequest = { showHelpDialog = false })
    }

    Scaffold(
        topBar = {
            TitleBar(
                title = "gRPC Connection",
                onBackClick = { mainNavController.navigateUp() },
                appBarActions = listOf(
                    AppBarAction(
                        icon = R.drawable.ic_help,
                        description = R.string.grpc_help_description,
                        onClick = { showHelpDialog = true }
                    )
                )
            )
        },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerGrpcFragmentBinding::inflate,
            modifier = Modifier.padding(innerPadding)
        ) {
        }
    }
}

@Suppress("AssignedValueIsNeverRead")
@Composable
fun MqttFragmentInCompose(
    mqttConnectionSettings: MqttConnectionSettings?,
    mainNavController: NavHostController
) {
    var showHelpDialog by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        MqttHelpDialog(onDismissRequest = { showHelpDialog = false })
    }

    Scaffold(
        topBar = {
            TitleBar(
                title = "MQTT Broker",
                onBackClick = { mainNavController.navigateUp() },
                appBarActions = listOf(
                    AppBarAction(
                        icon = R.drawable.ic_help,
                        description = R.string.mqtt_help_description,
                        onClick = { showHelpDialog = true }
                    )
                )
            )
        },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerMqttFragmentBinding::inflate,
            modifier = Modifier.padding(innerPadding)
        ) {
            val fragment: MqttFragment? = mqttFragmentContainerView.getFragment()
            fragment?.setMqttConnectionSettings(mqttConnectionSettings)
        }
    }
}

@Composable
fun SettingsFragmentInCompose(mainNavController: NavHostController) {
    Scaffold(
        topBar = { TitleBar("Settings") { mainNavController.navigateUp() } },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerSettingsFragmentBinding::inflate,
            modifier = Modifier.padding(paddingValues = innerPadding)
        ) {
        }
    }
}

@Composable
fun CommunityUploadSettingsFragmentInCompose(mainNavController: NavHostController) {
    Scaffold(
        topBar = { TitleBar("OpenCelliD & BeaconDB Upload Settings") { mainNavController.navigateUp() } },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerUploadSettingsFragmentBinding::inflate,
            modifier = Modifier.padding(paddingValues = innerPadding)
        ) {
        }
    }
}

@Composable
fun TowerMapSettingsFragmentInCompose(mainNavController: NavHostController) {
    Scaffold(
        topBar = { TitleBar("Tower Map Settings") { mainNavController.navigateUp() } },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerTowerMapSettingsFragmentBinding::inflate,
            modifier = Modifier.padding(paddingValues = innerPadding)
        ) {
        }
    }
}

@Composable
fun QrCodeScannerInCompose(mainNavController: NavHostController) {
    Scaffold(
        // TODO When navigating back the current settings are lost if they have not been saved, fix this
        topBar = { TitleBar("QR Code Scanner") { mainNavController.navigateUp() } },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerMqttQrCodeScannerFragmentBinding::inflate,
            modifier = Modifier.padding(paddingValues = innerPadding)
        ) {
        }
    }
}

@Composable
fun QrCodeShareInCompose(mainNavController: NavHostController) {
    Scaffold(
        // TODO When navigating back the current settings are lost if they have not been saved, fix this
        topBar = { TitleBar("QR Code Share") { mainNavController.navigateUp() } },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerMqttQrCodeShareFragmentBinding::inflate,
            modifier = Modifier.padding(paddingValues = innerPadding)
        ) {
        }
    }
}

@Composable
fun TowerMapInCompose(paddingValues: PaddingValues) {
    AndroidViewBinding(
        ContainerTowerMapFragmentBinding::inflate
    ) {
        val fragment: TowerMapFragment? = towerMapFragmentContainerView.getFragment()
        fragment?.setPaddingInsets(paddingValues)
    }
}

@Composable
fun WifiSpectrumInCompose(
    wifiNetworks: WifiNetworkInfoList?,
    mainNavController: NavHostController
) {
    Scaffold(
        topBar = { TitleBar("Wi-Fi Spectrum") { mainNavController.navigateUp() } },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerWifiSpectrumFragmentBinding::inflate,
            modifier = Modifier.padding(paddingValues = innerPadding)
        ) {
            if (wifiNetworks != null) {
                val fragment: WifiSpectrumFragment? =
                    wifiSpectrumFragmentContainerView.getFragment()
                fragment?.setWifiNetworks(wifiNetworks)
            }
        }
    }
}

@Composable
fun WifiDetailsInCompose(paddingValues: PaddingValues, wifiNetwork: WifiNetwork?) {
    if (wifiNetwork != null) {
        AndroidViewBinding(
            ContainerWifiDetailsFragmentBinding::inflate,
            modifier = Modifier.padding(paddingValues = paddingValues)
        ) {
            val fragment: WifiDetailsFragment? = wifiDetailsFragmentContainerView.getFragment()
            fragment?.setWifiNetwork(wifiNetwork)
        }
    }
}

@Composable
fun BluetoothDetailsInCompose(
    paddingValues: PaddingValues,
    bluetoothRecordData: BluetoothRecordData?
) {
    if (bluetoothRecordData != null) {
        AndroidViewBinding(
            ContainerBluetoothDetailsFragmentBinding::inflate,
            modifier = Modifier.padding(paddingValues = paddingValues)
        ) {
            val fragment: BluetoothDetailsFragment? =
                bluetoothDetailsFragmentContainerView.getFragment()
            fragment?.setBluetoothData(bluetoothRecordData)
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
        Box(modifier = Modifier.padding(paddingValues = innerPadding)) {
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

@Composable
fun NsAnalyticsQrScannerInCompose(mainNavController: NavHostController) {
    Scaffold(
        topBar = { TitleBar("Scan NS Analytics QR Code") { mainNavController.navigateUp() } },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerNsAnalyticsQrScannerFragmentBinding::inflate,
            modifier = Modifier.padding(paddingValues = innerPadding)
        ) {
            // Fragment is defined in the layout XML
        }
    }
}
