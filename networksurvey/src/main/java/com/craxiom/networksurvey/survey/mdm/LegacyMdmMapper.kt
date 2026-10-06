package com.craxiom.networksurvey.survey.mdm

import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup

/**
 * Maps the pre-v2 MDM keys onto the v2 survey setup model, for one release only, so fleets keep
 * working between the app update and the admin pushing the new keys. Remove this class (and the
 * deprecated keys in `app_restrictions.xml`) in the release after v2.
 *
 * Rules:
 * - Each dimension (record types, destinations, auto start) falls back independently, and only
 *   when the organization has not set any v2 key in that dimension to something other than `user`.
 * - Only keys that are actually present are mapped. A missing key never implies a value, because
 *   consoles never delivered schema defaults to the app and the old readers fell back to the
 *   user's own setting when a key was absent.
 * - Old keys never create record-type locks. The old keys only controlled what auto started or
 *   what MQTT streamed; they never stopped the user from logging a type. They seed the
 *   organization default setup instead.
 * - An MDM broker maps to MQTT turned on by the organization, matching the old behavior where the
 *   Dashboard hid the MQTT switch for a managed broker.
 */
object LegacyMdmMapper {

    private val AUTO_START_KEYS: Map<RecordType, String> = linkedMapOf(
        RecordType.CELLULAR to NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING,
        RecordType.PHONE_STATE to NetworkSurveyConstants.PROPERTY_AUTO_START_PHONE_STATE_LOGGING,
        RecordType.WIFI to NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING,
        RecordType.BLUETOOTH to NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING,
        RecordType.GNSS to NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING,
        RecordType.CDR to NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING,
    )

    /** Old MQTT stream keys and the defaults the old MDM reader used when a key was absent. */
    private val STREAM_KEYS: Map<RecordType, Pair<String, Boolean>> = linkedMapOf(
        RecordType.CELLULAR to (NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING),
        RecordType.PHONE_STATE to (NetworkSurveyConstants.PROPERTY_MQTT_PHONE_STATE_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_PHONE_STATE_STREAM_SETTING),
        RecordType.WIFI to (NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING),
        RecordType.BLUETOOTH to (NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING),
        RecordType.GNSS to (NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED to NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING),
    )

    /** Every deprecated key this mapper reads. They stay in the schema for one release. */
    @JvmStatic
    val DEPRECATED_KEYS: List<String> =
        AUTO_START_KEYS.values + STREAM_KEYS.values.map { it.first } + NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT

    /**
     * The result of mapping the old keys.
     *
     * @property destinationLocks Destinations the old keys turned on (only MQTT, for an MDM broker).
     * @property autoStart        [ManagedState.ON] if an old auto start key was present and true.
     * @property seedSetup        The organization default setup the old keys describe, or null when
     *                            they describe nothing. It seeds the user's setup; it is not a lock.
     */
    data class Result(
        val destinationLocks: Map<Destination, ManagedState>,
        val autoStart: ManagedState,
        val seedSetup: SurveySetup?,
    ) {
        val isEmpty: Boolean
            get() = destinationLocks.isEmpty() && autoStart == ManagedState.USER && seedSetup == null
    }

    /**
     * @param snapshot              The managed configuration.
     * @param typesManagedByV2      True if any v2 `survey_*` key is set to something other than user.
     * @param destinationsManagedByV2 True if any v2 `destination_*` key is set to something other than user.
     * @param autoStartManagedByV2  True if the v2 `auto_start_survey` key is set to something other than user.
     */
    @JvmStatic
    fun map(
        snapshot: RestrictionsSnapshot,
        typesManagedByV2: Boolean,
        destinationsManagedByV2: Boolean,
        autoStartManagedByV2: Boolean,
    ): Result {
        val hasMdmBroker = hasMdmBroker(snapshot)

        val autoStartTypes = AUTO_START_KEYS.filter { (_, key) -> snapshot.getBoolean(key) == true }.keys
        val streamTypes = if (hasMdmBroker) {
            STREAM_KEYS.filter { (_, keyAndDefault) ->
                snapshot.getBoolean(keyAndDefault.first) ?: keyAndDefault.second
            }.keys
        } else {
            emptySet()
        }

        val destinationLocks = if (!destinationsManagedByV2 && hasMdmBroker) {
            mapOf(Destination.MQTT to ManagedState.ON)
        } else {
            emptyMap()
        }

        val mqttStartOnBoot = hasMdmBroker &&
            snapshot.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT) == true
        val autoStart = if (!autoStartManagedByV2 && (autoStartTypes.isNotEmpty() || mqttStartOnBoot)) {
            ManagedState.ON
        } else {
            ManagedState.USER
        }

        val seedTypes = if (typesManagedByV2) emptySet() else autoStartTypes + streamTypes
        val seedDestinations = buildSet {
            if (!destinationsManagedByV2) {
                if (autoStartTypes.isNotEmpty()) add(Destination.LOG_FILES)
                if (hasMdmBroker) add(Destination.MQTT)
            }
        }
        val seedSetup = if (seedTypes.isEmpty() && seedDestinations.isEmpty()) {
            null
        } else {
            SurveySetup.custom(seedTypes, seedDestinations)
        }

        return Result(destinationLocks, autoStart, seedSetup)
    }

    /**
     * @return True if the managed configuration carries a usable MQTT broker (a host and a client
     * id), the same test the service uses before connecting to an MDM broker.
     */
    @JvmStatic
    fun hasMdmBroker(snapshot: RestrictionsSnapshot): Boolean =
        !snapshot.getString(MqttConstants.PROPERTY_MQTT_CONNECTION_HOST).isNullOrBlank() &&
            !snapshot.getString(MqttConstants.PROPERTY_MQTT_CLIENT_ID).isNullOrBlank()
}
