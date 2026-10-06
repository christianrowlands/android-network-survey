package com.craxiom.networksurvey.survey.mdm

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType

/**
 * The MDM (managed configuration) restriction keys that describe a survey setup, and the values
 * they accept. These keys are declared in `res/xml/app_restrictions.xml`; never rename one once it
 * has shipped, because fleet configurations reference them by name.
 *
 * Each key is a `choice` restriction with the values [VALUE_USER], [VALUE_ON] and [VALUE_OFF].
 * `user` (the default) leaves the item to the person using the phone. A bool restriction cannot
 * express "not set", and some EMM consoles push every key with its default value, which would
 * silently lock every item; the three-way choice avoids that.
 */
object SurveyMdmKeys {
    const val VALUE_USER = "user"
    const val VALUE_ON = "on"
    const val VALUE_OFF = "off"

    const val SURVEY_CELLULAR = "survey_cellular"
    const val SURVEY_PHONE_STATE = "survey_phone_state"
    const val SURVEY_WIFI = "survey_wifi"
    const val SURVEY_BLUETOOTH = "survey_bluetooth"
    const val SURVEY_GNSS = "survey_gnss"
    const val SURVEY_CDR = "survey_cdr"

    const val DESTINATION_LOG_FILES = "destination_log_files"
    const val DESTINATION_NS_ANALYTICS = "destination_ns_analytics"
    const val DESTINATION_MQTT = "destination_mqtt"
    const val DESTINATION_GRPC = "destination_grpc"
    const val DESTINATION_COMMUNITY = "destination_community"

    /**
     * Start the survey automatically when the app opens and when the phone boots. The same key is
     * also the user preference that holds the user's own choice.
     */
    const val AUTO_START_SURVEY = "auto_start_survey"

    /** @return The restriction key that controls [type]. */
    @JvmStatic
    fun keyFor(type: RecordType): String = when (type) {
        RecordType.CELLULAR -> SURVEY_CELLULAR
        RecordType.PHONE_STATE -> SURVEY_PHONE_STATE
        RecordType.WIFI -> SURVEY_WIFI
        RecordType.BLUETOOTH -> SURVEY_BLUETOOTH
        RecordType.GNSS -> SURVEY_GNSS
        RecordType.CDR -> SURVEY_CDR
    }

    /** @return The restriction key that controls [destination]. */
    @JvmStatic
    fun keyFor(destination: Destination): String = when (destination) {
        Destination.LOG_FILES -> DESTINATION_LOG_FILES
        Destination.NS_ANALYTICS -> DESTINATION_NS_ANALYTICS
        Destination.MQTT -> DESTINATION_MQTT
        Destination.GRPC -> DESTINATION_GRPC
        Destination.COMMUNITY -> DESTINATION_COMMUNITY
    }

    /** Every survey setup key, in schema order. */
    @JvmStatic
    val ALL_SETUP_KEYS: List<String> =
        RecordType.entries.map { keyFor(it) } + Destination.entries.map { keyFor(it) } + AUTO_START_SURVEY
}
