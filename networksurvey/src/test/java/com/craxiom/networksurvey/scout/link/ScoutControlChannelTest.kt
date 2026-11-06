package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.ResultCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** One control request in flight, response matching by request ID and the 5 s timeout. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoutControlChannelTest {
    private val written = ArrayList<ControlRequest>()
    private val channel = ScoutControlChannel { written.add(ControlRequest.parseFrom(it)) }

    private fun response(id: Int, result: ResultCode = ResultCode.RESULT_CODE_OK) =
        ControlResponse.newBuilder().setRequestId(id).setResult(result).build()

    @Test
    fun send_waitsForTheResponseWithItsRequestId() = runTest {
        val pending = async { channel.send(ScoutProtocol.start()) }
        runCurrent()
        val id = written.single().requestId

        assertFalse(channel.onResponse(response(id + 1)))
        assertTrue(channel.onResponse(response(id)))
        assertEquals(id, pending.await().requestId)
    }

    @Test
    fun send_acceptsRequestIdZeroFromABoardThatCouldNotReadTheRequest() = runTest {
        val pending = async { channel.send(ScoutProtocol.start()) }
        runCurrent()
        assertTrue(channel.onResponse(response(0, ResultCode.RESULT_CODE_INVALID_ARGUMENT)))
        assertEquals(ResultCode.RESULT_CODE_INVALID_ARGUMENT, pending.await().result)
    }

    @Test
    fun send_keepsOneRequestInFlight() = runTest {
        val first = async { channel.send(ScoutProtocol.start()) }
        val second = async { channel.send(ScoutProtocol.stop()) }
        runCurrent()
        assertEquals(1, written.size)

        channel.onResponse(response(written[0].requestId))
        first.await()
        runCurrent()
        assertEquals(2, written.size)
        assertTrue(written[1].hasStop())
        assertTrue(written[1].requestId != written[0].requestId)
        channel.onResponse(response(written[1].requestId))
        second.await()
    }

    @Test
    fun send_timesOutAfterFiveSecondsAndIgnoresTheLateAnswer() = runTest {
        val pending = async { runCatching { channel.send(ScoutProtocol.start()) } }
        runCurrent()
        advanceTimeBy(ScoutProtocol.CONTROL_TIMEOUT_MS + 1)
        val error = pending.await().exceptionOrNull() as ScoutLinkException
        assertEquals(ScoutLinkFailure.TIMEOUT, error.failure)
        assertFalse(channel.onResponse(response(written.single().requestId)))
    }

    @Test
    fun sendExpectingOk_throwsRejectedForAnyOtherResult() = runTest {
        val pending = async { runCatching { channel.sendExpectingOk(ScoutProtocol.start()) } }
        runCurrent()
        channel.onResponse(response(written.single().requestId, ResultCode.RESULT_CODE_INVALID_STATE))
        val error = pending.await().exceptionOrNull()
        if (error !is ScoutLinkException) fail("Expected a ScoutLinkException, got $error")
        assertEquals(ScoutLinkFailure.REJECTED, (error as ScoutLinkException).failure)
    }

    @Test
    fun fail_endsThePendingRequestWithTheLinkFailure() = runTest {
        val pending = async { runCatching { channel.send(ScoutProtocol.start()) } }
        runCurrent()
        channel.fail(ScoutLinkException(ScoutLinkFailure.LINK_LOST, "gone"))
        assertEquals(ScoutLinkFailure.LINK_LOST, (pending.await().exceptionOrNull() as ScoutLinkException).failure)
    }
}
