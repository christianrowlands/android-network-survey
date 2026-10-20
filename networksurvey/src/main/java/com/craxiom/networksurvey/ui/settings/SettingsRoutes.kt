package com.craxiom.networksurvey.ui.settings

import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.craxiom.networksurvey.ui.main.NavDrawerOption
import com.craxiom.networksurvey.ui.main.NavOption
import com.craxiom.networksurvey.ui.main.SharedViewModel

/** The Settings page and the two pages it opens (upload settings and map settings). */
fun NavGraphBuilder.settingsPages(mainNavController: NavHostController, sharedViewModel: SharedViewModel) {
    val openOrganization = { mainNavController.navigate(NavOption.OrganizationSettings.name) }

    composable(NavDrawerOption.Settings.name) {
        val navigation = remember(mainNavController, sharedViewModel) {
            SettingsNavigation(
                onBack = { mainNavController.navigateUp() },
                onOpenNsAnalytics = { mainNavController.navigate(NavDrawerOption.NsAnalyticsConnection.name) },
                onOpenUploads = { mainNavController.navigate(NavOption.UploadSettings.name) },
                onOpenMapSettings = { mainNavController.navigate(NavOption.TowerMapSettings.name) },
                onOpenSsidExclusions = { mainNavController.navigate(NavOption.SsidExclusionList.name) },
                onOpenOrganization = openOrganization,
                onBackgroundLocationRationale = sharedViewModel::showBackgroundLocationRationale,
            )
        }
        SettingsScreen(navigation)
    }

    composable(NavOption.UploadSettings.name) {
        UploadSettingsScreen(onBack = { mainNavController.navigateUp() }, onOpenOrganization = openOrganization)
    }

    composable(NavOption.TowerMapSettings.name) {
        MapSettingsScreen(
            onBack = { mainNavController.navigateUp() },
            onOpenProviderColors = { mainNavController.navigate(NavOption.ProviderColorOverrides.name) },
        )
    }
}
