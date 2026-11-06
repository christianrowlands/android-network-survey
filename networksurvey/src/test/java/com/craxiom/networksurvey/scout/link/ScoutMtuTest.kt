package com.craxiom.networksurvey.scout.link

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MTU exchange at connect, including the stuck reconnect seen on a Pixel 6: Android's Service
 * Changed indication draws an ATT Error Response (status 6) from the board, Android pairs it with
 * the MTU request and never exchanges the MTU again on that connection, so the link is held for
 * Android's 30 s Service Changed timeout and then fails, which makes the next connection clean.
 */
class ScoutMtuTest {
    private val holds = mutableListOf<Long>()
    private val hold: suspend (Long) -> Unit = { holds += it }

    @Test
    fun aSuccessfulExchange_givesItsMtu() = runTest {
        assertEquals(517, ScoutMtu.negotiate(exchange = { ScoutMtu.Answer(status = 0, mtu = 517) }, hold = hold))
        assertEquals(emptyList<Long>(), holds)
    }

    @Test
    fun aTimeout_continuesAtTheDefaultMtu() = runTest {
        assertEquals(ScoutMtu.DEFAULT_ATT_MTU, ScoutMtu.negotiate(exchange = { null }, hold = hold))
        assertEquals(emptyList<Long>(), holds)
    }

    @Test
    fun anErrorAnswer_holdsTheLinkPastAndroidsServiceChangedTimeout_thenFails() = runTest {
        val failure = try {
            ScoutMtu.negotiate(exchange = { ScoutMtu.Answer(status = REQUEST_NOT_SUPPORTED, mtu = 23) }, hold = hold)
            null
        } catch (e: ScoutLinkException) {
            e.failure
        }
        assertEquals(ScoutLinkFailure.GATT_ERROR, failure)
        assertEquals(listOf(ScoutMtu.SERVICE_CHANGED_TIMEOUT_MS), holds)
        assertTrue(ScoutMtu.SERVICE_CHANGED_TIMEOUT_MS > ANDROID_SERVICE_CHANGED_TIMEOUT_MS)
    }

    private companion object {
        /** The ATT error the board answers Android's Service Changed indication with. */
        const val REQUEST_NOT_SUPPORTED = 6

        /** How long Android waits for a Service Changed confirmation (`gatt_utils.cc`). */
        const val ANDROID_SERVICE_CHANGED_TIMEOUT_MS = 30_000L
    }
}
