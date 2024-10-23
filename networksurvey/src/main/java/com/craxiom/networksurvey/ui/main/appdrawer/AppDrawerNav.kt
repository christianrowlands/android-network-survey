package com.craxiom.networksurvey.ui.main.appdrawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.craxiom.networksurvey.databinding.ContainerGrpcFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerMqttFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerSettingsFragmentBinding
import com.craxiom.networksurvey.fragments.SettingsFragment
import com.craxiom.networksurvey.ui.cellular.CalculatorScreen
import com.craxiom.networksurvey.ui.main.HomeScreen
import com.craxiom.networksurvey.ui.main.NavRoutes

fun NavGraphBuilder.mainGraph(
    drawerState: DrawerState,
    paddingValues: PaddingValues
) {
    navigation(startDestination = NavDrawerOption.None.name, route = NavRoutes.MainRoute.name) {
        // TODO Need to add a header like the old display for all of these
        composable(NavDrawerOption.None.name) {
            HomeScreen(drawerState)
        }
        composable(NavDrawerOption.ServerConnection.name) {
            GrpcFragmentInCompose(paddingValues)
        }
        composable(NavDrawerOption.MqttBrokerConnection.name) {
            MqttFragmentInCompose(paddingValues)
        }
        composable(NavDrawerOption.CellularCalculators.name) {
            Box(modifier = Modifier.padding(paddingValues = paddingValues)) {
                CalculatorScreen(viewModel = viewModel())
            }
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

    // External Links
    UserManual,
    MessagingDocs,
    ReportAnIssue,
    GitHub
}

@Composable
fun GrpcFragmentInCompose(paddingValues: PaddingValues) {
    AndroidViewBinding(
        ContainerGrpcFragmentBinding::inflate,
        modifier = Modifier.padding(paddingValues = paddingValues)
    ) {
    }
}

@Composable
fun MqttFragmentInCompose(paddingValues: PaddingValues) {
    AndroidViewBinding(
        ContainerMqttFragmentBinding::inflate,
        modifier = Modifier.padding(paddingValues = paddingValues)
    ) {
    }
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
