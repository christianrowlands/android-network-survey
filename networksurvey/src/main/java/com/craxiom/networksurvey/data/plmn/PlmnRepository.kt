package com.craxiom.networksurvey.data.plmn

import android.util.LruCache
import com.craxiom.networksurvey.data.api.Api
import com.craxiom.networksurvey.data.api.PlmnRecord
import com.craxiom.networksurvey.data.api.retrofit
import timber.log.Timber
import java.io.IOException

/**
 * Public entry point for PLMN search + resolve.
 *
 * Concurrency model: both [search] and [resolve] are regular `suspend` functions. Callers
 * dispatch them inside a `viewModelScope.launch` (or any other structured-concurrency scope).
 * Cancellation of the caller cancels the outstanding network call correctly.
 *
 * Cache: a single in-memory LRU keyed by request shape (search query or "mcc-mnc"). Entries
 * carry a TTL so they age out within a session, and the entire cache is cleared when a new
 * response reports a different `datasetVersion` (the backend updated its dataset mid-session).
 *
 * No persistent cache layer in v1 — the dataset is small and the screen is stateless across
 * sessions per the PLMN lookup design brief.
 */
class PlmnRepository private constructor(
    private val api: Api
) {

    private val cache: LruCache<String, CacheEntry> = LruCache(CACHE_MAX_ENTRIES)

    @Volatile
    private var lastSeenDatasetVersion: String? = null

    /**
     * Resolves a single MCC+MNC pair. Returns [PlmnResult.Loaded] (possibly with an empty list
     * when the backend reports `is_unknown`), [PlmnResult.Offline] on network failure, or
     * [PlmnResult.TransientFailure] for everything else. Never throws.
     */
    suspend fun resolve(mcc: String, mnc: String): PlmnResult {
        val key = "resolve:$mcc-$mnc"
        cachedHit(key)?.let { return it }

        return try {
            val response = api.getPlmn(mcc, mnc)
            if (!response.isSuccessful) {
                return PlmnResult.TransientFailure(response.code())
            }
            val body = response.body() ?: return PlmnResult.TransientFailure(response.code())
            maybeInvalidateOnDatasetFlip(body.datasetVersion)
            val records = if (body.result.isUnknown) emptyList() else body.result.records
            val loaded = PlmnResult.Loaded(records, body.datasetVersion, truncated = false)
            cache.put(key, CacheEntry(loaded, System.currentTimeMillis() + CACHE_TTL_MS))
            loaded
        } catch (e: IOException) {
            Timber.d(e, "PLMN resolve offline (mcc=%s, mnc=%s)", mcc, mnc)
            PlmnResult.Offline
        } catch (t: Throwable) {
            Timber.w(t, "PLMN resolve failed (mcc=%s, mnc=%s)", mcc, mnc)
            PlmnResult.TransientFailure()
        }
    }

    /**
     * Free-text search using the backend's `q` parameter.
     *
     * @param query Trimmed user input. The caller is expected to enforce a minimum length.
     */
    suspend fun search(query: String): PlmnResult {
        val key = "search:q:$query"
        cachedHit(key)?.let { return it }

        return try {
            val response = api.searchPlmn(q = query)
            mapSearchResponse(response, key)
        } catch (e: IOException) {
            Timber.d(e, "PLMN search offline (q=%s)", query)
            PlmnResult.Offline
        } catch (t: Throwable) {
            Timber.w(t, "PLMN search failed (q=%s)", query)
            PlmnResult.TransientFailure()
        }
    }

    /**
     * Single-filter search used by Resolve mode when only one of MCC/MNC is populated.
     * Routes through the same `/v2/plmn/search` endpoint with the typed filter set.
     */
    suspend fun searchByMcc(mcc: String): PlmnResult {
        val key = "search:mcc:$mcc"
        cachedHit(key)?.let { return it }
        return try {
            mapSearchResponse(api.searchPlmn(mcc = mcc), key)
        } catch (e: IOException) {
            PlmnResult.Offline
        } catch (t: Throwable) {
            Timber.w(t, "PLMN searchByMcc failed (mcc=%s)", mcc)
            PlmnResult.TransientFailure()
        }
    }

    /** Companion to [searchByMcc] for the MNC-only case. */
    suspend fun searchByMnc(mnc: String): PlmnResult {
        val key = "search:mnc:$mnc"
        cachedHit(key)?.let { return it }
        return try {
            mapSearchResponse(api.searchPlmn(mnc = mnc), key)
        } catch (e: IOException) {
            PlmnResult.Offline
        } catch (t: Throwable) {
            Timber.w(t, "PLMN searchByMnc failed (mnc=%s)", mnc)
            PlmnResult.TransientFailure()
        }
    }

    private fun mapSearchResponse(
        response: retrofit2.Response<com.craxiom.networksurvey.data.api.PlmnSearchResponse>,
        cacheKey: String
    ): PlmnResult {
        if (!response.isSuccessful) {
            return PlmnResult.TransientFailure(response.code())
        }
        val body = response.body() ?: return PlmnResult.TransientFailure(response.code())
        maybeInvalidateOnDatasetFlip(body.datasetVersion)
        val loaded = PlmnResult.Loaded(
            records = body.results,
            datasetVersion = body.datasetVersion,
            truncated = body.truncated
        )
        cache.put(cacheKey, CacheEntry(loaded, System.currentTimeMillis() + CACHE_TTL_MS))
        return loaded
    }

    private fun cachedHit(key: String): PlmnResult.Loaded? {
        val entry = cache.get(key) ?: return null
        if (entry.isExpired()) {
            cache.remove(key)
            return null
        }
        return entry.result
    }

    private fun maybeInvalidateOnDatasetFlip(incomingVersion: String?) {
        if (incomingVersion.isNullOrEmpty()) return
        val previous = lastSeenDatasetVersion
        if (previous != null && previous != incomingVersion) {
            cache.evictAll()
        }
        lastSeenDatasetVersion = incomingVersion
    }

    /**
     * Synchronizes the cached LRU state with the per-record list returned from a fresh request.
     * Carries the [PlmnResult.Loaded] payload plus an absolute expiration timestamp in ms.
     */
    private data class CacheEntry(
        val result: PlmnResult.Loaded,
        val expiresAtMs: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAtMs
    }

    companion object {
        private const val CACHE_MAX_ENTRIES = 128
        private const val CACHE_TTL_MS = 120_000L

        @Volatile
        private var INSTANCE: PlmnRepository? = null

        @JvmStatic
        fun getInstance(): PlmnRepository {
            INSTANCE?.let { return it }
            return synchronized(this) {
                INSTANCE ?: PlmnRepository(retrofit.create(Api::class.java)).also { INSTANCE = it }
            }
        }
    }
}

/** Sample helper for tests / display: convenience extension to derive a PLMN row's display name. */
fun PlmnRecord.displayName(): String =
    brand?.takeIf { it.isNotBlank() }
        ?: operator?.takeIf { it.isNotBlank() }
        ?: "Unknown"
