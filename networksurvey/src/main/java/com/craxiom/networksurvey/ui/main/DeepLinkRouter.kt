package com.craxiom.networksurvey.ui.main

import android.content.Intent
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.logging.db.uploader.UploaderNotificationHelper
import java.net.URI
import java.net.URISyntaxException

/** Where a deep link or a notification tap takes the user. */
sealed interface DeepLinkTarget {
    /** The gRPC page (`craxiom.com/grpc_server_connection`). */
    data object GrpcPage : DeepLinkTarget

    /** An NS Analytics registration link, to be read and stored before opening the NS Analytics page. */
    data class NsAnalyticsRegister(val uri: String) : DeepLinkTarget

    /** A watchlist link, to be read and offered on the Watchlist page. */
    data class WatchlistImport(val uri: String) : DeepLinkTarget

    /** The NS Analytics page (the "uploads paused" notification). */
    data object NsAnalyticsPage : DeepLinkTarget

    /** The Watchlist history (a watchlist "Seen" notification). */
    data object WatchlistHistory : DeepLinkTarget

    /** The Community databases page (the community upload notification), where the upload's results are. */
    data object CommunityPage : DeepLinkTarget

    /**
     * A tab, by its [MainScreens] route (the survey notification selects Survey, the new-tower
     * notification selects Map).
     */
    data class HomeTab(val route: String) : DeepLinkTarget
}

/**
 * Decides where an incoming intent goes. It is the one place a link or notification tap is read,
 * so each is handled exactly once; it reads nothing but its arguments, so it is tested on the JVM.
 */
object DeepLinkRouter {

    /** The boolean notification extras the router reads; the activity passes the ones set to true. */
    val NAVIGATION_EXTRAS = listOf(
        NsAnalyticsConstants.EXTRA_NAVIGATE_TO_NS_ANALYTICS,
        NetworkSurveyConstants.EXTRA_NAVIGATE_TO_WATCHLIST_HISTORY,
        UploaderNotificationHelper.EXTRA_NAVIGATE_TO_COMMUNITY,
        NetworkSurveyConstants.EXTRA_NAVIGATE_TO_DASHBOARD,
        NetworkSurveyConstants.EXTRA_NAVIGATE_TO_MAP,
    )

    /**
     * @param action The intent's action.
     * @param uri The intent's data, as a string.
     * @param trueExtras The [NAVIGATION_EXTRAS] the intent carries as true.
     * @return The target, or null when the intent asks for nothing. A link wins over an extra.
     */
    fun route(action: String?, uri: String?, trueExtras: Set<String>): DeepLinkTarget? {
        if (action == Intent.ACTION_VIEW && uri != null) {
            linkTarget(uri)?.let { return it }
        }
        return when {
            NsAnalyticsConstants.EXTRA_NAVIGATE_TO_NS_ANALYTICS in trueExtras -> DeepLinkTarget.NsAnalyticsPage
            NetworkSurveyConstants.EXTRA_NAVIGATE_TO_WATCHLIST_HISTORY in trueExtras -> DeepLinkTarget.WatchlistHistory
            UploaderNotificationHelper.EXTRA_NAVIGATE_TO_COMMUNITY in trueExtras -> DeepLinkTarget.CommunityPage
            NetworkSurveyConstants.EXTRA_NAVIGATE_TO_DASHBOARD in trueExtras -> DeepLinkTarget.HomeTab(MainScreens.Survey.route)
            NetworkSurveyConstants.EXTRA_NAVIGATE_TO_MAP in trueExtras -> DeepLinkTarget.HomeTab(MainScreens.Map.route)
            else -> null
        }
    }

    /**
     * @return True when the activity's `onCreate` should act on its intent: not when it is being
     * restored (rotation, process death), and not when it was reopened from Recents, which hands
     * back the intent that started the task.
     */
    fun shouldRouteOnCreate(restoring: Boolean, intentFlags: Int): Boolean =
        !restoring && (intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

    private fun linkTarget(uri: String): DeepLinkTarget? {
        val parsed = try {
            URI(uri)
        } catch (_: URISyntaxException) {
            return null
        }
        val host = parsed.host
        val path = parsed.path.orEmpty()
        return when {
            host == NetworkSurveyConstants.GRPC_DEEP_LINK_HOST && path == NetworkSurveyConstants.GRPC_DEEP_LINK_PATH ->
                DeepLinkTarget.GrpcPage

            host == NsAnalyticsConstants.NS_ANALYTICS_DEEP_LINK_HOST && path.startsWith(NsAnalyticsConstants.NS_ANALYTICS_DEEP_LINK_PATH) ->
                DeepLinkTarget.NsAnalyticsRegister(uri)

            host == NsAnalyticsConstants.NS_ANALYTICS_DEEP_LINK_HOST && path.startsWith(NetworkSurveyConstants.WATCHLIST_DEEP_LINK_PATH) ->
                DeepLinkTarget.WatchlistImport(uri)

            else -> null
        }
    }
}
