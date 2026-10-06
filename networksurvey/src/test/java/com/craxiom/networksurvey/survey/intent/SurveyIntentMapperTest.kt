package com.craxiom.networksurvey.survey.intent

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that the Intent API extras, old and new, map onto one survey setup.
 */
class SurveyIntentMapperTest {

    private class MapExtras(private val values: Map<String, Any>) : IntentExtras {
        override fun hasExtra(key: String) = values.containsKey(key)
        override fun getBoolean(key: String) = values[key] as? Boolean ?: false
        override fun getString(key: String) = values[key] as? String
        override fun getStringArray(key: String): Array<String>? {
            @Suppress("UNCHECKED_CAST")
            return values[key] as? Array<String>
        }
    }

    private val fallback = setOf(RecordType.CELLULAR, RecordType.WIFI)

    private fun map(vararg pairs: Pair<String, Any>) = SurveyIntentMapper.map(MapExtras(mapOf(*pairs)), fallback)

    @Test
    fun `the extra names are pinned`() {
        // External tooling depends on these exact names.
        assertEquals("cellular_file_logging", NetworkSurveyConstants.EXTRA_CELLULAR_FILE_LOGGING)
        assertEquals("phone_state_file_logging", NetworkSurveyConstants.EXTRA_PHONE_STATE_FILE_LOGGING)
        assertEquals("wifi_file_logging", NetworkSurveyConstants.EXTRA_WIFI_FILE_LOGGING)
        assertEquals("bluetooth_file_logging", NetworkSurveyConstants.EXTRA_BLUETOOTH_FILE_LOGGING)
        assertEquals("gnss_file_logging", NetworkSurveyConstants.EXTRA_GNSS_FILE_LOGGING)
        assertEquals("cdr_file_logging", NetworkSurveyConstants.EXTRA_CDR_FILE_LOGGING)
        assertEquals("mqtt_config_json", NetworkSurveyConstants.EXTRA_MQTT_CONFIG_JSON)
        assertEquals("record_types", NetworkSurveyConstants.EXTRA_RECORD_TYPES)
        assertEquals("destinations", NetworkSurveyConstants.EXTRA_DESTINATIONS)
    }

    @Test
    fun `an intent with no extras asks for nothing`() {
        assertNull(map())
    }

    @Test
    fun `each legacy file logging extra adds its type and log files`() {
        val request = map(
            NetworkSurveyConstants.EXTRA_CELLULAR_FILE_LOGGING to true,
            NetworkSurveyConstants.EXTRA_GNSS_FILE_LOGGING to true,
            NetworkSurveyConstants.EXTRA_WIFI_FILE_LOGGING to false,
        )!!
        assertEquals(setOf(RecordType.CELLULAR, RecordType.GNSS), request.setup.recordTypes)
        assertEquals(setOf(Destination.LOG_FILES), request.setup.destinations)
        assertTrue(request.usedLegacyExtras)
    }

    @Test
    fun `legacy file extras and MQTT stream flags map as a union`() {
        val json = """{"mqtt_host":"broker","mqtt_port":8883,"mqtt_client":"d1","wifi_stream_enabled":true,"gnss_stream_enabled":false}"""
        val request = map(
            NetworkSurveyConstants.EXTRA_CELLULAR_FILE_LOGGING to true,
            NetworkSurveyConstants.EXTRA_MQTT_CONFIG_JSON to json,
        )!!
        assertEquals(setOf(RecordType.CELLULAR, RecordType.WIFI), request.setup.recordTypes)
        assertEquals(setOf(Destination.LOG_FILES, Destination.MQTT), request.setup.destinations)
        assertNotNull(request.mqttSettings)
        assertEquals("broker", request.mqttSettings!!.host())
    }

    @Test
    fun `an MQTT JSON without stream flags streams the current setup's types`() {
        val request = map(NetworkSurveyConstants.EXTRA_MQTT_CONFIG_JSON to """{"mqtt_host":"broker","mqtt_port":1883,"mqtt_client":"d1"}""")!!
        assertEquals(fallback, request.setup.recordTypes)
        assertEquals(setOf(Destination.MQTT), request.setup.destinations)
        assertFalse(request.usedLegacyExtras)
    }

    @Test
    fun `new extras accept a string array`() {
        val request = map(
            NetworkSurveyConstants.EXTRA_RECORD_TYPES to arrayOf("cellular", "bluetooth"),
            NetworkSurveyConstants.EXTRA_DESTINATIONS to arrayOf("log_files", "ns_analytics"),
        )!!
        assertEquals(setOf(RecordType.CELLULAR, RecordType.BLUETOOTH), request.setup.recordTypes)
        assertEquals(setOf(Destination.LOG_FILES, Destination.NS_ANALYTICS), request.setup.destinations)
        assertFalse(request.usedLegacyExtras)
    }

    @Test
    fun `new extras accept a comma separated string and report unknown values`() {
        val request = map(
            NetworkSurveyConstants.EXTRA_RECORD_TYPES to " wifi, LTE ,gnss",
            NetworkSurveyConstants.EXTRA_DESTINATIONS to "mqtt,ftp",
        )!!
        assertEquals(setOf(RecordType.WIFI, RecordType.GNSS), request.setup.recordTypes)
        assertEquals(setOf(Destination.MQTT), request.setup.destinations)
        assertEquals(listOf("LTE", "ftp"), request.invalidValues)
    }

    @Test
    fun `a malformed MQTT JSON is ignored`() {
        val request = map(
            NetworkSurveyConstants.EXTRA_CELLULAR_FILE_LOGGING to true,
            NetworkSurveyConstants.EXTRA_MQTT_CONFIG_JSON to "{not json",
        )!!
        assertNull(request.mqttSettings)
        assertEquals(setOf(Destination.LOG_FILES), request.setup.destinations)
    }

    @Test
    fun `legacy extras never name NS Analytics`() {
        val request = map(
            NetworkSurveyConstants.EXTRA_CELLULAR_FILE_LOGGING to true,
            NetworkSurveyConstants.EXTRA_MQTT_CONFIG_JSON to """{"mqtt_host":"b","mqtt_port":1,"mqtt_client":"c","cellular_stream_enabled":true}""",
        )!!
        assertFalse(Destination.NS_ANALYTICS in request.setup.destinations)
    }
}
