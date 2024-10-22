package com.craxiom.networksurvey.ui.main.appdrawer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.craxiom.networksurvey.databinding.ContainerSettingsFragmentBinding
import com.craxiom.networksurvey.fragments.SettingsFragment
import com.craxiom.networksurvey.ui.main.HomeScreen
import com.craxiom.networksurvey.ui.main.NavRoutes

fun NavGraphBuilder.mainGraph(
    drawerState: DrawerState,
    paddingValues: PaddingValues
) {
    navigation(startDestination = NavDrawerOption.None.name, route = NavRoutes.MainRoute.name) {
        composable(NavDrawerOption.None.name) {
            HomeScreen(drawerState)
        }
        composable(NavDrawerOption.ServerConnection.name) {
            // TODO finish this
        }
        composable(NavDrawerOption.MqttBrokerConnection.name) {
            // TODO finish this
        }
        composable(NavDrawerOption.CellularCalculators.name) {
            // TODO finish this
        }

        composable(NavDrawerOption.Settings.name) {
            SettingsFragmentInCompose(paddingValues)
        }
    }
}

enum class NavDrawerOption {
    None,
    ServerConnection,
    MqttBrokerConnection,
    CellularCalculators,
    Settings,
    // TODO Add the other options
}

@Composable
fun SettingsFragmentInCompose(paddingValues: PaddingValues) {
    AndroidViewBinding(
        ContainerSettingsFragmentBinding::inflate,
        modifier = Modifier.padding(paddingValues = paddingValues)
    ) {
        val fragment = settingsFragmentContainerView.getFragment<SettingsFragment>()
    }
}
