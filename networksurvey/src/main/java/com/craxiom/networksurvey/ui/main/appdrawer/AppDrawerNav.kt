package com.craxiom.networksurvey.ui.main.appdrawer

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.craxiom.networksurvey.databinding.ContainerGrpcFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerMqttFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerMqttQrCodeScannerFragmentBinding
import com.craxiom.networksurvey.databinding.ContainerSettingsFragmentBinding
import com.craxiom.networksurvey.fragments.MqttFragment
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
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
        composable(
            route = "${NavDrawerOption.MqttBrokerConnection.name}?${MqttConnectionSettings.KEY}={mqttConnectionSettings}",
            arguments = listOf(navArgument("mqttConnectionSettings") {
                type = NavType.ParcelableType(MqttConnectionSettings::class.java)
                nullable = true  // Allow this argument to be nullable
            })
        ) { backStackEntry ->
            val mqttConnectionSettings =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    backStackEntry.arguments?.getParcelable(
                        "mqttConnectionSettings",
                        MqttConnectionSettings::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    backStackEntry.arguments?.getParcelable(MqttConnectionSettings.KEY)
                }
            MqttFragmentInCompose(
                paddingValues = paddingValues,
                mqttConnectionSettings = mqttConnectionSettings
            )
        }

        composable(NavDrawerOption.CellularCalculators.name) {
            Box(modifier = Modifier.padding(paddingValues = paddingValues)) {
                CalculatorScreen(viewModel = viewModel())
            }
        }

        composable(NavDrawerOption.Settings.name) {
            SettingsFragmentInCompose(paddingValues)
        }

        // --------- Deeper navigation (beyond the nav drawer) ---------
        composable(NavOption.QrCodeScanner.name) {
            QrCodeScannerInCompose(paddingValues)
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
    QrCodeScanner
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
fun MqttFragmentInCompose(
    paddingValues: PaddingValues,
    mqttConnectionSettings: MqttConnectionSettings?
) {
    AndroidViewBinding(
        ContainerMqttFragmentBinding::inflate,
        modifier = Modifier.padding(paddingValues)
    ) {
        val fragment = mqttFragmentContainerView.getFragment<MqttFragment>()
        fragment.setMqttConnectionSettings(mqttConnectionSettings)
    }
}

@Composable
fun SettingsFragmentInCompose(paddingValues: PaddingValues) {
    AndroidViewBinding(
        ContainerSettingsFragmentBinding::inflate,
        modifier = Modifier.padding(paddingValues = paddingValues)
    ) {
    }
}

@Composable
fun QrCodeScannerInCompose(paddingValues: PaddingValues) {
    AndroidViewBinding(
        ContainerMqttQrCodeScannerFragmentBinding::inflate,
        modifier = Modifier.padding(paddingValues = paddingValues)
    ) {
    }
}
