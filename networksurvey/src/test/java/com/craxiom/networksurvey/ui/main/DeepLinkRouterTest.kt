package com.craxiom.networksurvey.ui.main

import android.content.Intent
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.logging.db.uploader.UploaderNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where [DeepLinkRouter] sends each link and notification tap. */
class DeepLinkRouterTest {

    private val view = Intent.ACTION_VIEW

    @Test
    fun theGrpcLink_opensTheGrpcPage() {
        assertEquals(DeepLinkTarget.GrpcPage, DeepLinkRouter.route(view, "http://craxiom.com/grpc_server_connection", emptySet()))
    }

    @Test
    fun theRegistrationLink_isReadBeforeOpeningNsAnalytics() {
        val uri = "https://networksurvey.app/app/register?token=t&workspace_id=w&api_url=https://api.example.com"
        assertEquals(DeepLinkTarget.NsAnalyticsRegister(uri), DeepLinkRouter.route(view, uri, emptySet()))
    }

    @Test
    fun theWatchlistLink_isImported() {
        val uri = "https://networksurvey.app/app/watchlist?d=abc"
        assertEquals(DeepLinkTarget.WatchlistImport(uri), DeepLinkRouter.route(view, uri, emptySet()))
    }

    @Test
    fun theNotificationExtras_openTheirPageOrTab() {
        assertEquals(DeepLinkTarget.NsAnalyticsPage, route(NsAnalyticsConstants.EXTRA_NAVIGATE_TO_NS_ANALYTICS))
        assertEquals(DeepLinkTarget.WatchlistHistory, route(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_WATCHLIST_HISTORY))
        assertEquals(DeepLinkTarget.HomeTab(MainScreens.Survey.route), route(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_DASHBOARD))
    }

    @Test
    fun theCommunityUploadNotification_opensTheCommunityPage_notTheSurveyTab() {
        assertEquals(DeepLinkTarget.CommunityPage, route(UploaderNotificationHelper.EXTRA_NAVIGATE_TO_COMMUNITY))
        assertTrue(UploaderNotificationHelper.EXTRA_NAVIGATE_TO_COMMUNITY in DeepLinkRouter.NAVIGATION_EXTRAS)
    }

    @Test
    fun theNewTowerNotification_opensTheMapTab() {
        assertEquals(DeepLinkTarget.HomeTab(MainScreens.Map.route), route(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_MAP))
    }

    @Test
    fun anUnrelatedIntent_goesNowhere() {
        assertNull(DeepLinkRouter.route(Intent.ACTION_MAIN, null, emptySet()))
        assertNull(DeepLinkRouter.route(view, "https://example.com/app/register", emptySet()))
        assertNull(DeepLinkRouter.route(view, "https://networksurvey.app/manual", emptySet()))
        assertNull(DeepLinkRouter.route(view, "http://craxiom.com/other", emptySet()))
        assertNull(DeepLinkRouter.route(view, "not a uri at all", emptySet()))
        assertNull(DeepLinkRouter.route(null, null, emptySet()))
    }

    @Test
    fun aLinkOnAnotherAction_isIgnored() {
        assertNull(DeepLinkRouter.route(Intent.ACTION_SEND, "http://craxiom.com/grpc_server_connection", emptySet()))
    }

    @Test
    fun aLink_winsOverAnExtra() {
        val target = DeepLinkRouter.route(
            view,
            "http://craxiom.com/grpc_server_connection",
            setOf(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_DASHBOARD),
        )
        assertEquals(DeepLinkTarget.GrpcPage, target)
    }

    @Test
    fun onCreate_routesOnlyAFreshIntent() {
        assertTrue(DeepLinkRouter.shouldRouteOnCreate(restoring = false, intentFlags = 0))
        // Rotation or process death: the link was handled when the activity was first created.
        assertFalse(DeepLinkRouter.shouldRouteOnCreate(restoring = true, intentFlags = 0))
        // Reopened from Recents, which hands back the intent that started the task.
        assertFalse(DeepLinkRouter.shouldRouteOnCreate(restoring = false, intentFlags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
    }

    private fun route(extra: String) = DeepLinkRouter.route(Intent.ACTION_MAIN, null, setOf(extra))
}
