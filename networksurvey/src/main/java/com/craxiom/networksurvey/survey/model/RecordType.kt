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
 *
 * [needsScout] marks a type only an NS Scout can record ([WIFI_CLIENTS], [WIFI_RAW]).
 * Such a type is hidden without a paired Scout ([RecordTypeAvailability]), is left out of a survey
 * while the phone is the Wi-Fi source, and Remove Scout counts the saved setups that record it.
 * Another app can't start one through the Intent API ([RecordTypeAvailability.canIntentStart]).
 */
enum class RecordType(val wireName: String, val needsScout: Boolean = false) {
    CELLULAR("cellular"),
    PHONE_STATE("phone_state"),
    WIFI("wifi"),
    BLUETOOTH("bluetooth"),
    GNSS("gnss"),
    CDR("cdr"),

    /** Wi-Fi clients and the probe requests they send, heard by an NS Scout. */
    WIFI_CLIENTS("wifi_clients", needsScout = true),

    /** Raw 802.11 frames heard by an NS Scout, saved as pcapng to Log files only. */
    WIFI_RAW("wifi_raw", needsScout = true);

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
