package com.craxiom.networksurvey.data.oui

import com.craxiom.networksurvey.data.oui.OuiCacheRoomImpl.Companion.EVICT_BATCH_SIZE
import com.craxiom.networksurvey.data.oui.OuiCacheRoomImpl.Companion.MAX_ENTRIES


/**
 * Room-backed implementation of [OuiCache].
 *
 * On-disk cap is [MAX_ENTRIES] rows. When exceeded, the oldest [EVICT_BATCH_SIZE] rows by
 * `last_seen_ts` are dropped on the next write. Cheap amortized cleanup without a background
 * job in MVP.
 */
class OuiCacheRoomImpl(
    private val dao: OuiCacheDao
) : OuiCache {

    override suspend fun lookup(full48: Long): OuiResult? {
        // MVP: only MA-L (24-bit) entries are ever written because the coordinator only
        // transmits 24-bit prefixes. Keep the cache schema prefix-length-aware so a future
        // MA-M / MA-S implementation (requires the client to send longer prefixes) can slot
        // in without a data migration, but don't waste DAO round-trips probing for entries
        // that can't exist today.
        val prefix = MacPrefix.maskToPrefix(full48, MacPrefix.MA_L)
        val row = dao.findByPrefix(prefix, MacPrefix.MA_L) ?: return null
        dao.touch(prefix, MacPrefix.MA_L, System.currentTimeMillis())
        return rowToResult(row)
    }

    override suspend fun put(
        prefixLong: Long,
        prefixLen: Int,
        result: OuiResult,
        datasetVersion: String
    ) {
        if (!isCacheable(result.status)) return
        val entry = OuiCacheEntity(
            prefixLong = prefixLong,
            prefixLen = prefixLen,
            status = result.status.name,
            vendor = result.vendor,
            datasetVersion = datasetVersion,
            lastSeenTs = System.currentTimeMillis()
        )
        dao.upsert(entry)
        enforceCap()
    }

    override suspend fun evictAll() = dao.evictAll()

    override suspend fun evictStale(currentVersion: String): Int = dao.evictStale(currentVersion)

    override suspend fun count(): Int = dao.count()

    override suspend fun evictOldest(limit: Int) = dao.evictOldest(limit)

    private suspend fun enforceCap() {
        val count = dao.count()
        if (count > MAX_ENTRIES) {
            dao.evictOldest(EVICT_BATCH_SIZE)
        }
    }

    private fun rowToResult(row: OuiCacheEntity): OuiResult? {
        val status = runCatching { OuiStatus.valueOf(row.status) }.getOrNull() ?: return null
        return OuiResult(status = status, vendor = row.vendor)
    }

    private fun isCacheable(status: OuiStatus): Boolean = when (status) {
        OuiStatus.RESOLVED,
        OuiStatus.UNKNOWN,
        OuiStatus.PRIVATE,
        OuiStatus.SHARED_VENDOR_BLOCK -> true

        OuiStatus.LAA,
        OuiStatus.LOOKUP_DISABLED,
        OuiStatus.OFFLINE,
        OuiStatus.LOADING,
        OuiStatus.TRANSIENT_FAILURE -> false
    }

    companion object {
        private const val MAX_ENTRIES = 10_000
        private const val EVICT_BATCH_SIZE = 500
    }
}
