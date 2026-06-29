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
     * Clear the navigation event after it has been handled.
     * This prevents re-navigation on configuration changes.
     */
    fun clearNavigation() {
        _navigationDestination.value = null
    }
}
