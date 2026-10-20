package com.craxiom.networksurvey.survey.model

/**
 * The places a survey can deliver its records to. Every destination is an independent listener on
 * the same record streams, which is why several can run at once off one scan.
 *
 * Each value has a stable [wireName] used by the stored survey setup, the Intent API
 * `destinations` extra, and the MDM `destination_<wireName>` restriction keys. Never change a wire
 * name once it has shipped.
 */
enum class Destination(val wireName: String) {
    LOG_FILES("log_files"),
    NS_ANALYTICS("ns_analytics"),
    MQTT("mqtt"),
    GRPC("grpc"),
    COMMUNITY("community");

    /**
     * True if starting this destination starts a new Mission ID session. The community databases
     * (OpenCelliD and BeaconDB) discard the Mission ID, so they never roll it.
     */
    val isMissionRelevant: Boolean
        get() = this != COMMUNITY

    companion object {
        /**
         * @return The destination whose wire name matches [name] (case insensitive, surrounding
         * whitespace ignored), or null if there is no match.
         */
        @JvmStatic
        fun fromWireName(name: String?): Destination? {
            val trimmed = name?.trim() ?: return null
            return entries.firstOrNull { it.wireName.equals(trimmed, ignoreCase = true) }
        }
    }
}
