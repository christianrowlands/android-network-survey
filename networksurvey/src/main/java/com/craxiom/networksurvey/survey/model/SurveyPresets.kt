package com.craxiom.networksurvey.survey.model

/**
 * The setups that ship with the app. Their ids are stable and their display names come from
 * string resources (see the Survey screen), so only the ids and contents are defined here.
 */
object SurveyPresets {
    const val NS_ANALYTICS_SURVEY_ID = "builtin_ns_analytics_survey"
    const val DRIVE_TEST_ID = "builtin_drive_test"

    /** Cellular, Phone state and Wi-Fi sent to NS Analytics. Selected on a fresh unmanaged install. */
    @JvmStatic
    fun nsAnalyticsSurvey(
        recordTypes: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.WIFI),
    ): SurveySetup = SurveySetup(
        id = NS_ANALYTICS_SURVEY_ID,
        name = "NS Analytics survey",
        recordTypes = recordTypes,
        destinations = setOf(Destination.NS_ANALYTICS),
        builtIn = true,
    )

    /** Cellular, Phone state and GNSS saved to files, NS Analytics and the community databases. */
    @JvmStatic
    fun driveTest(): SurveySetup = SurveySetup(
        id = DRIVE_TEST_ID,
        name = "Drive test",
        recordTypes = setOf(RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.GNSS),
        destinations = setOf(Destination.LOG_FILES, Destination.NS_ANALYTICS, Destination.COMMUNITY),
        builtIn = true,
    )

    /** Both built-in presets in display order. */
    @JvmStatic
    fun all(): List<SurveySetup> = listOf(nsAnalyticsSurvey(), driveTest())
}
