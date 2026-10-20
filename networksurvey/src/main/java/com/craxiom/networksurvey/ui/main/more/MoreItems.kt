package com.craxiom.networksurvey.ui.main.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.main.NavDrawerOption
import com.craxiom.networksurvey.ui.main.NavOption
import com.craxiom.networksurvey.ui.survey.components.iconRes
import com.craxiom.networksurvey.ui.survey.components.labelRes
import com.craxiom.networksurvey.ui.survey.model.DestinationDetail
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo

/** A group of rows on the More tab; [title] is null for the lone Settings row. */
enum class MoreSection(@StringRes val title: Int?) {
    SETTINGS(null),
    DESTINATIONS(R.string.more_section_destinations),
    TOOLS(R.string.more_section_tools),
    HELP(R.string.more_section_help),
}

/** The second line of a More row. */
sealed interface MoreSubtitle {
    /** A string resource. */
    data class Text(@StringRes val text: Int) : MoreSubtitle

    /** Text that is not a resource, such as the version number. */
    data class Literal(val text: String) : MoreSubtitle

    /** A destination's state, in the Survey screen's words. */
    data class OfDestination(val destination: Destination, val detail: DestinationDetail) : MoreSubtitle
}

/** What tapping a More row does. */
sealed interface MoreAction {
    /** Opens the page at [route]. */
    data class Open(val route: String) : MoreAction

    /** Opens [url] in the browser. */
    data class External(val url: String) : MoreAction

    /** Copies the app name and version. */
    data object CopyVersion : MoreAction
}

/** One row on the More tab. */
data class MoreItem(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val subtitle: MoreSubtitle,
    val action: MoreAction,
)

/** One group of rows; an empty group shows nothing, not even its heading. */
data class MoreGroup(val section: MoreSection, val items: List<MoreItem>)

/** Builds the More list. Pure, so the rules are tested on the JVM. */
object MoreItems {

    /** The destinations in the order the More screen lists them. */
    private val DESTINATION_ORDER = listOf(
        Destination.NS_ANALYTICS, Destination.MQTT, Destination.GRPC, Destination.COMMUNITY, Destination.LOG_FILES,
    )

    /**
     * @param info What each destination is set up as, or null before it has been read (the
     *   destinations are left out until then).
     * @param policy The organization policy: a destination it blocks or keeps off is not listed,
     *   exactly as on the Survey screen; a managed phone gets an Organization settings row.
     * @param versionName The version shown on the last row.
     */
    fun build(info: DestinationInfo?, policy: SurveyPolicyState, versionName: String): List<MoreGroup> = listOf(
        MoreGroup(MoreSection.SETTINGS, listOf(SETTINGS)),
        MoreGroup(MoreSection.DESTINATIONS, destinations(info, policy)),
        MoreGroup(MoreSection.TOOLS, TOOLS),
        MoreGroup(MoreSection.HELP, help(versionName)),
    )

    private fun destinations(info: DestinationInfo?, policy: SurveyPolicyState): List<MoreItem> {
        if (info == null) return emptyList()
        val rows = DESTINATION_ORDER.filter { policy.isDestinationAvailable(it) }.map { destination ->
            MoreItem(
                title = destination.labelRes,
                icon = destination.iconRes,
                subtitle = MoreSubtitle.OfDestination(destination, DestinationDetail.forPage(destination, info)),
                action = MoreAction.Open(routeOf(destination)),
            )
        }
        val organization = MoreItem(
            title = R.string.organization_settings_title,
            icon = R.drawable.ic_domain,
            subtitle = MoreSubtitle.Text(R.string.organization_settings_summary),
            action = MoreAction.Open(NavOption.OrganizationSettings.name),
        )
        return if (policy.isManaged) rows + organization else rows
    }

    /** @return The route of [destination]'s page. */
    fun routeOf(destination: Destination): String = when (destination) {
        Destination.NS_ANALYTICS -> NavDrawerOption.NsAnalyticsConnection.name
        Destination.MQTT -> NavDrawerOption.MqttBrokerConnection.name
        Destination.GRPC -> NavDrawerOption.ServerConnection.name
        Destination.COMMUNITY -> NavOption.CommunityDatabases.name
        Destination.LOG_FILES -> NavOption.LogFiles.name
    }

    private val TOOLS = listOf(
        page(R.string.cellular_tools, R.drawable.ic_calculate, R.string.cellular_tools_description, NavDrawerOption.CellularCalculators.name),
        page(R.string.plmn_lookup_drawer_title, R.drawable.ic_search_24, R.string.plmn_lookup_drawer_description, NavDrawerOption.PlmnLookup.name),
        page(R.string.oui_lookup_drawer_title, R.drawable.ic_fingerprint, R.string.oui_lookup_drawer_description, NavDrawerOption.OuiLookup.name),
        page(R.string.watchlist_title, R.drawable.ic_watchlist, R.string.more_watchlist_subtitle, NavDrawerOption.Watchlist.name),
        page(R.string.wifi_spectrum_title, R.drawable.ic_bar_chart, R.string.more_wifi_spectrum_subtitle, NavOption.WifiSpectrum.name),
    )

    private val SETTINGS = page(R.string.settings, R.drawable.ic_settings, R.string.more_settings_subtitle, NavDrawerOption.Settings.name)

    private fun help(versionName: String) = listOf(
        link(R.string.manual, R.drawable.ic_user_manual, R.string.networksurvey_domain, ExternalLinks.USER_MANUAL),
        link(R.string.messaging_docs, R.drawable.ic_schema, R.string.more_messaging_docs_subtitle, ExternalLinks.MESSAGING_DOCS),
        link(R.string.report_issue, R.drawable.ic_bug, R.string.github, ExternalLinks.REPORT_ISSUE),
        link(R.string.github, R.drawable.ic_github, R.string.more_github_subtitle, ExternalLinks.GITHUB),
        page(R.string.acknowledgments_title, R.drawable.ic_favorite_border, R.string.acknowledgments_summary, NavOption.Acknowledgments.name),
        link(R.string.privacy_policy, R.drawable.ic_policy, R.string.networksurvey_domain, ExternalLinks.PRIVACY_POLICY),
        MoreItem(R.string.more_version, R.drawable.ic_info, MoreSubtitle.Literal(versionName), MoreAction.CopyVersion),
    )

    private fun page(@StringRes title: Int, @DrawableRes icon: Int, @StringRes subtitle: Int, route: String) =
        MoreItem(title, icon, MoreSubtitle.Text(subtitle), MoreAction.Open(route))

    private fun link(@StringRes title: Int, @DrawableRes icon: Int, @StringRes subtitle: Int, url: String) =
        MoreItem(title, icon, MoreSubtitle.Text(subtitle), MoreAction.External(url))
}
