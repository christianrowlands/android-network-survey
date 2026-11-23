package com.craxiom.networksurvey.ui.privacy

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.craxiom.networksurvey.ui.common.MapPoint
import com.craxiom.networksurvey.ui.main.NavOption
import com.craxiom.networksurvey.util.PreferenceUtils

/** The editor's route, with the optional zone id (absent for a new zone). */
private val EDITOR_ROUTE = "${NavOption.PrivacyZoneEditor.name}?${PrivacyZoneEditorViewModel.ZONE_ID_ARG}={${PrivacyZoneEditorViewModel.ZONE_ID_ARG}}"

/** Opens the zone editor for the zone with [zoneId], or for a new zone when it is null. */
fun NavHostController.navigateToPrivacyZoneEditor(zoneId: String?) {
    val route = zoneId?.let { "${NavOption.PrivacyZoneEditor.name}?${PrivacyZoneEditorViewModel.ZONE_ID_ARG}=$it" }
        ?: NavOption.PrivacyZoneEditor.name
    navigate(route) { launchSingleTop = true }
}

/** The Privacy zones list and the zone editor. */
fun NavGraphBuilder.privacyZonePages(mainNavController: NavHostController) {
    composable(NavOption.PrivacyZones.name) {
        PrivacyZonesScreen(
            viewModel = viewModel(),
            onBack = { mainNavController.navigateUp() },
            onAdd = { mainNavController.navigateToPrivacyZoneEditor(null) },
            onEdit = { mainNavController.navigateToPrivacyZoneEditor(it) },
            onOpenSsidExclusions = { mainNavController.navigate(NavOption.SsidExclusionList.name) },
        )
    }

    composable(
        route = EDITOR_ROUTE,
        arguments = listOf(
            navArgument(PrivacyZoneEditorViewModel.ZONE_ID_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
    ) {
        val context = LocalContext.current
        // Where the Map tab last was, for a new zone while the phone's location is unknown
        val fallbackCenter = remember {
            PreferenceUtils.getLatLngBoundsFromPreferences(context)?.center?.let { MapPoint(it.latitude, it.longitude) }
        }
        PrivacyZoneEditorScreen(
            viewModel = viewModel(),
            fallbackCenter = fallbackCenter,
            onBack = { mainNavController.navigateUp() },
            onDone = { mainNavController.navigateUp() },
        )
    }
}
