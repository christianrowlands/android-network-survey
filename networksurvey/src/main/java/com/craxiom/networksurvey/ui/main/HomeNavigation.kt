package com.craxiom.networksurvey.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.CappedFontScale

/** The routes of the four tabs. */
sealed class MainScreens(val route: String) {
    /** The route string predates the Survey screen; notification taps and saved state still use it. */
    data object Survey : MainScreens("dashboard_route")
    data object Signals : MainScreens("signals_route")
    data object Map : MainScreens("map_route")
    data object More : MainScreens("more_route")
}

/** One tab of the bottom bar (or the rail on a wide screen). */
enum class HomeTab(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int = icon,
) {
    SURVEY(MainScreens.Survey.route, R.string.nav_survey, R.drawable.ic_survey),
    SIGNALS(MainScreens.Signals.route, R.string.nav_signals, R.drawable.ic_monitoring),
    MAP(MainScreens.Map.route, R.string.nav_map, R.drawable.ic_map, R.drawable.ic_map_filled),
    MORE(MainScreens.More.route, R.string.nav_more, R.drawable.ic_more_horiz),
}

/**
 * System fontScale at and above which the tab labels are hidden entirely (icons only). This is
 * intentionally past Android's "Largest" preset so only extreme accessibility settings hit the
 * icons-only state.
 */
internal const val LABEL_HIDE_FONT_SCALE_THRESHOLD = 1.5f

/**
 * Selects the tab at [route]. Every tab sits directly above the Survey tab, so Back from any tab
 * goes to Survey and Back from Survey leaves the app; each tab's own stack is saved and restored.
 */
fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The tab bar around [content]: a bottom bar, or a navigation rail when [useRail]. The selected tab
 * follows the back stack, so Back and deep links keep it right. [content] has one call site so the
 * tab graph is not rebuilt when the layout switches between the two.
 */
@Composable
fun HomeNavigation(
    navController: NavController,
    useRail: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedRoute = backStackEntry?.destination?.route
    val onSelect: (HomeTab) -> Unit = { navController.navigateToTab(it.route) }

    Scaffold(
        modifier = modifier,
        bottomBar = { if (!useRail) HomeBottomBar(selectedRoute, onSelect) },
        // Each tab draws its own top bar under the status bar; the rail layout keeps the content
        // clear of the gesture bar and an end-side navigation bar itself.
        contentWindowInsets = if (useRail) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End)
        } else {
            WindowInsets(0)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            if (useRail) HomeRail(selectedRoute, onSelect)
            val contentModifier = if (useRail) {
                Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            } else {
                Modifier
            }
            Box(
                contentModifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { content() }
        }
    }
}

@Composable
private fun HomeBottomBar(selectedRoute: String?, onSelect: (HomeTab) -> Unit) {
    val labelPolicy = rememberLabelPolicy()
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar {
            HomeTab.entries.forEach { tab ->
                val selected = tab.route == selectedRoute
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab) },
                    icon = { TabIcon(tab, selected, labelPolicy.showLabels) },
                    label = tabLabel(tab, labelPolicy),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun HomeRail(selectedRoute: String?, onSelect: (HomeTab) -> Unit) {
    val labelPolicy = rememberLabelPolicy()
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        HomeTab.entries.forEach { tab ->
            val selected = tab.route == selectedRoute
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = { TabIcon(tab, selected, labelPolicy.showLabels) },
                label = tabLabel(tab, labelPolicy),
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Whether the labels show; they are hidden at very large font scales. */
private class LabelPolicy(val showLabels: Boolean)

@Composable
private fun rememberLabelPolicy(): LabelPolicy {
    val fontScale = LocalDensity.current.fontScale
    return remember(fontScale) { LabelPolicy(showLabels = fontScale < LABEL_HIDE_FONT_SCALE_THRESHOLD) }
}

/** The icon; it carries the label for TalkBack only when the visible label is hidden. */
@Composable
private fun TabIcon(tab: HomeTab, selected: Boolean, showLabels: Boolean) {
    Icon(
        painter = painterResource(if (selected) tab.selectedIcon else tab.icon),
        contentDescription = if (showLabels) null else stringResource(tab.label),
    )
}

private fun tabLabel(tab: HomeTab, policy: LabelPolicy): (@Composable () -> Unit)? =
    if (policy.showLabels) {
        {
            CappedFontScale {
                Text(text = stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        null
    }
