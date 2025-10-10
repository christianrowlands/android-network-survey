package com.craxiom.networksurvey.ui.main

import android.content.Intent
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.fragments.BLUETOOTH_DATA_KEY
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.main.appdrawer.AppDrawerContent
import com.craxiom.networksurvey.ui.main.appdrawer.AppDrawerItemInfo
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.BatteryOptimizationHelper


@Composable
fun MainCompose(
    mainNavController: NavHostController = rememberNavController(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    appVersion: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val viewModel = viewModel<SharedViewModel>()
    // Ensure we use activity context for proper intent launching
    val activity = context as? android.app.Activity
    val batteryOptimizationHelper = remember {
        BatteryOptimizationHelper(activity ?: context)
    }
    var showBatteryDialog by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.navigateToUploadSettings) {
        viewModel.navigateToUploadSettings.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                mainNavController.navigate(NavOption.UploadSettings.name)
                viewModel.resetNavigationFlag()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToTowerMapSettings) {
        viewModel.navigateToTowerMapSettings.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                mainNavController.navigate(NavOption.TowerMapSettings.name)
                viewModel.resetNavigationFlag()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToSsidExclusionList) {
        viewModel.navigateToSsidExclusionList.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                mainNavController.navigate(NavOption.SsidExclusionList.name)
                viewModel.resetSsidExclusionListNavigationFlag()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToAcknowledgments) {
        viewModel.navigateToAcknowledgments.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                mainNavController.navigate(NavOption.Acknowledgments.name)
                viewModel.resetAcknowledgmentsNavigationFlag()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToQrCodeScanner) {
        viewModel.navigateToQrCodeScanner.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                mainNavController.navigate(NavOption.QrCodeScanner.name)
                viewModel.resetNavigationFlag()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToQrCodeShare) {
        viewModel.navigateToQrCodeShare.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                mainNavController.navigate(NavOption.QrCodeShare.name)
                viewModel.resetNavigationFlag()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToTowerMap) {
        viewModel.navigateToTowerMap.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                mainNavController.navigate(NavOption.TowerMap.name)
                viewModel.resetNavigationFlag()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToWifiDetails) {
        viewModel.navigateToWifiDetails.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                mainNavController.currentBackStackEntry?.savedStateHandle?.set(
                    WifiNetwork.KEY,
                    viewModel.wifiNetwork
                )
                mainNavController.navigate(NavOption.WifiDetails.name)
                viewModel.resetNavigationFlag()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToBluetoothDetails) {
        viewModel.navigateToBluetoothDetails.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                mainNavController.currentBackStackEntry?.savedStateHandle?.set(
                    BLUETOOTH_DATA_KEY,
                    viewModel.bluetoothData
                )
                mainNavController.navigate(NavOption.BluetoothDetails.name)
                viewModel.resetNavigationFlag()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToMqttConnection) {
        viewModel.navigateToMqttConnection.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                if (viewModel.mqttConnectionSettings != null) {
                    mainNavController.currentBackStackEntry?.savedStateHandle?.set(
                        MqttConnectionSettings.KEY,
                        viewModel.mqttConnectionSettings
                    )
                }
                mainNavController.navigate(NavDrawerOption.MqttBrokerConnection.name)
                viewModel.resetNavigationFlag()
                viewModel.resetMqttConnectionSettings()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToSettings) {
        viewModel.navigateToSettings.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                mainNavController.navigate(NavDrawerOption.Settings.name)
                viewModel.resetNavigationFlag()
            }
        }
    }

    LaunchedEffect(viewModel.navigateToNsAnalyticsConnection) {
        viewModel.navigateToNsAnalyticsConnection.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                // Navigate back from QR scanner to NS Analytics connection screen
                mainNavController.navigate(NavDrawerOption.NsAnalyticsConnection.name) {
                    popUpTo(NavDrawerOption.None.name)
                }
                viewModel.resetNavigationFlag()
            }
        }
    }

    // Handle battery optimization dialog trigger from SharedViewModel
    LaunchedEffect(viewModel.showBatteryOptimizationDialog) {
        viewModel.showBatteryOptimizationDialog.observe(lifecycleOwner) { shouldShow ->
            if (shouldShow) {
                if (batteryOptimizationHelper.shouldPromptForBatteryOptimization()) {
                    showBatteryDialog = true
                }
                viewModel.resetBatteryOptimizationDialogFlag()
            }
        }
    }

    NsTheme {
        // Show battery optimization dialog on first launch
        LaunchedEffect(Unit) {
            if (batteryOptimizationHelper.shouldShowFirstTimePrompt()) {
                showBatteryDialog = true
            }
        }

        // Show battery dialog when triggered
        if (showBatteryDialog) {
            BatteryOptimizationDialog(
                onDismiss = { showBatteryDialog = false },
                onGoToSettings = {
                    showBatteryDialog = false
                    batteryOptimizationHelper.openBatteryOptimizationSettings()
                },
                batteryOptimizationHelper = batteryOptimizationHelper
            )
        }

        Scaffold { paddingValues ->
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    AppDrawerContent(
                        appVersion = appVersion,
                        drawerState = drawerState,
                        menuItems = DrawerParams.drawerButtons,
                        externalLinks = DrawerParams.externalDrawerLinks,
                        defaultPick = NavDrawerOption.None
                    ) { onUserPickedOption ->
                        when (onUserPickedOption) {
                            NavDrawerOption.None -> {
                                mainNavController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }


                            NavDrawerOption.NsAnalyticsConnection -> {
                                mainNavController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }

                            NavDrawerOption.ServerConnection -> {
                                mainNavController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }

                            NavDrawerOption.MqttBrokerConnection -> {
                                mainNavController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }

                            NavDrawerOption.CellularCalculators -> {
                                mainNavController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }

                            NavDrawerOption.SurveyMonitor -> {
                                mainNavController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }

                            NavDrawerOption.Settings -> {
                                mainNavController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }

                            NavDrawerOption.UserManual -> {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://networksurvey.app/manual".toUri()
                                )
                                context.startActivity(intent)
                            }

                            NavDrawerOption.MessagingDocs -> {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://messaging.networksurvey.app/".toUri()
                                )
                                context.startActivity(intent)
                            }

                            NavDrawerOption.ReportAnIssue -> {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/christianrowlands/android-network-survey/issues/new/choose".toUri()
                                )
                                context.startActivity(intent)
                            }

                            NavDrawerOption.GitHub -> {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/christianrowlands/android-network-survey".toUri()
                                )
                                context.startActivity(intent)
                            }
                        }
                    }
                }
            ) {
                NavHost(
                    mainNavController,
                    startDestination = NavRoutes.MainRoute.name
                ) {
                    mainGraph(
                        drawerState, paddingValues = paddingValues,
                        mainNavController = mainNavController,
                        sharedViewModel = viewModel
                    )
                }
            }
        }
    }
}

enum class NavRoutes {
    MainRoute,
}

object DrawerParams {
    val drawerButtons = arrayListOf(
        AppDrawerItemInfo(
            NavDrawerOption.NsAnalyticsConnection,
            R.string.ns_analytics,
            R.drawable.ic_ns_analytics,
            R.string.ns_analytics_description
        ),
        AppDrawerItemInfo(
            NavDrawerOption.ServerConnection,
            R.string.grpc_connection_title,
            R.drawable.connection_icon,
            R.string.grpc_connection_description
        ),
        AppDrawerItemInfo(
            NavDrawerOption.MqttBrokerConnection,
            R.string.mqtt_connection_title_full,
            R.drawable.ic_cloud_connection,
            R.string.device_status_stream_description
        ),
        AppDrawerItemInfo(
            NavDrawerOption.CellularCalculators,
            R.string.cellular_calculators,
            R.drawable.ic_calculator,
            R.string.device_status_stream_description
        ),
        AppDrawerItemInfo(
            NavDrawerOption.SurveyMonitor,
            R.string.survey_monitor,
            R.drawable.ic_survey_monitor,
            R.string.survey_monitor_description
        ),
        AppDrawerItemInfo(
            NavDrawerOption.Settings,
            R.string.settings,
            R.drawable.ic_settings,
            R.string.device_status_stream_description
        )
    )

    val externalDrawerLinks = arrayListOf(
        AppDrawerItemInfo(
            NavDrawerOption.UserManual,
            R.string.manual,
            R.drawable.ic_user_manual,
            R.string.manual
        ),
        AppDrawerItemInfo(
            NavDrawerOption.MessagingDocs,
            R.string.messaging_docs,
            R.drawable.ic_schema,
            R.string.messaging_docs
        ),
        AppDrawerItemInfo(
            NavDrawerOption.ReportAnIssue,
            R.string.report_issue,
            R.drawable.ic_bug,
            R.string.report_issue
        ),
        AppDrawerItemInfo(
            NavDrawerOption.GitHub,
            R.string.github,
            R.drawable.ic_github,
            R.string.github
        )
    )
}

@Preview
@Composable
fun MainActivityPreview() {
    MainCompose(appVersion = "1.0.0")
}