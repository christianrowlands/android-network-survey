package com.craxiom.networksurvey.data.plmn

import com.craxiom.networksurvey.data.api.PlmnRecord

/**
 * Outcome of a PLMN search or resolve request. UI-only model; not persisted.
 *
 * The [Loaded] state wraps a (possibly empty) list of records along with the dataset version
 * reported by the server. An empty list represents "no match" - for both the search endpoint
 * (which returns an empty results array) and the resolve endpoint (which returns HTTP 200 with
 * `result.is_unknown = true`). Callers don't need to distinguish these on the UI surface.
 */
sealed class PlmnResult {

    /** No request has been issued yet (mode tabs visible, input empty). */
    object Idle : PlmnResult()

    /** A request is in flight. The UI shows a pulsing indicator. */
    object Loading : PlmnResult()

    /**
     * Server responded successfully.
     *
     * @param records Operator records, in server order. Empty when there was no match.
     * @param datasetVersion The opaque version hash supplied by the backend.
     * @param truncated True when the search endpoint capped the response (more matches existed).
     */
    data class Loaded(
        val records: List<PlmnRecord>,
        val datasetVersion: String?,
        val truncated: Boolean = false
    ) : PlmnResult()

    /** The device is offline or the request could not reach the backend (IOException). */
    object Offline : PlmnResult()

    /**
     * Server returned a persistent error (5xx / 4xx) or an unexpected exception was thrown.
     *
     * @param httpCode HTTP status if available, null when the failure was a thrown exception.
     */
    data class TransientFailure(val httpCode: Int? = null) : PlmnResult()
}
