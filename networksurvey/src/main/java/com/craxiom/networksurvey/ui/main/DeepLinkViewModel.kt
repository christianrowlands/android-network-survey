package com.craxiom.networksurvey.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a navigation request from where it starts (a deep link or notification tap handled by
 * the activity, or a page that asks for a tab) to the Compose UI that owns the navigation.
 */
class DeepLinkViewModel : ViewModel() {

    // A page of the outer graph to open, as a route; NavDrawerOption.None means "back to the tabs".
    private val _navigationDestination = MutableStateFlow<String?>(null)
    val navigationDestination: StateFlow<String?> = _navigationDestination.asStateFlow()

    // The tab to select once the tabs are showing, as a MainScreens route.
    private val _homeTabDestination = MutableStateFlow<String?>(null)
    val homeTabDestination: StateFlow<String?> = _homeTabDestination.asStateFlow()

    /** Opens the page at [route] on top of the tabs, closing any page above them. */
    fun navigateToPage(route: String) {
        _navigationDestination.value = route
    }

    /**
     * Selects the tab at [route]. Any page above the tabs is closed first, so this works from a
     * page (a notification tap, Community's "See on map") as well as from the tabs.
     */
    fun navigateToHomeTab(route: String) {
        _navigationDestination.value = NavDrawerOption.None.name
        _homeTabDestination.value = route
    }

    /** Clears the page request once it has been handled, so a recreation does not repeat it. */
    fun clearNavigation() {
        _navigationDestination.value = null
    }

    /** Clears the tab request once the tab has been selected. */
    fun clearHomeTab() {
        _homeTabDestination.value = null
    }
}
