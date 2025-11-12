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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.fragments.BLUETOOTH_DATA_KEY
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.main.appdrawer.AppDrawerContent
import com.craxiom.networksurvey.ui.main.appdrawer.AppDrawerItemInfo
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.BatteryOptimizationHelper
import com.craxiom.networksurvey.util.MdmUtils


@Composable
fun MainCompose(
    mainNavController: NavHostController = rememberNavController(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    appVersion: String
) {
    val context = LocalContext.current

    val viewModel = viewModel<SharedViewModel>()
    // Ensure we use activity context for proper intent launching
    val activity = context as? android.app.Activity
    val batteryOptimizationHelper = remember {
        BatteryOptimizationHelper(activity ?: context)
    }
    var showBatteryDialog by remember { mutableStateOf(false) }

    // Observe navigation events using modern Compose StateFlow pattern
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is NavigationEvent.UploadSettings -> {
                    mainNavController.navigate(NavOption.UploadSettings.name)
                }

                is NavigationEvent.TowerMapSettings -> {
                    mainNavController.navigate(NavOption.TowerMapSettings.name)
                }

                is NavigationEvent.SsidExclusionList -> {
                    mainNavController.navigate(NavOption.SsidExclusionList.name)
                }

                is NavigationEvent.Acknowledgments -> {
                    mainNavController.navigate(NavOption.Acknowledgments.name)
                }

                is NavigationEvent.QrCodeScanner -> {
                    mainNavController.currentBackStackEntry?.savedStateHandle?.set(
                        MqttConnectionSettings.KEY,
                        event.mqttConnectionSettings
                    )
                    mainNavController.navigate(NavOption.QrCodeScanner.name)
                }

                is NavigationEvent.QrCodeShare -> {
                    mainNavController.currentBackStackEntry?.savedStateHandle?.set(
                        MqttConnectionSettings.KEY,
                        event.mqttConnectionSettings
                    )
                    mainNavController.navigate(NavOption.QrCodeShare.name)
                }

                is NavigationEvent.TowerMap -> {
                    mainNavController.currentBackStackEntry?.savedStateHandle?.set(
                        ServingCellInfo.KEY,
                        event.servingCellInfo
                    )
                    mainNavController.navigate(NavOption.TowerMap.name)
                }

                is NavigationEvent.WifiDetails -> {
                    mainNavController.currentBackStackEntry?.savedStateHandle?.set(
                        WifiNetwork.KEY,
                        event.wifiNetwork
                    )
                    mainNavController.navigate(NavOption.WifiDetails.name)
                }

                is NavigationEvent.BluetoothDetails -> {
                    mainNavController.currentBackStackEntry?.savedStateHandle?.set(
                        BLUETOOTH_DATA_KEY,
                        event.bluetoothData
                    )
                    mainNavController.navigate(NavOption.BluetoothDetails.name)
                }

                is NavigationEvent.MqttConnection -> {
                    event.mqttConnectionSettings?.let { settings ->
                        mainNavController.currentBackStackEntry?.savedStateHandle?.set(
                            MqttConnectionSettings.KEY,
                            settings
                        )
                    }
                    mainNavController.navigate(NavDrawerOption.MqttBrokerConnection.name)
                }

                is NavigationEvent.Settings -> {
                    mainNavController.navigate(NavDrawerOption.Settings.name)
                }

                is NavigationEvent.NsAnalyticsConnection -> {
                    // Navigate back from QR scanner to NS Analytics connection screen
                    mainNavController.navigate(NavDrawerOption.NsAnalyticsConnection.name) {
                        popUpTo(NavDrawerOption.None.name)
                    }
                }
            }
            // Clear the event after handling to prevent re-processing
            viewModel.clearNavigationEvent()
        }
    }

    // Observe dialog events using modern Compose StateFlow pattern
    val dialogEvent by viewModel.dialogEvent.collectAsStateWithLifecycle()
    LaunchedEffect(dialogEvent) {
        dialogEvent?.let { event ->
            when (event) {
                is DialogEvent.BatteryOptimization -> {
                    if (batteryOptimizationHelper.shouldPromptForBatteryOptimization()) {
                        showBatteryDialog = true
                    }
                }
            }
            // Clear the event after handling
            viewModel.clearDialogEvent()
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
                        menuItems = DrawerParams.drawerButtons(context),
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
    fun drawerButtons(context: android.content.Context): List<AppDrawerItemInfo<NavDrawerOption>> {
        val buttons = arrayListOf(
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

        // Filter out NS Analytics if not allowed via MDM
        return if (MdmUtils.isNsAnalyticsAllowed(context)) {
            buttons
        } else {
            buttons.filter { it.drawerOption != NavDrawerOption.NsAnalyticsConnection }
        }
    }

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