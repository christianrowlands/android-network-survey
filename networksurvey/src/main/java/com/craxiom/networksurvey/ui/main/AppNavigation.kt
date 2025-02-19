package com.craxiom.networksurvey.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.databinding.ContainerBluetoothDetailsFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerGrpcFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerMqttFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerMqttQrCodeScannerFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerMqttQrCodeShareFragmentBinding
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
import com.craxiom.networksurvey.ui.cellular.CalculatorScreen
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.wifi.model.WifiNetworkInfoList

fun NavGraphBuilder.mainGraph(
    drawerState: DrawerState,
    paddingValues: PaddingValues,
    mainNavController: NavHostController,
    sharedViewModel: SharedViewModel
) {
    navigation(startDestination = NavDrawerOption.None.name, route = NavRoutes.MainRoute.name) {
        composable(NavDrawerOption.None.name) {
            HomeScreen(drawerState, mainNavController = mainNavController)
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

        composable(NavDrawerOption.Settings.name) {
            SettingsFragmentInCompose(mainNavController)
        }

        // --------- Deeper navigation (beyond the nav drawer) --------- //

        composable(NavOption.UploadSettings.name) {
            UploadSettingsFragmentInCompose(mainNavController)
        }

        composable(NavOption.TowerMapSettings.name) {
            TowerMapSettingsFragmentInCompose(mainNavController)
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
    }
}

enum class NavDrawerOption {
    None,
    ServerConnection,
    MqttBrokerConnection,
    CellularCalculators,
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
    BluetoothDetails
}

@Composable
fun GrpcFragmentInCompose(mainNavController: NavHostController) {
    Scaffold(
        topBar = { TitleBar("Server Connection") { mainNavController.navigateUp() } },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerGrpcFragmentBinding::inflate,
            modifier = Modifier.padding(innerPadding)
        ) {
        }
    }
}

@Composable
fun MqttFragmentInCompose(
    mqttConnectionSettings: MqttConnectionSettings?,
    mainNavController: NavHostController
) {
    Scaffold(
        topBar = { TitleBar("MQTT Broker") { mainNavController.navigateUp() } },
    ) { innerPadding ->
        AndroidViewBinding(
            ContainerMqttFragmentBinding::inflate,
            modifier = Modifier.padding(innerPadding)
        ) {
            val fragment = mqttFragmentContainerView.getFragment<MqttFragment>()
            fragment.setMqttConnectionSettings(mqttConnectionSettings)
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
fun UploadSettingsFragmentInCompose(mainNavController: NavHostController) {
    Scaffold(
        topBar = { TitleBar("Upload Settings") { mainNavController.navigateUp() } },
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
        val fragment = towerMapFragmentContainerView.getFragment<TowerMapFragment>()
        fragment.setPaddingInsets(paddingValues)
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
                val fragment = wifiSpectrumFragmentContainerView.getFragment<WifiSpectrumFragment>()
                fragment.setWifiNetworks(wifiNetworks)
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
            val fragment = wifiDetailsFragmentContainerView.getFragment<WifiDetailsFragment>()
            fragment.setWifiNetwork(wifiNetwork)
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
            val fragment =
                bluetoothDetailsFragmentContainerView.getFragment<BluetoothDetailsFragment>()
            fragment.setBluetoothData(bluetoothRecordData)
        }
    }
}
