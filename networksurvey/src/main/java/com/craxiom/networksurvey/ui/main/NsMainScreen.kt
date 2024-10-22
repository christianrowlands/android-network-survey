package com.craxiom.networksurvey.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.main.appdrawer.AppDrawerContent
import com.craxiom.networksurvey.ui.main.appdrawer.AppDrawerItemInfo
import com.craxiom.networksurvey.ui.main.appdrawer.NavDrawerOption
import com.craxiom.networksurvey.ui.main.appdrawer.mainGraph
import com.craxiom.networksurvey.util.NsTheme


@Composable
fun MainCompose(
    navController: NavHostController = rememberNavController(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    appVersion: String
) {
    // TODO Is this needed? HandleBackPress(navController)

    NsTheme {
        Scaffold { paddingValues ->
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    AppDrawerContent(
                        appVersion = appVersion,
                        drawerState = drawerState,
                        menuItems = DrawerParams.drawerButtons,
                        defaultPick = NavDrawerOption.None
                    ) { onUserPickedOption ->
                        when (onUserPickedOption) {
                            NavDrawerOption.None -> {
                                navController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }

                            NavDrawerOption.ServerConnection -> {
                                navController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }

                            NavDrawerOption.MqttBrokerConnection -> {
                                navController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }

                            NavDrawerOption.CellularCalculators -> {
                                navController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }

                            NavDrawerOption.Settings -> {
                                navController.navigate(onUserPickedOption.name) {
                                    popUpTo(NavDrawerOption.None.name)
                                }
                            }
                        }
                    }
                }
            ) {
                NavHost(
                    navController,
                    startDestination = NavRoutes.MainRoute.name
                ) {
                    mainGraph(drawerState, paddingValues = paddingValues)
                }
            }
        }
    }
}

@Composable
fun HandleBackPress(navController: NavController) {
    BackHandler {
        if (navController.currentDestination?.route == "dashboard") {
            // TODO Handle closing or moving the task to the back
        } else {
            navController.navigateUp()
        }
    }
}

enum class NavRoutes {
    MainRoute,
}

object DrawerParams {
    val drawerButtons = arrayListOf(
        // TODO Update all of these
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
            NavDrawerOption.Settings,
            R.string.settings,
            R.drawable.ic_settings,
            R.string.device_status_stream_description
        )
    )
}

@Preview
@Composable
fun MainActivityPreview() {
    MainCompose(appVersion = "1.0.0")
}