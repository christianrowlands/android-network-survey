package com.craxiom.networksurvey.survey.model

/**
 * The record type x destination capability matrix, and the rules that turn a [SurveySetup] into
 * the exact set of record types each destination receives.
 *
 * Every destination receives everything the survey records, except where the destination cannot
 * accept a type:
 * - Log files accept every type (CDR and Raw frames are files only).
 * - MQTT and gRPC accept everything except CDR and Raw frames.
 * - NS Analytics accepts the phone types only: not CDR, Wi-Fi clients or Raw frames.
 * - The community databases accept Cellular (OpenCelliD and BeaconDB) and Wi-Fi (BeaconDB only),
 *   narrowed further by which upload targets the user selected.
 *
 * Every set is listed explicitly, so a new record type reaches no destination until it is added
 * here on purpose.
 */
object DestinationCapabilities {

    /** The phone's own record types, which every streaming destination takes. */
    private val PHONE_STREAMING_TYPES: Set<RecordType> = setOf(
        RecordType.CELLULAR, RecordType.PHONE_STATE, RecordType.WIFI, RecordType.BLUETOOTH, RecordType.GNSS,
    )
    private val LOG_FILE_TYPES: Set<RecordType> =
        PHONE_STREAMING_TYPES + RecordType.CDR + RecordType.WIFI_CLIENTS + RecordType.WIFI_RAW
    private val NS_ANALYTICS_TYPES: Set<RecordType> = PHONE_STREAMING_TYPES
    private val STREAM_TYPES: Set<RecordType> = PHONE_STREAMING_TYPES + RecordType.WIFI_CLIENTS
    private val COMMUNITY_TYPES: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.WIFI)

    /**
     * Types that only Log files take and that a start reports as left out without Log files
     * ([com.craxiom.networksurvey.survey.engine.SkipReason.NEEDS_LOG_FILES]). CDR is files only too,
     * but has always been left out quietly.
     */
    @JvmField
    val LOG_FILES_ONLY_TYPES: Set<RecordType> = setOf(RecordType.WIFI_RAW)

    /** What OpenCelliD takes; matches `PreferenceUtils.getRequiredProtocolsForUpload`. */
    @JvmField
    val OPENCELLID_TYPES: Set<RecordType> = setOf(RecordType.CELLULAR)

    /** What BeaconDB takes; matches `PreferenceUtils.getRequiredProtocolsForUpload`. */
    @JvmField
    val BEACONDB_TYPES: Set<RecordType> = setOf(RecordType.CELLULAR, RecordType.WIFI)

    /** @return Every record type [destination] can accept, before any user or upload target choice. */
    @JvmStatic
    fun accepts(destination: Destination): Set<RecordType> = when (destination) {
        Destination.LOG_FILES -> LOG_FILE_TYPES
        Destination.NS_ANALYTICS -> NS_ANALYTICS_TYPES
        Destination.MQTT, Destination.GRPC -> STREAM_TYPES
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
