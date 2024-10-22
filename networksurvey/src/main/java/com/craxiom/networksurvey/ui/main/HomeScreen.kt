package com.craxiom.networksurvey.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.databinding.ContainerBluetoothFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerCellularFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerDashboardFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerGnssFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerWifiFragmentBinding
import com.craxiom.networksurvey.fragments.BluetoothFragment
import com.craxiom.networksurvey.fragments.DashboardFragment
import com.craxiom.networksurvey.fragments.MainCellularFragment
import com.craxiom.networksurvey.fragments.MainGnssFragment
import com.craxiom.networksurvey.fragments.WifiNetworksFragment
import com.craxiom.networksurvey.ui.main.appbar.AppBar

@Composable
fun HomeScreen(
    drawerState: DrawerState
) {
    val navController: NavHostController = rememberNavController()

    Scaffold(
        topBar = { AppBar(drawerState = drawerState) },
        bottomBar = {
            BottomNavigationBar(navController)
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = MainScreens.Dashboard.route,
            modifier = Modifier.padding(paddingValues = padding)
        ) {
            composable(MainScreens.Dashboard.route) {
                DashboardFragmentInCompose()
            }
            composable(MainScreens.Cellular.route) {
                CellularFragmentInCompose()
            }
            composable(MainScreens.Wifi.route) {
                WifiFragmentInCompose()
            }
            composable(MainScreens.Bluetooth.route) {
                BluetoothFragmentInCompose()
            }
            composable(MainScreens.Gnss.route) {
                GnssFragmentInCompose()
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    var navigationSelectedItem by remember {
        mutableStateOf(0)
    }

    NavigationBar {
        //getting the list of bottom navigation items for our data class
        BottomNavItem().bottomNavigationItems().forEachIndexed { index, navigationItem ->

            //iterating all items with their respective indexes
            NavigationBarItem(
                selected = index == navigationSelectedItem,
                label = {
                    Text(navigationItem.label)
                },
                icon = {
                    Icon(
                        painter = painterResource(id = navigationItem.icon),
                        contentDescription = navigationItem.label
                    )
                },
                onClick = {
                    navigationSelectedItem = index
                    navController.navigate(navigationItem.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

sealed class MainScreens(val route: String) {
    data object Dashboard : MainScreens("dashboard_route")
    data object Cellular : MainScreens("cellular_route")
    data object Wifi : MainScreens("wifi_route")
    data object Bluetooth : MainScreens("bluetooth_route")
    data object Gnss : MainScreens("gnss_route")
}

data class BottomNavItem(
    val label: String = "",
    @DrawableRes val icon: Int = R.drawable.ic_dashboard,
    val route: String = ""
) {
    fun bottomNavigationItems(): List<BottomNavItem> {
        return listOf(
            BottomNavItem(
                label = "Dashboard",
                icon = R.drawable.ic_dashboard,
                route = MainScreens.Dashboard.route
            ),
            BottomNavItem(
                label = "Cellular",
                icon = R.drawable.ic_cellular,
                route = MainScreens.Cellular.route
            ),
            BottomNavItem(
                label = "Wi-Fi",
                icon = R.drawable.ic_wifi,
                route = MainScreens.Wifi.route
            ),
            BottomNavItem(
                label = "Bluetooth",
                icon = R.drawable.ic_bluetooth,
                route = MainScreens.Bluetooth.route
            ),
            BottomNavItem(
                label = "GNSS",
                icon = R.drawable.ic_gnss,
                route = MainScreens.Gnss.route
            ),
        )
    }
}

@Composable
fun DashboardFragmentInCompose() {
    AndroidViewBinding(ContainerDashboardFragmentBinding::inflate) {
        val fragment = dashboardFragmentContainerView.getFragment<DashboardFragment>()
    }
}

@Composable
fun CellularFragmentInCompose() {
    AndroidViewBinding(ContainerCellularFragmentBinding::inflate) {
        val fragment = cellularFragmentContainerView.getFragment<MainCellularFragment>()
    }
}

@Composable
fun WifiFragmentInCompose() {
    AndroidViewBinding(ContainerWifiFragmentBinding::inflate) {
        val fragment = wifiFragmentContainerView.getFragment<WifiNetworksFragment>()
    }
}

@Composable
fun BluetoothFragmentInCompose() {
    AndroidViewBinding(ContainerBluetoothFragmentBinding::inflate) {
        val fragment = bluetoothFragmentContainerView.getFragment<BluetoothFragment>()
    }
}

@Composable
fun GnssFragmentInCompose() {
    AndroidViewBinding(ContainerGnssFragmentBinding::inflate) {
        val fragment = gnssFragmentContainerView.getFragment<MainGnssFragment>()
    }
}
