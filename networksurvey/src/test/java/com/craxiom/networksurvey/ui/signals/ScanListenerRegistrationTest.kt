package com.craxiom.networksurvey.ui.signals

import com.craxiom.networksurvey.services.NetworkSurveyService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/** The shared rule for when a Signals view keeps its record listener registered with the service. */
class ScanListenerRegistrationTest {

    private val calls = mutableListOf<String>()
    private val service = mock<NetworkSurveyService>()
    private val registration = ScanListenerRegistration(
        register = { calls += "register" },
        unregister = { calls += "unregister" },
        onRegistered = { calls += "registered" },
        onUnregistered = { calls += "unregistered" },
    )

    @Test
    fun registersOnlyWhenBoundAndReady() {
        registration.update(ready = true)
        assertEquals(emptyList<String>(), calls)

        registration.onServiceChanged(service, ready = false)
        assertEquals(emptyList<String>(), calls)

        registration.update(ready = true)
        assertEquals(listOf("register", "registered"), calls)
        assertTrue(registration.isRegistered)
    }

    @Test
    fun registeringTwiceIsANoOp() {
        registration.onServiceChanged(service, ready = true)
        registration.update(ready = true)
        registration.onServiceChanged(service, ready = true)

        assertEquals(listOf("register", "registered"), calls)
    }

    @Test
    fun anObstacleUnregisters_andClearingItRegistersAgain() {
        registration.onServiceChanged(service, ready = true)
        registration.update(ready = false)
        assertFalse(registration.isRegistered)
        registration.update(ready = true)

        assertEquals(listOf("register", "registered", "unregister", "unregistered", "register", "registered"), calls)
    }

    @Test
    fun aNullServiceAlwaysUnregisters_andNothingRegistersUntilTheNextBind() {
        registration.onServiceChanged(service, ready = true)
        registration.onServiceChanged(null, ready = true)
        registration.update(ready = true)

        assertEquals(listOf("register", "registered", "unregister", "unregistered"), calls)
        assertFalse(registration.isRegistered)
    }

    @Test
    fun aDifferentService_getsTheListenerAfresh() {
        val other = mock<NetworkSurveyService>()
        val calls = mutableListOf<Pair<String, NetworkSurveyService>>()
        val registration = ScanListenerRegistration(
            register = { calls += "register" to it },
            unregister = { calls += "unregister" to it },
        )

        registration.onServiceChanged(service, ready = true)
        registration.onServiceChanged(other, ready = true)

        assertEquals(listOf("register" to service, "unregister" to service, "register" to other), calls)
    }

    @Test
    fun releaseWithoutARegistrationDoesNothing() {
        registration.release()
        registration.onServiceChanged(service, ready = false)
        registration.release()

        assertEquals(emptyList<String>(), calls)
    }
}
