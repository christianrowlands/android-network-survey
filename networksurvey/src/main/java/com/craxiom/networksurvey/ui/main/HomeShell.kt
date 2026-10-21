package com.craxiom.networksurvey.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.craxiom.networksurvey.ui.common.rememberUseRail
import com.craxiom.networksurvey.ui.signals.SignalsSubTab

/**
 * The four-tab shell: the tab graph inside the bottom bar or rail, the Signals sub-tab (kept
 * across tab switches, rotation and process death), and the tab a deep link asks for.
 *
 * @param homeTabRequest A tab route to select, set by a deep link or a notification tap.
 * @param onHomeTabHandled Called once [homeTabRequest] has been selected.
 * @param signals Draws the Signals tab with the selected sub-tab and a way to change it.
 */
@Composable
fun HomeShell(
    survey: @Composable () -> Unit,
    signals: @Composable (SignalsSubTab, (SignalsSubTab) -> Unit) -> Unit,
    map: @Composable () -> Unit,
    more: @Composable () -> Unit,
    homeTabRequest: String? = null,
    onHomeTabHandled: () -> Unit = {},
    useRail: Boolean = rememberUseRail(),
    navController: NavHostController = rememberNavController(),
) {
    var signalsSubTab by rememberSaveable { mutableStateOf(SignalsSubTab.CELLULAR) }

    // The graph is set while the NavHost composes, so wait for the first entry before navigating.
    val graphReady = navController.currentBackStackEntryAsState().value != null
    LaunchedEffect(homeTabRequest, graphReady) {
        if (homeTabRequest != null && graphReady) {
            navController.navigateToTab(homeTabRequest)
            onHomeTabHandled()
        }
    }

    HomeNavigation(navController, useRail) {
        HomeNavHost(
            navController = navController,
            survey = survey,
            signals = {
                signals(signalsSubTab) { signalsSubTab = it }
            },
            map = map,
            more = more,
        )
    }
}
