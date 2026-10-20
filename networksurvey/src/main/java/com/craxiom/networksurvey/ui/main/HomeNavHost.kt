package com.craxiom.networksurvey.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

/**
 * The tab graph: one destination per tab, each drawing the slot it is given. The slots are
 * parameters so the navigation can be tested with stand-ins for the real screens.
 */
@Composable
fun HomeNavHost(
    navController: NavHostController,
    survey: @Composable () -> Unit,
    signals: @Composable () -> Unit,
    map: @Composable () -> Unit,
    more: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(navController, startDestination = MainScreens.Survey.route, modifier = modifier) {
        composable(MainScreens.Survey.route) { survey() }
        composable(MainScreens.Signals.route) { signals() }
        composable(MainScreens.Map.route) { map() }
        composable(MainScreens.More.route) { more() }
    }
}
