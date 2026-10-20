package com.craxiom.networksurvey.survey.model

/**
 * The survey a phone starts with before the user picks anything: Cellular, Phone state and Wi-Fi
 * sent to NS Analytics. First run can switch the destination to Log files.
 */
object SurveyDefaults {
    /** The record types of a fresh install, and of an upgrade that had none on. */
    @JvmField
    val RECORD_TYPES: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.WIFI)

    /** The working setup of a fresh install. */
    @JvmStatic
    fun freshInstallSetup(): SurveySetup = SurveySetup.custom(RECORD_TYPES, setOf(Destination.NS_ANALYTICS))
}
