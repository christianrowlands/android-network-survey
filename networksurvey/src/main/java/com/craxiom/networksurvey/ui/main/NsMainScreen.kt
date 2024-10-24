package com.craxiom.networksurvey.ui.main

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.main.appdrawer.AppDrawerContent
import com.craxiom.networksurvey.ui.main.appdrawer.AppDrawerItemInfo
import com.craxiom.networksurvey.ui.main.appdrawer.NavDrawerOption
import com.craxiom.networksurvey.ui.main.appdrawer.NavOption
import com.craxiom.networksurvey.ui.main.appdrawer.mainGraph
import com.craxiom.networksurvey.util.NsTheme


@Composable
fun MainCompose(
    navController: NavHostController = rememberNavController(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    appVersion: String
) {
    // TODO Is this needed? HandleBackPress(navController)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val viewModel = viewModel<SharedViewModel>()
    LaunchedEffect(viewModel.navigateToQrCodeScanner) {
        viewModel.navigateToQrCodeScanner.observe(lifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                navController.navigate(NavOption.QrCodeScanner.name)
                viewModel.resetNavigationFlag()
            }
        }
    }

    NsTheme {
        Scaffold { paddingValues ->
            ModalNavigationDrawer(
                drawerState = drawerState,
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

                            NavDrawerOption.UserManual -> {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://networksurvey.app/manual")
                                )
                                context.startActivity(intent)
                            }

                            NavDrawerOption.MessagingDocs -> {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://messaging.networksurvey.app/")
                                )
                                context.startActivity(intent)
                            }

                            NavDrawerOption.ReportAnIssue -> {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/christianrowlands/android-network-survey/issues/new/choose")
                                )
                                context.startActivity(intent)
                            }

                            NavDrawerOption.GitHub -> {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/christianrowlands/android-network-survey")
                                )
                                context.startActivity(intent)
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