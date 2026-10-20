package com.craxiom.networksurvey.ui.survey

import android.content.Context
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.fragments.model.GrpcConnectionSettings
import com.craxiom.networksurvey.mqtt.OrganizationBroker
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfileStore
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.PreferenceUtils

/**
 * Reads what each destination row needs to say: whether it is set up, and the workspace, broker
 * or address that describes it. Reads preferences, encrypted storage and the managed
 * configuration, so call it off the main thread.
 */
class DestinationInfoReader(private val context: Context) {

    private val brokerStore = MqttBrokerProfileStore.create(context)


    /**
     * @param mqttConfigured The service's own answer when it is bound (it also knows about an Intent
     *                       API broker); null to work it out from the settings.
     */
    fun read(mqttConfigured: Boolean?): DestinationInfo {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val overrideOn = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false)
        val restrictions = AndroidRestrictionsSource(context).snapshot()
        val organizationBroker = !overrideOn && OrganizationBroker.isPresent(restrictions)
        val activeBroker = brokerStore.active()
        val hasClientId = brokerStore.clientId().isNotBlank()
        val grpc = GrpcConnectionSettings.fromPreferences(preferences)

        return DestinationInfo(
            nsAnalyticsRegistered = NsAnalyticsSecureStorage.isRegistered(context),
            workspaceName = NsAnalyticsSecureStorage.getWorkspaceName(context),
            nsAnalyticsAutoUpload = PreferenceUtils.isNsAnalyticsAutoUpload(context),
            mqttConfigured = mqttConfigured ?: (organizationBroker || (activeBroker != null && hasClientId)),
            mqttBrokerLabel = activeBroker?.takeIf { !organizationBroker }?.let {
                // A broker migrated from earlier versions is named after its host; say it once.
                if (it.name == it.host) it.host else "${it.name}$LABEL_SEPARATOR${it.host}"
            },
            mqttFromOrganization = organizationBroker,
            mqttBrokersSavedNoneSelected = !organizationBroker && activeBroker == null && brokerStore.hasUserProfiles(),
            grpcAddress = grpc?.displayAddress,
            communityTargets = SurveyTypeResolver.communityTargets(context),
            openCelliDSelected = preferences.getBoolean(
                NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID,
            ),
            beaconDbSelected = preferences.getBoolean(
                NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB,
            ),
            communityAutoUpload = PreferenceUtils.isAutoUploadEnabled(context),
            // The same rule PreferenceUtils.getStreamingQueueLimit and getBatteryThresholdPercent follow
            queueLimitSetByOrganization = !overrideOn && restrictions.contains(NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT),
            batteryThresholdSetByOrganization = !overrideOn && restrictions.contains(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT),
        )
    }

    private companion object {
        const val LABEL_SEPARATOR = " · "
    }
}
