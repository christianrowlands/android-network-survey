package com.craxiom.networksurvey.data.oui

/**
 * Persistent L2 cache for OUI lookup results. Keyed on MAC prefix + prefix length.
 *
 * Implementations must probe 36-bit → 28-bit → 24-bit on reads (mirrors the server's
 * longest-prefix-first match) so an MA-M/MA-S registrant wins over its parent MA-L block.
 *
 * Only terminal, non-transient statuses are persisted. Transient results
 * (`OFFLINE`, `TRANSIENT_FAILURE`, `LOADING`, `LOOKUP_DISABLED`, `LAA`) are session-only and
 * held by the repository's in-memory layer, never here.
 */
interface OuiCache {

    /**
     * Probes the cache for the longest matching prefix. Returns null on miss. Touches the
     * `last_seen_ts` of any hit so LRU eviction keeps hot rows.
     */
    suspend fun lookup(full48: Long): OuiResult?

    /**
     * Persists a result at the specified prefix length (24/28/36). Callers should map server
     * `matched_prefix_len` directly; negatives (`UNKNOWN`, `SHARED_VENDOR_BLOCK`) are cached at 24.
     */
    suspend fun put(prefixLong: Long, prefixLen: Int, result: OuiResult, datasetVersion: String)

    /** Drops every row. Called on dataset-version mismatch. */
    suspend fun evictAll()

    /**
     * Removes rows whose `dataset_version` differs from the provided current version. Returns
     * the number of rows deleted.
     */
    suspend fun evictStale(currentVersion: String): Int

    /** Best-effort row count; used to enforce the on-disk cap. */
    suspend fun count(): Int

    /** Removes the oldest [limit] rows by `last_seen_ts`. */
    suspend fun evictOldest(limit: Int)
}
