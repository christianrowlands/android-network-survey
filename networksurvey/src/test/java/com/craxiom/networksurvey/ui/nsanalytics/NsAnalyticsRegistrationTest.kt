package com.craxiom.networksurvey.ui.nsanalytics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a failed registration response is explained: the ingestion server answers with
 * `{"error": ..., "error_code": ...}`, and only real token problems say the code expired.
 */
class NsAnalyticsRegistrationTest {

    @Test
    fun `the three token messages mean the code was rejected`() {
        for (message in listOf("Invalid or expired token", "Token already used", "Token expired")) {
            assertEquals(message, RegistrationError.CodeRejected, NsAnalyticsRegistration.classify(400, """{"error":"$message"}"""))
        }
    }

    @Test
    fun `a full workspace is a device limit, from either response`() {
        assertEquals(RegistrationError.DeviceLimit, NsAnalyticsRegistration.classify(400, """{"error":"Device limit reached (5/5)"}"""))
        assertEquals(
            RegistrationError.DeviceLimit,
            NsAnalyticsRegistration.classify(403, """{"error":"Device limit","error_code":"LICENSE_DEVICE_LIMIT"}"""),
        )
    }

    @Test
    fun `an expired license says so`() {
        assertEquals(
            RegistrationError.License,
            NsAnalyticsRegistration.classify(403, """{"error":"License expired","error_code":"LICENSE_EXPIRED"}"""),
        )
    }

    @Test
    fun `server errors are the server's problem`() {
        assertEquals(RegistrationError.Server, NsAnalyticsRegistration.classify(500, """{"error":"Database error"}"""))
        assertEquals(RegistrationError.Server, NsAnalyticsRegistration.classify(503, null))
    }

    @Test
    fun `anything else keeps the server's words, and a body that is not JSON is ignored`() {
        assertEquals(RegistrationError.Other("Invalid request body"), NsAnalyticsRegistration.classify(400, """{"error":"Invalid request body"}"""))
        assertEquals(RegistrationError.Other(null), NsAnalyticsRegistration.classify(418, "<html>teapot</html>"))
        assertEquals(RegistrationError.CodeRejected, NsAnalyticsRegistration.classify(401, ""))
    }
}
