package com.craxiom.networksurvey.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for managing deep link navigation events.
 *
 * This ViewModel acts as a bridge between the Activity (which receives deep links)
 * and the Compose UI (which handles navigation). It uses StateFlow to emit navigation
 * events that the Compose UI can observe and react to.
 */
class DeepLinkViewModel : ViewModel() {

    private val _navigationDestination = MutableStateFlow<String?>(null)
    val navigationDestination: StateFlow<String?> = _navigationDestination.asStateFlow()

    // The bottom navigation tab to select on the home screen, as a MainScreens route.
    private val _homeTabDestination = MutableStateFlow<String?>(null)
    val homeTabDestination: StateFlow<String?> = _homeTabDestination.asStateFlow()

    /**
     * Trigger navigation to the NS Analytics screen.
     * Called by the Activity when a valid NS Analytics deep link is received.
     */
    fun navigateToNsAnalytics() {
        _navigationDestination.value = NavDrawerOption.NsAnalyticsConnection.name
    }

    /**
     * Trigger navigation to the Watchlist history screen.
     * Called by the Activity when a watchlist notification is tapped.
     */
    fun navigateToWatchlistHistory() {
        _navigationDestination.value = NavOption.WatchlistHistory.name
    }

    /**
     * Trigger navigation to the Watchlist management screen.
     * Called by the Activity when a valid watchlist import deep link is received, after the parsed
     * networks have been stashed for the screen to confirm.
     */
    fun navigateToWatchlist() {
        _navigationDestination.value = NavDrawerOption.Watchlist.name
    }

    /**
     * Trigger navigation to the dashboard tab of the home screen.
     * Called by the Activity when the survey or upload notification is tapped. The main graph pops
     * back to the home screen and the home screen then selects the dashboard tab.
     */
    fun navigateToDashboard() {
        _navigationDestination.value = NavDrawerOption.None.name
        _homeTabDestination.value = MainScreens.Dashboard.route
    }

    /**
     * Clear the navigation event after it has been handled.
     * This prevents re-navigation on configuration changes.
     */
    fun clearNavigation() {
        _navigationDestination.value = null
    }

    /**
     * Clear the home tab event after the home screen has selected the tab.
     */
    fun clearHomeTab() {
        _homeTabDestination.value = null
    }
}
