package com.craxiom.networksurvey.survey.intent

import android.content.Intent
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.google.gson.Gson
import com.google.gson.JsonParseException
import timber.log.Timber

/**
 * Read-only access to an intent's extras, so the mapping can be unit tested without Android.
 */
interface IntentExtras {
    fun hasExtra(key: String): Boolean
    fun getBoolean(key: String): Boolean
    fun getString(key: String): String?
    fun getStringArray(key: String): Array<String>?
}

/** [IntentExtras] backed by a platform [Intent]. */
class AndroidIntentExtras(private val intent: Intent) : IntentExtras {
    override fun hasExtra(key: String): Boolean = intent.hasExtra(key)
    override fun getBoolean(key: String): Boolean = intent.getBooleanExtra(key, false)

    @Suppress("DEPRECATION")
    override fun getString(key: String): String? = intent.extras?.get(key) as? String

    @Suppress("DEPRECATION")
    override fun getStringArray(key: String): Array<String>? =
        (intent.extras?.get(key) as? Array<*>)?.filterIsInstance<String>()?.toTypedArray()
}

/**
 * What an Intent API `START_SURVEY` asks for, as a survey setup.
 *
 * @property setup              The record types and destinations requested.
 * @property mqttSettings       The broker from `mqtt_config_json`, or null if none was given.
 * @property usedLegacyExtras   True if any deprecated per-destination extra was used.
 * @property invalidValues      Values in `record_types` or `destinations` that were not recognized.
 */
data class IntentSurveyRequest(
    val setup: SurveySetup,
    val mqttSettings: MqttConnectionSettings?,
    val usedLegacyExtras: Boolean,
    val invalidValues: List<String>,
)

/**
 * Maps the Intent API extras onto one [SurveySetup].
 *
 * The documented form is two extras, each a string array or a comma separated string of wire
 * names: `record_types` (cellular, phone_state, wifi, bluetooth, gnss, cdr) and `destinations`
 * (log_files, ns_analytics, mqtt, grpc, community). `mqtt_config_json` still supplies the broker.
 *
 * The deprecated per-destination extras keep their names and are mapped as a union, because a
 * survey records one set of types for every destination:
 * - each `*_file_logging` extra adds its record type and the Log files destination;
 * - `mqtt_config_json` adds the MQTT destination, and its `*_stream_enabled` flags add record
 *   types. A JSON with no stream flags at all adds none: as in earlier versions, which connected
 *   MQTT and streamed only device status, an intent that names no record types anywhere gets a
 *   heartbeat only MQTT stream.
 */
object SurveyIntentMapper {

    private val FILE_LOGGING_EXTRAS: Map<String, RecordType> = linkedMapOf(
        NetworkSurveyConstants.EXTRA_CELLULAR_FILE_LOGGING to RecordType.CELLULAR,
        NetworkSurveyConstants.EXTRA_PHONE_STATE_FILE_LOGGING to RecordType.PHONE_STATE,
        NetworkSurveyConstants.EXTRA_WIFI_FILE_LOGGING to RecordType.WIFI,
        NetworkSurveyConstants.EXTRA_BLUETOOTH_FILE_LOGGING to RecordType.BLUETOOTH,
        NetworkSurveyConstants.EXTRA_GNSS_FILE_LOGGING to RecordType.GNSS,
        NetworkSurveyConstants.EXTRA_CDR_FILE_LOGGING to RecordType.CDR,
    )

    /**
     * @param extras The intent's extras.
     * @return The request, or null when the intent asks for nothing at all.
     */
    @JvmStatic
    fun map(extras: IntentExtras): IntentSurveyRequest? {
        val types = linkedSetOf<RecordType>()
        val destinations = linkedSetOf<Destination>()
        val invalid = mutableListOf<String>()
        var usedLegacy = false

        for (value in readList(extras, NetworkSurveyConstants.EXTRA_RECORD_TYPES)) {
            RecordType.fromWireName(value)?.let { types += it } ?: run { invalid += value }
        }
        for (value in readList(extras, NetworkSurveyConstants.EXTRA_DESTINATIONS)) {
            Destination.fromWireName(value)?.let { destinations += it } ?: run { invalid += value }
        }

        for ((extra, type) in FILE_LOGGING_EXTRAS) {
            if (extras.hasExtra(extra)) usedLegacy = true
            if (extras.getBoolean(extra)) {
                types += type
                destinations += Destination.LOG_FILES
            }
        }

        val mqttSettings = parseMqttSettings(extras.getString(NetworkSurveyConstants.EXTRA_MQTT_CONFIG_JSON))
        if (mqttSettings != null) {
            destinations += Destination.MQTT
            val streamFlags: Map<RecordType, Boolean?> = mapOf(
                RecordType.CELLULAR to mqttSettings.cellularStreamEnabled(),
                RecordType.PHONE_STATE to mqttSettings.phoneStateStreamEnabled(),
                RecordType.WIFI to mqttSettings.wifiStreamEnabled(),
                RecordType.BLUETOOTH to mqttSettings.bluetoothStreamEnabled(),
                RecordType.GNSS to mqttSettings.gnssStreamEnabled(),
            )
            // No stream flags adds no record types: without record types from another extra, the
            // stream sends only device status, as it did in earlier versions.
            if (streamFlags.values.any { it != null }) {
                usedLegacy = true
                types += streamFlags.filterValues { it == true }.keys
            }
        }

        if (types.isEmpty() && destinations.isEmpty()) return null
        return IntentSurveyRequest(SurveySetup.custom(types, destinations), mqttSettings, usedLegacy, invalid)
    }

    private fun readList(extras: IntentExtras, key: String): List<String> {
        val array = extras.getStringArray(key)
        val raw = array?.toList() ?: extras.getString(key)?.split(',') ?: return emptyList()
        return raw.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseMqttSettings(json: String?): MqttConnectionSettings? {
        if (json.isNullOrBlank()) return null
        return try {
            Gson().fromJson(json, MqttConnectionSettings::class.java)
        } catch (e: JsonParseException) {
            Timber.e(e, "Failed to parse the MQTT connection settings from the intent")
            null
        }
    }
}
