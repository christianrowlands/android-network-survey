package com.craxiom.networksurvey.ui.main.more

import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.mdm.ManagedState
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.main.NavOption
import com.craxiom.networksurvey.ui.survey.model.DestinationDetail
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of the More list in [MoreItems]. */
class MoreItemsTest {

    private val unmanaged = SurveyPolicyState.UNMANAGED

    @Test
    fun theGroups_comeInOrder() {
        val groups = MoreItems.build(DestinationInfo(), unmanaged, "1.0")
        assertEquals(listOf(MoreSection.SETTINGS, MoreSection.DESTINATIONS, MoreSection.TOOLS, MoreSection.HELP), groups.map { it.section })
        assertEquals(
            listOf(Destination.NS_ANALYTICS, Destination.MQTT, Destination.GRPC, Destination.COMMUNITY, Destination.LOG_FILES),
            destinations(groups).map { it.destination },
        )
    }

    @Test
    fun beforeTheSettingsAreRead_noDestinationIsListed() {
        assertTrue(MoreItems.build(null, unmanaged, "1.0").first { it.section == MoreSection.DESTINATIONS }.items.isEmpty())
    }

    @Test
    fun aBlockedOrLockedOffDestination_isNotListed_likeOnTheSurveyScreen() {
        val policy = unmanaged.copy(
            blockedDestinations = setOf(Destination.COMMUNITY),
            destinationLocks = mapOf(Destination.GRPC to ManagedState.OFF),
        )
        val listed = destinations(MoreItems.build(DestinationInfo(), policy, "1.0")).map { it.destination }
        assertEquals(listOf(Destination.NS_ANALYTICS, Destination.MQTT, Destination.LOG_FILES), listed)
    }

    @Test
    fun everyDestinationOff_leavesTheGroupEmpty() {
        val policy = unmanaged.copy(destinationLocks = Destination.entries.associateWith { ManagedState.OFF })
        assertTrue(MoreItems.build(DestinationInfo(), policy, "1.0").first { it.section == MoreSection.DESTINATIONS }.items.isEmpty())
    }

    @Test
    fun aManagedPhone_endsTheDestinationsWithOrganizationSettings() {
        val items = MoreItems.build(DestinationInfo(), unmanaged.copy(isManaged = true), "1.0")
            .first { it.section == MoreSection.DESTINATIONS }.items
        assertEquals(MoreAction.Open(NavOption.OrganizationSettings.name), items.last().action)
    }

    @Test
    fun theSubtitles_sayWhatEachDestinationNeeds() {
        val info = DestinationInfo(mqttBrokersSavedNoneSelected = true)
        val details = destinations(MoreItems.build(info, unmanaged, "1.0")).associate { it.destination to it.detail }
        assertEquals(DestinationDetail.NotConnected, details[Destination.NS_ANALYTICS])
        assertEquals(DestinationDetail.ChooseBroker, details[Destination.MQTT])
        assertEquals(DestinationDetail.NotSetUp, details[Destination.GRPC])
        assertEquals(DestinationDetail.NotSetUp, details[Destination.COMMUNITY])
        assertEquals(DestinationDetail.Default, details[Destination.LOG_FILES])
    }

    @Test
    fun anOrganizationBroker_isSetUp_andDescribedByTheSurveyMapping() {
        val info = DestinationInfo(mqttConfigured = true, mqttFromOrganization = true)
        val mqtt = destinations(MoreItems.build(info, unmanaged, "1.0")).first { it.destination == Destination.MQTT }
        assertEquals(DestinationDetail.Default, mqtt.detail)
    }

    @Test
    fun theHelpRows_linkOut_andTheVersionCopies() {
        val help = MoreItems.build(DestinationInfo(), unmanaged, "1.62.0-cdr").first { it.section == MoreSection.HELP }.items
        assertEquals(
            listOf(ExternalLinks.USER_MANUAL, ExternalLinks.MESSAGING_DOCS, ExternalLinks.REPORT_ISSUE, ExternalLinks.GITHUB, ExternalLinks.PRIVACY_POLICY),
            help.mapNotNull { (it.action as? MoreAction.External)?.url },
        )
        val version = help.last()
        assertEquals(R.string.more_version, version.title)
        assertEquals(MoreSubtitle.Literal("1.62.0-cdr"), version.subtitle)
        assertEquals(MoreAction.CopyVersion, version.action)
    }

    @Test
    fun eachDestination_opensItsOwnPage() {
        assertEquals(Destination.entries.size, Destination.entries.map { MoreItems.routeOf(it) }.toSet().size)
    }

    private fun destinations(groups: List<MoreGroup>): List<MoreSubtitle.OfDestination> =
        groups.first { it.section == MoreSection.DESTINATIONS }.items.mapNotNull { it.subtitle as? MoreSubtitle.OfDestination }
}
