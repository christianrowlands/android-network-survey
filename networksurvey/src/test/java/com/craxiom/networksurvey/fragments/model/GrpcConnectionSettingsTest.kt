package com.craxiom.networksurvey.fragments.model

import android.content.Context
import androidx.core.content.edit
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Tests where the gRPC server settings are kept, and how the address is shown. */
@RunWith(RobolectricTestRunner::class)
class GrpcConnectionSettingsTest {

    private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("grpc_settings_test", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        preferences.edit(commit = true) { clear() }
    }

    @Test
    fun `saved settings read back the same, under the keys earlier versions used`() {
        val settings = GrpcConnectionSettings("grpc.example.net", 9090, "field-phone-1")
        GrpcConnectionSettings.save(preferences, settings)

        assertEquals(settings, GrpcConnectionSettings.fromPreferences(preferences))
        assertEquals("grpc.example.net", preferences.getString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_HOST, null))
        assertEquals("connection_host", NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_HOST)
        assertEquals("connection_port", NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_PORT)
        assertEquals("device_name", NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_DEVICE_NAME)
    }

    @Test
    fun `no host or a blank host means gRPC is not set up`() {
        assertNull(GrpcConnectionSettings.fromPreferences(preferences))

        preferences.edit(commit = true) { putString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_HOST, "  ") }
        assertNull(GrpcConnectionSettings.fromPreferences(preferences))
    }

    @Test
    fun `a host saved without a port or name uses the default port and sends no name`() {
        preferences.edit(commit = true) { putString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_HOST, "grpc.example.net") }

        assertEquals(
            GrpcConnectionSettings("grpc.example.net", NetworkSurveyConstants.DEFAULT_GRPC_PORT, ""),
            GrpcConnectionSettings.fromPreferences(preferences),
        )
    }

    @Test
    fun `an IPv6 address is shown in brackets so the port stays readable`() {
        assertEquals("grpc.example.net:2621", GrpcConnectionSettings("grpc.example.net", 2621, "").displayAddress)
        assertEquals("192.0.2.4:2621", GrpcConnectionSettings("192.0.2.4", 2621, "").displayAddress)
        assertEquals("[2001:db8::4]:2621", GrpcConnectionSettings("2001:db8::4", 2621, "").displayAddress)
    }
}
