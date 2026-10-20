package com.craxiom.networksurvey.ui.survey.model

import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.status.DeliveryQueues

/**
 * Everything [SurveyUiStateMapper] needs, gathered by the view model from the service, the
 * survey setup store and the phone. Pure data so the mapper can be tested on the JVM.
 *
 * @property runtime               What the service is actually running, plus the policy in force.
 * @property userSetup             The user's own working setup (before the organization's locks).
 * @property autoIncludePhoneState The `auto_include_phone_state_with_cellular` setting.
 * @property destinationInfo       Configuration and display details for each destination.
 * @property location              The location line's state.
 * @property counts                Per record type counts for the running session.
 * @property lastRecordTimeMs      When the newest record arrived, or null if none has this session.
 * @property communityCounts       Per record type counts of the records saved for the community
 *                                 databases this session (after their upload filters).
 * @property communityLastRecordTimeMs When the newest record was saved for the community databases,
 *                                 or null if none has been this session.
 * @property nowMs                 The current time, for the timer and the heartbeat.
 * @property hiddenDestinations    Destinations the user hid with Edit list.
 * @property userAutoStart         The user's own auto start choice.
 * @property communityUpload       A running community upload, if any.
 * @property savedSetups           The user's saved setups in display order (starred first).
 * @property selectedSetupId       The saved setup the working setup was last picked from, if any.
 * @property queues                What waits on the phone for NS Analytics and the community
 *                                 databases, for their status and the "Still to do" panel.
 * @property wifiOff               True when Wi-Fi and Wi-Fi scanning are both off, so no Wi-Fi scan
 *                                 can run until one of them is turned on.
 */
data class SurveyInputs(
    val runtime: SurveyRuntimeState,
    val userSetup: SurveySetup,
    val autoIncludePhoneState: Boolean,
    val destinationInfo: DestinationInfo,
    val location: LocationStatus,
    val counts: Map<RecordType, Long> = emptyMap(),
    val lastRecordTimeMs: Long? = null,
    val communityCounts: Map<RecordType, Long> = emptyMap(),
    val communityLastRecordTimeMs: Long? = null,
    val nowMs: Long,
    val hiddenDestinations: Set<Destination> = emptySet(),
    val userAutoStart: Boolean = false,
    val communityUpload: CommunityUploadUi? = null,
    val savedSetups: List<SurveySetup> = emptyList(),
    val selectedSetupId: String? = null,
    val queues: DeliveryQueues = DeliveryQueues(),
    val wifiOff: Boolean = false,
)

/**
 * Whether each destination can be used, and what its row says about it.
 *
 * @property nsAnalyticsRegistered True once this phone is connected to an NS Analytics workspace.
 * @property workspaceName         The connected workspace's name.
 * @property nsAnalyticsAutoUpload True when queued NS Analytics records upload on their own; false
 *                                 when they wait on the phone until the user uploads them.
 * @property mqttConfigured        True if MQTT has a broker (the organization's or the user's).
 * @property mqttBrokerLabel       The broker's host, for the row.
 * @property mqttFromOrganization  True when the broker is the one the organization provides.
 * @property mqttBrokersSavedNoneSelected True when brokers are saved but none is selected, so MQTT
 *                                 needs the user to choose one.
 * @property grpcAddress           "host:port" of the gRPC server, or null when not set up.
 * @property communityTargets      The record types the selected OpenCelliD and BeaconDB targets
 *                                 take; empty when neither is selected.
 * @property openCelliDSelected    True if uploads to OpenCelliD are on.
 * @property beaconDbSelected      True if uploads to BeaconDB are on.
 * @property communityAutoUpload   True when the community databases upload on their own while
 *                                 recording; false when records wait for the user's upload.
 * @property queueLimitSetByOrganization True when the organization sets the MQTT queue limit (and
 *                                 the user has not overridden it), so the app cannot change it.
 * @property batteryThresholdSetByOrganization True when the organization sets the battery pause
 *                                 threshold (and the user has not overridden it).
 */
data class DestinationInfo(
    val nsAnalyticsRegistered: Boolean = false,
    val workspaceName: String? = null,
    val nsAnalyticsAutoUpload: Boolean = true,
    val mqttConfigured: Boolean = false,
    val mqttBrokerLabel: String? = null,
    val mqttFromOrganization: Boolean = false,
    val mqttBrokersSavedNoneSelected: Boolean = false,
    val grpcAddress: String? = null,
    val communityTargets: Set<RecordType> = emptySet(),
    val openCelliDSelected: Boolean = false,
    val beaconDbSelected: Boolean = false,
    val communityAutoUpload: Boolean = true,
    val queueLimitSetByOrganization: Boolean = false,
    val batteryThresholdSetByOrganization: Boolean = false,
) {
    /** @return True if [destination] has everything it needs to receive records. */
    fun isReady(destination: Destination): Boolean = when (destination) {
        Destination.LOG_FILES -> true
        Destination.NS_ANALYTICS -> nsAnalyticsRegistered
        Destination.MQTT -> mqttConfigured
        Destination.GRPC -> grpcAddress != null
        Destination.COMMUNITY -> communityTargets.isNotEmpty()
    }
}
