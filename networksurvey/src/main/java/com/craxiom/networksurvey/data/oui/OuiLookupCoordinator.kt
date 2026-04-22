package com.craxiom.networksurvey.data.oui

import com.craxiom.networksurvey.data.api.Api
import com.craxiom.networksurvey.data.api.OuiBatchRequest
import com.craxiom.networksurvey.data.api.OuiLookupResult
import com.craxiom.networksurvey.util.NsCrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import kotlin.random.Random

/**
 * Dispatches OUI lookups to the tower service.
 *
 * MVP exposes a single entry point - [lookupHigh] - which always batches (even for a single
 * prefix) because the JSON overhead is negligible and it keeps one code path. Concurrent
 * lookups for the same prefix share a [Deferred] so we never fire duplicate requests.
 *
 * Privacy: only the **24-bit OUI prefix** is sent to the server. Full MACs are never
 * transmitted. This does mean MA-M (28-bit) and MA-S (36-bit) registrants cannot be
 * disambiguated - but MA-L covers the vast majority of the dataset and keeps the wire cost
 * minimal. Callers cache results at 24-bit granularity accordingly.
 *
 * Backoff: 503 / 429 responses trigger exponential backoff (2 s → 60 s, ±25 % jitter). While
 * active, new calls short-circuit to `OFFLINE` so the UI degrades gracefully.
 */
class OuiLookupCoordinator(
    private val api: Api,
    private val datasetManager: OuiDatasetManager,
    private val scope: CoroutineScope
) {

    private val inFlightMutex = Mutex()
    private val inFlight: MutableMap<String, Deferred<OuiResult>> = mutableMapOf()

    @Volatile
    private var backoffUntilMs: Long = 0L
    private var currentBackoffMs: Long = INITIAL_BACKOFF_MS

    /**
     * Fires a single-prefix batch POST and returns the resolved [OuiResult]. Callers waiting on
     * an existing in-flight dispatch for the same prefix will share the result without re-
     * issuing the request.
     */
    suspend fun lookupHigh(prefix24String: String): OuiResult {
        val deferred = inFlightMutex.withLock {
            inFlight[prefix24String] ?: scope.async {
                try {
                    dispatchBatch(listOf(prefix24String))[prefix24String]
                        ?: OuiResult.TRANSIENT_FAILURE
                } finally {
                    // Only evict if the map still points at *this* Deferred. Without the identity
                    // check, a late-running finally could remove a successor's entry and break
                    // dedup for callers that arrived after we completed.
                    withContext(NonCancellable) {
                        inFlightMutex.withLock {
                            val self = coroutineContext[Job]
                            val current = inFlight[prefix24String]
                            if (current != null && (current as? Job) === self) {
                                inFlight.remove(prefix24String)
                            }
                        }
                    }
                }
            }.also { inFlight[prefix24String] = it }
        }
        return deferred.await()
    }

    private suspend fun dispatchBatch(prefixes: List<String>): Map<String, OuiResult> {
        val now = System.currentTimeMillis()
        if (now < backoffUntilMs) {
            return prefixes.associateWith { OuiResult.OFFLINE }
        }

        val response = try {
            api.getOuiBatch(OuiBatchRequest(macs = prefixes))
        } catch (io: IOException) {
            Timber.d(io, "OUI batch network failure")
            return prefixes.associateWith { OuiResult.OFFLINE }
        } catch (t: Throwable) {
            Timber.w(t, "OUI batch unexpected error")
            NsCrashReporter.recordException(t, "OUI batch unexpected error")
            return prefixes.associateWith { OuiResult.TRANSIENT_FAILURE }
        }

        return when {
            response.isSuccessful -> {
                resetBackoff()
                val body = response.body()
                if (body == null) {
                    Timber.w("OUI batch returned empty body")
                    return prefixes.associateWith { OuiResult.TRANSIENT_FAILURE }
                }
                body.datasetVersion?.let { datasetManager.rememberVersion(it) }
                prefixes.mapIndexedNotNull { index, prefix ->
                    val item = body.results.getOrNull(index)
                        ?: return@mapIndexedNotNull prefix to OuiResult.TRANSIENT_FAILURE
                    prefix to mapResponseItem(item)
                }.toMap()
            }

            response.code() == 503 || response.code() == 429 -> {
                applyBackoff()
                prefixes.associateWith { OuiResult.OFFLINE }
            }

            else -> {
                Timber.e("OUI batch failed with HTTP %d", response.code())
                NsCrashReporter.recordException(
                    IllegalStateException("OUI batch HTTP ${response.code()}"),
                    "OUI batch HTTP ${response.code()}"
                )
                prefixes.associateWith { OuiResult.TRANSIENT_FAILURE }
            }
        }
    }

    private fun mapResponseItem(item: OuiLookupResult): OuiResult {
        if (item.isUnknown) {
            return when (item.reason) {
                REASON_LOCALLY_ADMINISTERED -> OuiResult.LAA
                REASON_REGISTERED_TO_SUBREGISTRY -> OuiResult.SHARED_VENDOR_BLOCK
                // invalid_mac and no_match both surface as UNKNOWN in the UI
                else -> OuiResult.UNKNOWN
            }
        }
        val vendor = item.vendor
        return when {
            vendor.isNullOrEmpty() -> OuiResult.UNKNOWN
            vendor.equals(PRIVATE_VENDOR_LITERAL, ignoreCase = true) ->
                OuiResult.privateVendor(vendor)

            else -> OuiResult.resolved(vendor)
        }
    }

    private fun applyBackoff() {
        val jitterRange = (currentBackoffMs * JITTER_FRACTION).toLong().coerceAtLeast(1L)
        val jitter = Random.nextLong(-jitterRange, jitterRange + 1)
        val delay = (currentBackoffMs + jitter).coerceAtLeast(INITIAL_BACKOFF_MS)
        backoffUntilMs = System.currentTimeMillis() + delay
        currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        Timber.w("OUI backoff active for %d ms", delay)
    }

    private fun resetBackoff() {
        backoffUntilMs = 0L
        currentBackoffMs = INITIAL_BACKOFF_MS
    }

    companion object {
        private const val INITIAL_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val JITTER_FRACTION = 0.25

        // Only the reason strings the client actually branches on are kept; `invalid_mac` and
        // `no_match` both flow through the UNKNOWN catch-all so they don't need a constant.
        const val REASON_LOCALLY_ADMINISTERED = "locally_administered"
        const val REASON_REGISTERED_TO_SUBREGISTRY = "registered_to_ieee_subregistry"

        private const val PRIVATE_VENDOR_LITERAL = "Private"
    }
}
