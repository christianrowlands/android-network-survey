package com.craxiom.networksurvey.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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
import com.craxiom.networksurvey.databinding.ContainerGnssFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerWifiFragmentBinding
import com.craxiom.networksurvey.fragments.BluetoothFragment
import com.craxiom.networksurvey.fragments.MainCellularFragment
import com.craxiom.networksurvey.fragments.MainGnssFragment
import com.craxiom.networksurvey.fragments.WifiNetworksFragment
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.util.LibUIUtils
import com.craxiom.networksurvey.ui.dashboard.DashboardScreen
import com.craxiom.networksurvey.ui.main.appbar.AppBar
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.util.PreferenceUtils

@Composable
fun HomeScreen(
    drawerState: DrawerState,
    mainNavController: NavHostController,
    sharedViewModel: SharedViewModel
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
                DashboardScreen(sharedViewModel = sharedViewModel)
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

/**
 * Upper bound applied to the system fontScale when rendering bottom-nav labels. Keeps the labels
 * legible without letting them grow large enough to wrap onto a second line.
 */
private const val LABEL_MAX_FONT_SCALE = 1.15f

/**
 * System fontScale at and above which the bottom-nav labels are hidden entirely (icons only). This
 * is intentionally past Android's "Largest" preset so only extreme accessibility settings hit the
 * icons-only state.
 */
private const val LABEL_HIDE_FONT_SCALE_THRESHOLD = 1.5f

@Composable
fun BottomNavigationBar(
    navController: NavController,
    onBottomNavigationItemSelected: (Int) -> Unit,
    bottomNavSelectedItem: Int
) {
    val currentDensity = LocalDensity.current
    val systemFontScale = currentDensity.fontScale
    val cappedFontScale = systemFontScale.coerceAtMost(LABEL_MAX_FONT_SCALE)
    val showLabels = systemFontScale < LABEL_HIDE_FONT_SCALE_THRESHOLD
    val cappedDensity = remember(currentDensity, cappedFontScale) {
        Density(currentDensity.density, cappedFontScale)
    }

    NavigationBar {
        BottomNavItem().bottomNavigationItems().forEachIndexed { index, navigationItem ->
            val label = stringResource(id = navigationItem.labelRes)
            NavigationBarItem(
                selected = index == bottomNavSelectedItem,
                label = if (showLabels) {
                    {
                        CompositionLocalProvider(LocalDensity provides cappedDensity) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else null,
                icon = {
                    Icon(
                        painter = painterResource(id = navigationItem.icon),
                        contentDescription = label
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
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
            ),
            AppBarAction(
                icon = R.drawable.ic_ns_analytics,
                description = R.string.ns_analytics,
                onClick = {
                    navController.navigate(NavDrawerOption.NsAnalyticsConnection.name)
                }
            )
        )

        MainScreens.Cellular -> listOf(
            AppBarAction(
                icon = R.drawable.ic_survey_monitor,
                description = R.string.survey_monitor,
                onClick = {
                    navController.navigate(NavDrawerOption.SurveyMonitor.name)
                }
            ),
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
            ),
            AppBarAction(
                icon = R.drawable.ic_filter,
                description = R.string.ssid_exclusion_list_title,
                onClick = {
                    navController.navigate(NavOption.SsidExclusionList.name)
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

                GnssScreen.GNSS_SKY_VIEW -> listOf(
                    AppBarAction(
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
    @StringRes val labelRes: Int = R.string.nav_dashboard,
    @DrawableRes val icon: Int = R.drawable.ic_dashboard,
    val route: String = ""
) {
    fun bottomNavigationItems(): List<BottomNavItem> {
        return listOf(
            BottomNavItem(
                labelRes = R.string.nav_dashboard,
                icon = R.drawable.ic_dashboard,
                route = MainScreens.Dashboard.route
            ),
            BottomNavItem(
                labelRes = R.string.cellular_title,
                icon = R.drawable.ic_cellular,
                route = MainScreens.Cellular.route
            ),
            BottomNavItem(
                labelRes = R.string.wifi_title,
                icon = R.drawable.ic_wifi,
                route = MainScreens.Wifi.route
            ),
            BottomNavItem(
                labelRes = R.string.bluetooth_title,
                icon = R.drawable.ic_bluetooth,
                route = MainScreens.Bluetooth.route
            ),
            BottomNavItem(
                labelRes = R.string.gnss_title,
                icon = R.drawable.ic_gnss,
                route = MainScreens.Gnss.route
            ),
        )
    }
}

@Composable
fun CellularFragmentInCompose() {
    AndroidViewBinding(ContainerCellularFragmentBinding::inflate) {
        cellularFragmentContainerView.getFragment<MainCellularFragment>()
    }
}

@Composable
fun WifiFragmentInCompose() {
    AndroidViewBinding(ContainerWifiFragmentBinding::inflate) {
        wifiFragmentContainerView.getFragment<WifiNetworksFragment>()
    }
}

@Composable
fun BluetoothFragmentInCompose() {
    AndroidViewBinding(ContainerBluetoothFragmentBinding::inflate) {
        bluetoothFragmentContainerView.getFragment<BluetoothFragment>()
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
