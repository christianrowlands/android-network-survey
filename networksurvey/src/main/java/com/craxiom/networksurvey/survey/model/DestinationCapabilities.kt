package com.craxiom.networksurvey.survey.model

/**
 * The record type x destination capability matrix, and the rules that turn a [SurveySetup] into
 * the exact set of record types each destination receives.
 *
 * Every destination receives everything the survey records, except where the destination cannot
 * accept a type:
 * - Log files accept every type (CDR is files only).
 * - NS Analytics, MQTT and gRPC accept everything except CDR.
 * - The community databases accept Cellular (OpenCelliD and BeaconDB) and Wi-Fi (BeaconDB only),
 *   narrowed further by which upload targets the user selected.
 */
object DestinationCapabilities {

    private val ALL_TYPES: Set<RecordType> = RecordType.entries.toSet()
    private val STREAMING_TYPES: Set<RecordType> = ALL_TYPES - RecordType.CDR
    private val COMMUNITY_TYPES: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.WIFI)

    /** What OpenCelliD takes; matches `PreferenceUtils.getRequiredProtocolsForUpload`. */
    @JvmField
    val OPENCELLID_TYPES: Set<RecordType> = setOf(RecordType.CELLULAR)

    /** What BeaconDB takes; matches `PreferenceUtils.getRequiredProtocolsForUpload`. */
    @JvmField
    val BEACONDB_TYPES: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.WIFI)

    /** @return Every record type [destination] can accept, before any user or upload target choice. */
    @JvmStatic
    fun accepts(destination: Destination): Set<RecordType> = when (destination) {
        Destination.LOG_FILES -> ALL_TYPES
        Destination.NS_ANALYTICS, Destination.MQTT, Destination.GRPC -> STREAMING_TYPES
        Destination.COMMUNITY -> COMMUNITY_TYPES
    }

    /**
     * Applies the "Phone state is included with Cellular" rule to a selection of record types.
     *
     * @param recordTypes            The selected record types.
     * @param autoIncludePhoneState  The `auto_include_phone_state_with_cellular` preference.
     * @param phoneStateForcedOff    True when the organization turned Phone state off, which beats
     *                               the auto include rule.
     */
    @JvmStatic
    fun effectiveRecordTypes(
        recordTypes: Set<RecordType>,
        autoIncludePhoneState: Boolean,
        phoneStateForcedOff: Boolean = false,
    ): Set<RecordType> {
        val result = recordTypes.toMutableSet()
        if (autoIncludePhoneState && RecordType.CELLULAR in result) result += RecordType.PHONE_STATE
        if (phoneStateForcedOff) result -= RecordType.PHONE_STATE
        return result
    }

    /**
     * @param destination      The destination to compute types for.
     * @param effectiveTypes   The survey's record types after [effectiveRecordTypes].
     * @param communityTargets The record types the selected community upload targets need (from
     *                         the OpenCelliD and BeaconDB settings). Only used for
     *                         [Destination.COMMUNITY].
     * @return The record types [destination] actually receives from this survey.
     */
    @JvmStatic
    fun typesFor(
        destination: Destination,
        effectiveTypes: Set<RecordType>,
        communityTargets: Set<RecordType>,
    ): Set<RecordType> {
        val accepted = effectiveTypes intersect accepts(destination)
        return if (destination == Destination.COMMUNITY) accepted intersect communityTargets else accepted
    }
}
