package com.craxiom.networksurvey.ui.scout

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.craxiom.networksurvey.ui.main.NavOption
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.scout.firmware.FirmwareUpdateScreen

/**
 * The NS Scout page and its firmware update in the main graph. The page is opened from More >
 * Hardware, the Survey tab's Scout row and plates, Signals > Wi-Fi and the Wi-Fi throttle notice
 * (all through [SharedViewModel.triggerNavigationToScout] or the route). The firmware update is
 * pushed from the page, the "Needs a firmware update" plate and the Signals blocked state
 * ([SharedViewModel.triggerNavigationToScoutFirmware]).
 */
fun NavGraphBuilder.scoutPages(mainNavController: NavHostController) {
    composable(NavOption.NsScout.name) {
        ScoutScreen(
            onBack = { mainNavController.navigateUp() },
            onOpenFirmware = { mainNavController.navigate(NavOption.ScoutFirmware.name) { launchSingleTop = true } },
        )
    }
    composable(NavOption.ScoutFirmware.name) {
        FirmwareUpdateScreen(
            onBack = { mainNavController.navigateUp() },
            onOpenScoutPage = mainNavController::openScoutPage,
        )
    }
}

/** Opens the NS Scout page: back to it when it is already on the back stack, so it is never there twice. */
internal fun NavController.openScoutPage() {
    if (!popBackStack(NavOption.NsScout.name, inclusive = false)) {
        navigate(NavOption.NsScout.name) { launchSingleTop = true }
    }
}
