package com.craxiom.networksurvey.survey.model

/**
 * The kinds of records the survey engine collects. A survey records a set of these and delivers
 * them to a set of [Destination]s.
 *
 * Each value has a stable [wireName] that is used anywhere the value leaves the app's memory: the
 * stored survey setup, the Intent API `record_types` extra, and the MDM `survey_<wireName>`
 * restriction keys. Never change a wire name once it has shipped.
 *
 * This is deliberately separate from [com.craxiom.networksurvey.model.SurveyTypes], which only
 * describes what a community upload toggle started and has no phone state value.
 */
enum class RecordType(val wireName: String) {
    CELLULAR("cellular"),
    PHONE_STATE("phone_state"),
    WIFI("wifi"),
    BLUETOOTH("bluetooth"),
    GNSS("gnss"),
    CDR("cdr");

    companion object {
        /**
         * @return The record type whose wire name matches [name] (case insensitive, surrounding
         * whitespace ignored), or null if there is no match.
         */
        @JvmStatic
        fun fromWireName(name: String?): RecordType? {
            val trimmed = name?.trim() ?: return null
            return entries.firstOrNull { it.wireName.equals(trimmed, ignoreCase = true) }
        }
    }
}
