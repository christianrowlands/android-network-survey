package com.craxiom.networksurvey.ui.grpc

import com.craxiom.networksurvey.fragments.model.GrpcConnectionSettings
import com.craxiom.networksurvey.services.GrpcConnectionService.ConnectionFailure
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.survey.status.StatusWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the gRPC page's field checks and how it describes the saved server. */
class GrpcServerFormTest {

    private fun validate(host: String = "grpc.example.net", port: String = "2621", name: String = "") =
        GrpcServerForm(host = host, port = port, deviceName = name).validate()

    @Test
    fun `a host and port make the settings, trimmed`() {
        val result = validate(host = "  grpc.example.net ", port = " 9090 ", name = " field-phone-1 ")

        assertEquals(GrpcConnectionSettings("grpc.example.net", 9090, "field-phone-1"), result.settings)
        assertFalse(result.hostError)
        assertFalse(result.portError)
    }

    @Test
    fun `addresses in every form are accepted`() {
        assertEquals("192.0.2.4", validate(host = "192.0.2.4").settings?.host)
        assertEquals("localhost", validate(host = "localhost").settings?.host)
        assertEquals("2001:db8::4", validate(host = "2001:db8::4").settings?.host)
        assertEquals("2001:db8::4", validate(host = "[2001:db8::4]").settings?.host)
    }

    @Test
    fun `the device name is optional`() {
        assertEquals("", validate(name = "").settings?.deviceName)
    }

    @Test
    fun `an empty or malformed host is an error`() {
        for (host in listOf("", "   ", "grpc example", "http://grpc.example.net", "grpc.example.net:2621")) {
            val result = validate(host = host)
            assertNull(host, result.settings)
            assertTrue(host, result.hostError)
        }
    }

    @Test
    fun `a port outside 1 to 65535 or not a number is an error`() {
        for (port in listOf("", "0", "65536", "port", "-1")) {
            val result = validate(port = port)
            assertNull(port, result.settings)
            assertTrue(port, result.portError)
            assertFalse(port, result.hostError)
        }
        assertEquals(65535, validate(port = "65535").settings?.port)
        assertEquals(1, validate(port = "1").settings?.port)
    }

    @Test
    fun `Save turns gRPC on only for a first server, idle, allowed and not already in the setup`() {
        assertTrue(GrpcServerForm(loading = false).savesAndTurnsOn)
        assertFalse(GrpcServerForm(configured = true).savesAndTurnsOn)
        assertFalse(GrpcServerForm(recording = true).savesAndTurnsOn)
        assertFalse(GrpcServerForm(organizationOff = true).savesAndTurnsOn)
        assertFalse(GrpcServerForm(inSetup = true).savesAndTurnsOn)
    }

    @Test
    fun `the status follows the saved server, the setup and the running survey`() {
        val saved = GrpcConnectionSettings("grpc.example.net", 2621, "")
        val address = "grpc.example.net:2621"

        assertEquals(GrpcStatus.NotSetUp, grpcStatus(null, inSetup = true, live = null, lastFailure = null))
        assertEquals(GrpcStatus.Ready(address), grpcStatus(saved, inSetup = true, live = null, lastFailure = null))
        assertEquals(GrpcStatus.OffInSurvey(address), grpcStatus(saved, inSetup = false, live = null, lastFailure = null))
        assertEquals(
            GrpcStatus.Live(address, DestinationStatus(StatusWord.LIVE)),
            grpcStatus(saved, inSetup = true, live = DestinationStatus(StatusWord.LIVE), lastFailure = null),
        )
    }

    @Test
    fun `a failure to reach the saved server is shown, and whether the survey keeps trying`() {
        val saved = GrpcConnectionSettings("grpc.example.net", 2621, "")
        val failed = ConnectionFailure("grpc.example.net", 2621, 1L)

        assertEquals(
            GrpcStatus.Unreachable("grpc.example.net:2621", retrying = true),
            grpcStatus(saved, inSetup = true, live = DestinationStatus(StatusWord.CONNECTING), lastFailure = failed),
        )
        assertEquals(
            GrpcStatus.Unreachable("grpc.example.net:2621", retrying = false),
            grpcStatus(saved, inSetup = true, live = null, lastFailure = failed),
        )
        // A failure for a server that is no longer the saved one does not count.
        val other = ConnectionFailure("old.example.net", 2621, 1L)
        assertEquals(GrpcStatus.Ready("grpc.example.net:2621"), grpcStatus(saved, inSetup = true, live = null, lastFailure = other))
    }

    @Test
    fun `streaming covers a survey that is still trying to reach the server`() {
        assertTrue(GrpcServerForm(status = GrpcStatus.Live("a:1", DestinationStatus(StatusWord.CONNECTING))).streaming)
        assertTrue(GrpcServerForm(status = GrpcStatus.Unreachable("a:1", retrying = true)).streaming)
        assertFalse(GrpcServerForm(status = GrpcStatus.Unreachable("a:1", retrying = false)).streaming)
        assertFalse(GrpcServerForm(status = GrpcStatus.Ready("a:1")).streaming)
    }
}
