package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.ResultCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Sends control requests to a Scout one at a time and matches each indication to its request
 * (protocol.md section 6). The ATT write response only confirms delivery; the result is the
 * `ControlResponse` indication with the same request ID, or ID 0 when the board could not read
 * the request. A response that matches nothing is a late answer to a request that already timed
 * out and is ignored.
 *
 * Used on the Scout thread only.
 */
class ScoutControlChannel(private val write: suspend (ByteArray) -> Unit) {
    private val mutex = Mutex()
    private var nextRequestId = 1
    private var pendingRequestId: Int? = null
    private var pendingResponse: CompletableDeferred<ControlResponse>? = null

    /**
     * Sends [request] (its request ID is set here) and waits for the response.
     *
     * @throws ScoutLinkException [ScoutLinkFailure.TIMEOUT] when no response arrives within
     * [timeoutMs], or the failure that ended the link meanwhile.
     */
    suspend fun send(
        request: ControlRequest.Builder,
        timeoutMs: Long = ScoutProtocol.timeoutFor(request),
    ): ControlResponse = mutex.withLock {
        val requestId = takeRequestId()
        val bytes = request.setRequestId(requestId).build().toByteArray()
        require(bytes.size <= ScoutGatt.CONTROL_REQUEST_MAX_BYTES) {
            "A ${request.commandCase} request is ${bytes.size} bytes, over the board's limit"
        }
        val response = CompletableDeferred<ControlResponse>()
        pendingRequestId = requestId
        pendingResponse = response
        val command = request.commandCase
        val started = System.nanoTime()
        try {
            val answer = withTimeoutOrNull(timeoutMs) {
                write(bytes)
                response.await()
            } ?: throw ScoutLinkException(ScoutLinkFailure.TIMEOUT, "no answer to $command")
            Timber.d(
                "Scout %s #%d answered %s in %d ms", command, requestId, answer.result,
                (System.nanoTime() - started) / NANOS_PER_MILLI,
            )
            answer
        } finally {
            pendingRequestId = null
            pendingResponse = null
        }
    }

    /** Sends [request] and throws [ScoutLinkFailure.REJECTED] unless the board answers OK. */
    suspend fun sendExpectingOk(request: ControlRequest.Builder): ControlResponse {
        val command = request.commandCase
        val response = send(request)
        if (response.result != ResultCode.RESULT_CODE_OK) {
            throw ScoutLinkException(
                ScoutLinkFailure.REJECTED, "$command answered ${response.result} ${response.message}", boardResult = response.result,
            )
        }
        return response
    }

    /** Delivers one decoded Control indication. @return True when it answered the pending request. */
    fun onResponse(response: ControlResponse): Boolean {
        val pending = pendingRequestId
        if (pending == null || (response.requestId != pending && response.requestId != 0)) {
            Timber.i("Ignoring Scout control response %d with no matching request", response.requestId)
            return false
        }
        pendingResponse?.complete(response)
        return true
    }

    /** Fails the pending request, if any, because the link ended. */
    fun fail(error: ScoutLinkException) {
        pendingResponse?.completeExceptionally(error)
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }

    private fun takeRequestId(): Int {
        val id = nextRequestId
        nextRequestId = if (nextRequestId == Int.MAX_VALUE) 1 else nextRequestId + 1
        return id
    }
}
