package com.craxiom.networksurvey.survey.mdm

import com.craxiom.networksurvey.constants.NetworkSurveyConstants

/**
 * The settings an organization can set through the managed configuration and the user can also
 * change in the app. On a managed phone (without the override) each of these that the organization
 * set shows on Settings as set by the organization. The pre-v2 auto start keys stay in this list for
 * the one release that still reads them (see [LegacyMdmMapper]).
 */
object MdmOverlapKeys {

    @JvmField
    val ALL: Array<String> = arrayOf(
        SurveyMdmKeys.SURVEY_CELLULAR,
        SurveyMdmKeys.SURVEY_PHONE_STATE,
        SurveyMdmKeys.SURVEY_WIFI,
        SurveyMdmKeys.SURVEY_BLUETOOTH,
        SurveyMdmKeys.SURVEY_GNSS,
        SurveyMdmKeys.SURVEY_CDR,
        SurveyMdmKeys.DESTINATION_LOG_FILES,
        SurveyMdmKeys.DESTINATION_NS_ANALYTICS,
        SurveyMdmKeys.DESTINATION_MQTT,
        SurveyMdmKeys.DESTINATION_GRPC,
        SurveyMdmKeys.DESTINATION_COMMUNITY,
        SurveyMdmKeys.AUTO_START_SURVEY,
        NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING,
        NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING,
        NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING,
        NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING,
        NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING,
        NetworkSurveyConstants.PROPERTY_AUTO_START_PHONE_STATE_LOGGING,
        NetworkSurveyConstants.PROPERTY_AUTO_INCLUDE_PHONE_STATE_WITH_CELLULAR,
        NetworkSurveyConstants.PROPERTY_INCLUDE_NEIGHBOR_CELLS,
        NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS,
        NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
        NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS,
        NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS,
        NetworkSurveyConstants.PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS,
        NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT,
        NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER,
        NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL,
        NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT,
        NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT,
        NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED,
        NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_WIFI_ONLY,
        NetworkSurveyConstants.PROPERTY_OUI_LOOKUP_ENABLED,
        NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED,
    )
}
