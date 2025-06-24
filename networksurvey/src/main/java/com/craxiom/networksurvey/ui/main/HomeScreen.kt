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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.Application
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
import com.craxiom.networksurvey.model.GnssType
import com.craxiom.networksurvey.ui.main.appbar.AppBar
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.util.LibUIUtils
import com.craxiom.networksurvey.util.PreferenceUtils

@Composable
fun HomeScreen(
    drawerState: DrawerState,
    mainNavController: NavHostController
) {
    var bottomNavSelectedItem by rememberSaveable { mutableIntStateOf(0) }
    val bottomNavController: NavHostController = rememberNavController()
    var currentScreen by remember { mutableStateOf<MainScreens>(MainScreens.Dashboard) }
    var currentGnssScreen by remember { mutableStateOf(GnssScreen.GNSS_DETAILS) }
    var showGnssFilterDialog by remember { mutableStateOf(false) }
    var showGnssSortDialog by remember { mutableStateOf(false) }

    bottomNavController.addOnDestinationChangedListener { _, destination, _ ->
        BottomNavItem().bottomNavigationItems().forEachIndexed { index, item ->
            // Only needed to update the selected item when hitting back from a bottom tab (which
            // takes you to the dashboard tab)
            if (destination.route == item.route) {
                bottomNavSelectedItem = index
            }
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                drawerState = drawerState,
                title = getAppBarTitle(currentScreen),
                appBarActions = getAppBarActions(
                    currentScreen,
                    currentGnssScreen,
                    mainNavController,
                    showGnssFilterDialog = { showGnssFilterDialog = it },
                    showGnssSortDialog = { showGnssSortDialog = true })
            )
        },
        bottomBar = {
            BottomNavigationBar(
                bottomNavController,
                onBottomNavigationItemSelected = { bottomNavSelectedItem = it },
                bottomNavSelectedItem
            )
        },
    ) { padding ->
        NavHost(
            bottomNavController,
            startDestination = MainScreens.Dashboard.route,
            modifier = Modifier.padding(paddingValues = padding)
        ) {
            composable(MainScreens.Dashboard.route) {
                currentScreen = MainScreens.Dashboard
                DashboardFragmentInCompose()
            }
            composable(MainScreens.Cellular.route) {
                currentScreen = MainScreens.Cellular
                CellularFragmentInCompose()
            }
            composable(MainScreens.Wifi.route) {
                currentScreen = MainScreens.Wifi
                WifiFragmentInCompose()
            }
            composable(MainScreens.Bluetooth.route) {
                currentScreen = MainScreens.Bluetooth
                BluetoothFragmentInCompose()
            }
            composable(MainScreens.Gnss.route) {
                currentScreen = MainScreens.Gnss
                GnssFragmentInCompose(onGnssScreenChange = { newScreen ->
                    currentGnssScreen = newScreen
                })
            }
        }
    }

    if (showGnssFilterDialog) {
        ShowSatsFilterDialog(
            onDismissRequest = { showGnssFilterDialog = false },
            onSave = { showGnssFilterDialog = false }
        )
    }

    if (showGnssSortDialog) {
        GnssSortByDialog(
            onDismissRequest = { showGnssSortDialog = false },
            onSave = { showGnssFilterDialog = false }
        )
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    onBottomNavigationItemSelected: (Int) -> Unit,
    bottomNavSelectedItem: Int
) {
    NavigationBar {
        BottomNavItem().bottomNavigationItems().forEachIndexed { index, navigationItem ->
            NavigationBarItem(
                selected = index == bottomNavSelectedItem,
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
                    onBottomNavigationItemSelected(index)
                    navController.navigate(navigationItem.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}

/**
 * Returns teh title resource ID that corresponds to the current screen
 */
fun getAppBarTitle(currentScreen: MainScreens): Int {
    return when (currentScreen) {
        MainScreens.Dashboard -> R.string.nav_dashboard
        MainScreens.Cellular -> R.string.cellular_title
        MainScreens.Wifi -> R.string.wifi_title
        MainScreens.Bluetooth -> R.string.bluetooth_title
        MainScreens.Gnss -> R.string.gnss_title
    }
}

@Composable
fun getAppBarActions(
    currentScreen: MainScreens,
    currentGnssScreen: GnssScreen,
    navController: NavController,
    showGnssFilterDialog: (Boolean) -> Unit,
    showGnssSortDialog: (Boolean) -> Unit
): List<AppBarAction> {
    return when (currentScreen) {
        MainScreens.Dashboard -> listOf(
            AppBarAction(
                icon = R.drawable.ic_survey_monitor,
                description = R.string.survey_monitor,
                onClick = {
                    navController.navigate(NavDrawerOption.SurveyMonitor.name)
                }
            )
        )

        MainScreens.Cellular -> listOf(
            AppBarAction(
                icon = android.R.drawable.ic_dialog_map,
                description = R.string.open_tower_map,
                onClick = {
                    navController.navigate(NavOption.TowerMap.name)
                }
            )
        )

        MainScreens.Wifi -> listOf(
            AppBarAction(
                icon = R.drawable.ic_spectrum_chart,
                description = R.string.open_wifi_spectrum,
                onClick = {
                    navController.navigate(NavOption.WifiSpectrum.name)
                }
            )
        )

        MainScreens.Gnss -> {
            return when (currentGnssScreen) {
                GnssScreen.GNSS_DETAILS -> listOf(
                    AppBarAction(
                        icon = R.drawable.ic_sort,
                        description = R.string.menu_option_sort_by,
                        onClick = { showGnssSortDialog(true) }
                    ),
                    AppBarAction(
                        icon = R.drawable.ic_filter,
                        description = R.string.menu_option_filter_content_description,
                        onClick = { showGnssFilterDialog(true) }
                    )
                )

                GnssScreen.GNSS_SKY_VIEW -> listOf(AppBarAction(
                    icon = R.drawable.ic_filter,
                    description = R.string.menu_option_filter_content_description,
                    onClick = { showGnssFilterDialog(true) }
                ))
            }
        }

        else -> emptyList()
    }
}

@Composable
fun ShowSatsFilterDialog(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val gnssTypes = GnssType.entries.toTypedArray()
    val len = gnssTypes.size

    // Retrieve the current filter from SharedPreferences
    val filter = PreferenceUtils.gnssFilter(context, Application.getPrefs())

    val items = Array(len) { index ->
        LibUIUtils.getGnssDisplayName(context, gnssTypes[index])
    }
    val checks = BooleanArray(len) { index ->
        filter.contains(gnssTypes[index])
    }

    // Display the GnssFilterDialog with the prepared items and initial checks
    GnssFilterDialog(
        initialItems = items,
        initialChecks = checks,
        onDismissRequest = onDismissRequest,
        onSave = onSave
    )
}

sealed class MainScreens(val route: String) {
    data object Dashboard : MainScreens("dashboard_route")
    data object Cellular : MainScreens("cellular_route")
    data object Wifi : MainScreens("wifi_route")
    data object Bluetooth : MainScreens("bluetooth_route")
    data object Gnss : MainScreens("gnss_route")
}

enum class GnssScreen {
    GNSS_DETAILS,
    GNSS_SKY_VIEW
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
fun GnssFragmentInCompose(onGnssScreenChange: (GnssScreen) -> Unit) {
    var fragment: MainGnssFragment? = null

    val lifecycleOwner = LocalLifecycleOwner.current
    val tabChangeObserver = rememberUpdatedState(newValue = { position: Int ->
        val newScreen = if (position == 0) GnssScreen.GNSS_DETAILS else GnssScreen.GNSS_SKY_VIEW
        onGnssScreenChange(newScreen)
    })

    DisposableEffect(lifecycleOwner) {
        val observer = Observer<Int> { position ->
            tabChangeObserver.value(position)
        }
        fragment?.tabChangeLiveData?.observe(lifecycleOwner, observer)
        onDispose {
            fragment?.tabChangeLiveData?.removeObserver(observer)
        }
    }

    AndroidViewBinding(ContainerGnssFragmentBinding::inflate) {
        fragment = gnssFragmentContainerView.getFragment<MainGnssFragment>()
    }
}
